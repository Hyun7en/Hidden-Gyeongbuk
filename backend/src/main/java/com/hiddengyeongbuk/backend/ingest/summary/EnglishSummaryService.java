package com.hiddengyeongbuk.backend.ingest.summary;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummary;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummaryRepository;
import com.hiddengyeongbuk.backend.domain.place.SummarySource;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * has_en=false인 장소(EngService2에 영문이 없는 장소)의 국문 overview를 Gemini Flash로 쉬운
 * 영어 요약으로 바꿔 place_summary에 저장한다(외부 API 연동 설계서 4장). 장소별 1회만 호출하고,
 * 이미 저장된 요약이 있으면 다시 부르지 않는다 — Gemini 무료 티어 호출량을 아끼기 위함.
 */
@Service
public class EnglishSummaryService {

    private static final Logger log = LoggerFactory.getLogger(EnglishSummaryService.class);
    private static final int MAX_OVERVIEW_CHARS = 1500;

    private final GeminiClient geminiClient;
    private final PlaceRepository placeRepository;
    private final PlaceSummaryRepository placeSummaryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EnglishSummaryService(
            GeminiClient geminiClient,
            PlaceRepository placeRepository,
            PlaceSummaryRepository placeSummaryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.geminiClient = geminiClient;
        this.placeRepository = placeRepository;
        this.placeSummaryRepository = placeSummaryRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 수집 파이프라인(PlaceCollectionService)에서 place 하나를 저장할 때마다 호출한다. */
    @Transactional
    public SummaryResult ensureSummary(Place place) {
        if (place.isHasEn()) {
            return SummaryResult.SKIPPED_HAS_EN;
        }
        if (placeSummaryRepository.existsById(place.getId())) {
            return SummaryResult.ALREADY_EXISTS;
        }
        String overview = place.getOverviewKo();
        if (overview == null || overview.isBlank()) {
            return SummaryResult.SKIPPED_NO_OVERVIEW;
        }

        String summaryEn;
        try {
            summaryEn = geminiClient.generateSummary(buildPrompt(truncate(overview)));
        } catch (RuntimeException e) {
            log.warn("장소 {} 영문 요약 생성 실패: {}", place.getId(), e.getMessage());
            return SummaryResult.FAILED;
        }

        // placeId는 세팅하지 않는다 — @MapsId가 place로부터 자동으로 채운다. 직접 세팅하면
        // Spring Data가 새 행인데도 "이미 존재하는 detached 엔티티"로 착각해 merge()가
        // update를 시도하다 ObjectOptimisticLockingFailureException으로 실패한다.
        placeSummaryRepository.save(PlaceSummary.builder()
                .place(place)
                .summaryEn(summaryEn)
                .source(SummarySource.LLM)
                .generatedAt(LocalDateTime.now())
                .build());
        return SummaryResult.GENERATED;
    }

    /**
     * 이미 수집된(재수집 없이) has_en=false 장소를 일괄 백필한다. TourAPI 호출 없이 Gemini만
     * 쓰므로 SyncAdminController의 지역 재수집과 별개로, Gemini 무료 티어 분당/일일 한도를
     * 감안해 limit만큼만 실제로 시도하고 멈춘다.
     *
     * limit은 "성공 개수"가 아니라 "Gemini 호출 시도 횟수"에 건다 — 설정 오류(잘못된 키·모델명
     * 등)로 전부 실패하는 상황에서 limit이 안 걸려 남은 후보 전체를 연달아 두드리는 걸 막기 위함.
     */
    @Transactional
    public BackfillResult backfill(int limit) {
        List<Place> candidates = placeRepository.findByHasEnFalse();
        int generated = 0;
        int alreadyExists = 0;
        int skippedNoOverview = 0;
        int failed = 0;
        int attempts = 0;

        for (Place place : candidates) {
            if (attempts >= limit) {
                break;
            }
            switch (ensureSummary(place)) {
                case GENERATED -> {
                    generated++;
                    attempts++;
                }
                case FAILED -> {
                    failed++;
                    attempts++;
                }
                case ALREADY_EXISTS -> alreadyExists++;
                case SKIPPED_NO_OVERVIEW -> skippedNoOverview++;
                case SKIPPED_HAS_EN -> {
                    // findByHasEnFalse로 이미 걸러졌으니 도달하지 않는다.
                }
            }
        }
        if (generated > 0) {
            eventPublisher.publishEvent(new PlaceDataChangedEvent(this));
        }
        return new BackfillResult(generated, alreadyExists, skippedNoOverview, failed);
    }

    private static String buildPrompt(String overviewKo) {
        return """
                Summarize the following Korean tourist attraction description in 2-3 simple English \
                sentences for a foreign tourist audience. Only use facts present in the text — do not \
                add information that isn't there. Respond with the summary only, no preamble.

                Korean description:
                %s"""
                .formatted(overviewKo);
    }

    private static String truncate(String overview) {
        return overview.length() > MAX_OVERVIEW_CHARS ? overview.substring(0, MAX_OVERVIEW_CHARS) : overview;
    }

    public enum SummaryResult {
        GENERATED,
        ALREADY_EXISTS,
        SKIPPED_HAS_EN,
        SKIPPED_NO_OVERVIEW,
        FAILED
    }

    public record BackfillResult(int generated, int alreadyExists, int skippedNoOverview, int failed) {}
}
