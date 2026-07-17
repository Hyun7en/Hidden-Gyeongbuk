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
 * has_en=false인 장소(EngService2에 영문이 없는 장소)의 국문 제목·overview를 Google Cloud
 * Translation API로 영어 번역해서 place_summary에 저장한다(외부 API 연동 설계서 4장 개정).
 * 원래는 Gemini(LLM)로 2~3문장 요약을 만들었지만, 정보 손실 없이 원문을 그대로 옮기는 게
 * 관광 정보 앱에는 더 유용하다고 판단해 "전문 번역"으로 바꿨고(2026-07-17), 이어서 순수 번역에는
 * 전용 번역 API가 프롬프트 설계·출력 파싱 없이 더 간단·저렴·안정적이라 엔진 자체도 Gemini에서
 * Google Translate로 옮겼다(2026-07-17) — Gemini 무료 티어는 번역이 아니라 향후 챗봇처럼
 * LLM이 실제로 필요한 기능을 위해 남겨둔다.
 * 장소별 1회만 호출하고, 이미 저장된 번역이 있으면 다시 부르지 않는다.
 */
@Service
public class EnglishSummaryService {

    private static final Logger log = LoggerFactory.getLogger(EnglishSummaryService.class);
    private static final int MAX_OVERVIEW_CHARS = 3000;

    private final TranslateClient translateClient;
    private final PlaceRepository placeRepository;
    private final PlaceSummaryRepository placeSummaryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EnglishSummaryService(
            TranslateClient translateClient,
            PlaceRepository placeRepository,
            PlaceSummaryRepository placeSummaryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.translateClient = translateClient;
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

        try {
            List<String> translated = translateClient.translate(List.of(place.getTitleKo(), truncate(overview)));
            // placeId는 세팅하지 않는다 — @MapsId가 place로부터 자동으로 채운다. 직접 세팅하면
            // Spring Data가 새 행인데도 "이미 존재하는 detached 엔티티"로 착각해 merge()가
            // update를 시도하다 ObjectOptimisticLockingFailureException으로 실패한다.
            placeSummaryRepository.save(PlaceSummary.builder()
                    .place(place)
                    .titleEn(translated.get(0))
                    .summaryEn(translated.get(1))
                    .source(SummarySource.LLM)
                    .generatedAt(LocalDateTime.now())
                    .build());
            return SummaryResult.GENERATED;
        } catch (RuntimeException e) {
            log.warn("장소 {} 영문 번역 생성 실패: {}", place.getId(), e.getMessage());
            return SummaryResult.FAILED;
        }
    }

    /**
     * 이미 수집된(재수집 없이) has_en=false 장소를 일괄 백필한다. TourAPI 호출 없이 번역 API만
     * 쓰므로 SyncAdminController의 지역 재수집과 별개로, limit만큼만 실제로 시도하고 멈춘다.
     *
     * limit은 "성공 개수"가 아니라 "호출 시도 횟수"에 건다 — 설정 오류(잘못된 키 등)로 전부
     * 실패하는 상황에서 limit이 안 걸려 남은 후보 전체를 연달아 두드리는 걸 막기 위함.
     *
     * 이 메서드 자체엔 @Transactional을 걸지 않는다 — 걸면 루프 전체가 트랜잭션 하나가 되어,
     * 배치 중간에 한 건이라도 DB 저장에서 예외가 나면 그 전까지 성공한 항목들까지 전부
     * 롤백된다(같은 클래스 안에서 ensureSummary()를 호출하는 self-invocation이라 그 메서드의
     * @Transactional은 여기선 안 먹는다 — Spring AOP 프록시를 안 거치기 때문). 대신 각 place의
     * save()가 자기 호출 안에서(SimpleJpaRepository 자체가 @Transactional) 독립적으로
     * 커밋되게 둬서 진짜 항목별 격리가 되도록 한다.
     */
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
                    pace();
                }
                case FAILED -> {
                    failed++;
                    attempts++;
                    pace();
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

    private static String truncate(String overview) {
        return overview.length() > MAX_OVERVIEW_CHARS ? overview.substring(0, MAX_OVERVIEW_CHARS) : overview;
    }

    // 번역 API 호출 직후에만 쉰다 — 딜레이 없이 연달아 부르면 "초당/분당 요청 수" 한도(하루
    // 글자수 한도와는 별개)에 걸려 403 userRateLimitExceeded가 난다(2026-07-17 실측, 0.3초
    // 간격으로도 부족해서 1초로 올림 — TranslateClient의 재시도 로직과 함께 방어).
    private static void pace() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
