package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.ingest.client.TourApiClient;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailCommonItem;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 수집 1단계 — 각 언어 서비스를 <b>독립적으로</b> 호출해 {@code staging_place}에 원본 그대로
 * 적재한다(ADR-016, 외부 API 연동 설계서 §2.6.2). 이 단계에서는 매칭·판단을 하지 않는다.
 *
 * <p>구 구현은 국문 목록을 순회하며 각 건에 영문을 매칭하는 단방향이었다. 그 구조에서는
 * EngService2에만 있고 KorService2에 없는 장소가 순회 대상에 아예 들어오지 않아
 * <b>구조적으로 누락</b>됐다(ISS-1). 여기서는 언어마다 자기 목록을 받으므로 누락이 없다.
 *
 * <p>언어는 레지스트리에서 온다 — 언어별 메서드나 분기를 두지 않는다(ADR-018).
 */
@Service
public class Collector {

    private static final Logger log = LoggerFactory.getLogger(Collector.class);
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final StagingWriter stagingWriter;
    private final ContentTypeMapping contentTypes;
    private final LanguageRegistry languages;
    private final RegionRepository regionRepository;
    private final SyncLogger syncLogger;

    public Collector(
            TourApiClient tourApiClient,
            StagingWriter stagingWriter,
            ContentTypeMapping contentTypes,
            LanguageRegistry languages,
            RegionRepository regionRepository,
            SyncLogger syncLogger) {
        this.tourApiClient = tourApiClient;
        this.stagingWriter = stagingWriter;
        this.contentTypes = contentTypes;
        this.languages = languages;
        this.regionRepository = regionRepository;
        this.syncLogger = syncLogger;
    }

    public record Result(String lang, int upserted, int unchanged, int skipped, int failed) {
        int total() {
            return upserted + unchanged + skipped + failed;
        }
    }

    /**
     * 활성 언어 × 활성 지역 전체 수집.
     *
     * <p><b>한 언어가 실패해도 나머지를 계속한다</b>(아키텍처 §5.1 부분 실패 처리). 전체를
     * 멈추면 정상 수집된 언어까지 방치되는데, 3단 분리 덕에 나중에 재매칭하면 손실이 없다.
     */
    public List<Result> collectAll() {
        return languages.activeCodes().stream().map(this::collectLanguage).toList();
    }

    public Result collectLanguage(String lang) {
        List<Region> regions = regionRepository.findByActiveTrue();
        Counters counters = new Counters();
        String failMessage = null;

        try {
            for (Region region : regions) {
                collectRegion(lang, region, counters, Integer.MAX_VALUE);
            }
        } catch (RuntimeException e) {
            failMessage = e.getMessage();
            log.error("[{}] 수집 중 오류", lang, e);
        }

        Result result = counters.toResult(lang);
        syncLogger.record(SyncPhase.COLLECT, lang, "전체 지역", result.total(), failMessage);
        return result;
    }

    /** 단일 지역 수집. 로직 검증·소규모 확인용으로 maxItems를 제한할 수 있다. */
    public Result collectRegion(String lang, Long regionId, int maxItems) {
        Region region = regionRepository
                .findById(regionId)
                .orElseThrow(() -> new IllegalArgumentException("region을 찾을 수 없다: " + regionId));
        Counters counters = new Counters();
        String failMessage = null;
        try {
            collectRegion(lang, region, counters, maxItems);
        } catch (RuntimeException e) {
            failMessage = e.getMessage();
            log.error("[{}] region {} 수집 중 오류", lang, regionId, e);
        }
        Result result = counters.toResult(lang);
        syncLogger.record(SyncPhase.COLLECT, lang, "region=" + regionId, result.total(), failMessage);
        return result;
    }

    private void collectRegion(String lang, Region region, Counters counters, int maxItems) {
        String serviceName = languages.serviceNameOf(lang);
        if (serviceName == null) {
            throw new IllegalStateException("언어 레지스트리에 서비스명이 없다: " + lang);
        }

        int pageNo = 1;
        int seen = 0;
        List<AreaBasedSyncItem> page;
        do {
            int rows = Math.min(PAGE_SIZE, maxItems - seen);
            page = tourApiClient.fetchAreaBasedSyncList(
                    serviceName, region.getLdongRegnCd(), region.getLdongSignguCd(), pageNo, rows);
            for (AreaBasedSyncItem item : page) {
                // 항목 실패는 여기서 격리한다 — 워터마크가 저장되지 않으므로 다음 수집에서
                // 자동 재시도된다.
                try {
                    collectItem(lang, serviceName, region, item, counters);
                } catch (RuntimeException e) {
                    counters.failed++;
                    log.warn("[{}] 항목 수집 실패 contentId={}", lang, item.contentid(), e);
                }
            }
            seen += page.size();
            pageNo++;
        } while (page.size() == PAGE_SIZE && seen < maxItems);
    }

    private void collectItem(
            String lang, String serviceName, Region region, AreaBasedSyncItem item, Counters counters) {
        if (item.contentid() == null || item.contentid().isBlank()) {
            counters.skipped++;
            return;
        }
        // 매핑에 없는 타입은 수집 대상이 아니다(여행코스·교통 등). 원본 적재 단계지만,
        // 어차피 place로 만들지 않을 것을 staging에 쌓아 재매칭 비용만 늘릴 이유가 없다.
        if (contentTypes.placeTypeOf(lang, item.contenttypeid()).isEmpty()) {
            counters.skipped++;
            return;
        }

        StagingWriter.UpsertResult upserted =
                stagingWriter.upsert(lang, region.getLdongRegnCd(), region.getLdongSignguCd(), item);
        if (!upserted.changed()) {
            counters.unchanged++;
            return;
        }
        counters.upserted++;

        // 목록 응답에는 overview가 없어 상세를 한 번 더 부른다. 바뀐 항목만 부르는 것이
        // 오퍼레이션별 일 1,000건 한도를 지키는 핵심이다.
        Optional<DetailCommonItem> detail = tourApiClient.fetchDetailCommon(serviceName, item.contentid());
        detail.map(DetailCommonItem::overview)
                .ifPresent(overview -> stagingWriter.applyOverview(upserted.stagingId(), overview));
    }

    private static final class Counters {
        int upserted;
        int unchanged;
        int skipped;
        int failed;

        Result toResult(String lang) {
            return new Result(lang, upserted, unchanged, skipped, failed);
        }
    }
}
