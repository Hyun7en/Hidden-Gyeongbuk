package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.ingest.client.TourApiClient;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailCommonItem;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailImageItem;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailIntroItem;
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

    /**
     * @param detailFailed 목록은 받았으나 상세(overview)를 못 받은 건수. <b>upserted의 부분집합</b>이라
     *     {@code total()}에 더하지 않는다 — 더하면 목록에서 훑은 항목 수와 어긋난다. 별도로 세는
     *     이유는 이게 <b>호출 한도에 걸렸다는 신호</b>라서다. 전량 수집이 며칠에 걸쳐 도는 동안
     *     이 값이 오르기 시작하면 그날 예산이 끝났다는 뜻이고, 해당 건은 워터마크가 지워져
     *     다음 수집에서 자동 재시도된다.
     */
    public record Result(
            String lang,
            int upserted,
            int unchanged,
            int skipped,
            int failed,
            int detailFailed,
            int images,
            int imageFailed,
            int events,
            int eventFailed) {
        int total() {
            return upserted + unchanged + skipped + failed;
        }

        /**
         * {@code total()}의 내역. data_sync_log에 합계만 남기면 staging 행 수와 어긋났을 때
         * 원인을 알 수 없다 — skipped(수집 대상 아닌 타입)와 unchanged(변경 없음)는 staging
         * 행을 늘리지 않으므로 이 셋을 갈라 놔야 산수가 맞는다.
         */
        String breakdown() {
            return "upserted=%d, unchanged=%d, skipped=%d, failed=%d, detailFailed=%d, "
                            .formatted(upserted, unchanged, skipped, failed, detailFailed)
                    + "images=%d, imageFailed=%d, events=%d, eventFailed=%d"
                            .formatted(images, imageFailed, events, eventFailed);
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
        syncLogger.record(SyncPhase.COLLECT, lang, "전체 지역", result.total(), result.breakdown(), failMessage);
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
        syncLogger.record(
                SyncPhase.COLLECT, lang, "region=" + regionId, result.total(), result.breakdown(), failMessage);
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
        if (!contentTypes.isCollectable(lang, item.contenttypeid())) {
            counters.skipped++;
            return;
        }

        StagingWriter.UpsertResult upserted =
                stagingWriter.upsert(lang, region.getLdongRegnCd(), region.getLdongSignguCd(), item);

        if (upserted.changed()) {
            counters.upserted++;
            fetchOverview(lang, serviceName, item, upserted, counters);
        } else {
            counters.unchanged++;
        }

        // 사진·개최 기간은 changed와 무관하게 "아직 안 물어본 것"을 기준으로 부른다.
        // overview와 규칙이 다른 이유: overview는 첫 수집부터 받아 왔지만 이 둘은 나중에
        // 추가돼 기존 행 전부가 미조회 상태다. changed만 보면 소스가 modifiedtime을 바꿀
        // 때까지 영영 안 채워진다.
        fetchExtras(lang, serviceName, item, upserted, counters);
    }

    /**
     * 목록 응답에는 overview가 없어 상세를 한 번 더 부른다(2026-08-16 원본 응답으로 확인 —
     * 목록 26개 필드에 overview가 없다). 바뀐 항목만 부르는 것이 오퍼레이션별 일 1,000건
     * 한도를 지키는 핵심이다.
     */
    private void fetchOverview(
            String lang,
            String serviceName,
            AreaBasedSyncItem item,
            StagingWriter.UpsertResult upserted,
            Counters counters) {
        try {
            Optional<DetailCommonItem> detail = tourApiClient.fetchDetailCommon(serviceName, item.contentid());
            detail.map(DetailCommonItem::overview)
                    .ifPresent(overview -> stagingWriter.applyOverview(upserted.stagingId(), overview));
        } catch (RuntimeException e) {
            // 상세를 못 받았으면 워터마크를 지워 다음 수집에서 다시 시도하게 한다. upsert가
            // 이미 커밋된 뒤라 여기서 되돌리지 않으면 "안 바뀐 항목"이 되어 영영 재시도되지
            // 않는다. 바깥 catch에 맡기지 않는 이유는 stagingId가 여기에만 있어서다.
            //
            // 응답은 왔는데 overview가 비어 있는 경우는 여기 오지 않는다 — 그건 실패가 아니라
            // "원본에 설명이 없다"는 정상 결과이고, 워터마크를 지우면 매 수집마다 무한 재시도가
            // 된다(안동 기준 그런 장소가 절반이 넘는다).
            stagingWriter.clearWatermark(upserted.stagingId());
            counters.detailFailed++;
            log.warn(
                    "[{}] 상세 조회 실패 contentId={} — 워터마크를 지워 다음 수집에서 재시도한다",
                    lang,
                    item.contentid(),
                    e);
        }
    }

    /**
     * 사진(detailImage2)과 축제 개최 기간(detailIntro2). 둘 다 <b>목록 응답에 없어</b> 따로 부른다.
     *
     * <p><b>비용이 크다.</b> 목록은 100건당 1회지만 이 둘은 1건당 1회다. 그래서 두 가지로 아낀다.
     *
     * <ul>
     *   <li><b>이미 물어본 것은 다시 안 묻는다.</b> 받은 개수가 아니라 "물어봤는가"로 판단한다 —
     *       사진이 없는 장소를 매 수집마다 영원히 다시 묻지 않기 위해서다.
     *   <li><b>소스가 내린 장소(showflag=0)는 건너뛴다.</b> 어차피 Resolver가 EXCLUDED로 보내
     *       place를 만들지 않으므로, 그 사진을 받아 봐야 쓸 데가 없다. 국문 기준 258건이다.
     * </ul>
     *
     * <p>개최 기간은 <b>축제에만</b> 묻는다. detailIntro2는 타입마다 응답이 달라 축제가 아닌
     * 곳에 물으면 우리가 쓸 필드가 아예 없다. 축제 판정은 설정의 폴백 대분류가 {@code EV}인지로
     * 하며, 코드에 15/85를 박지 않는다(ADR-018).
     *
     * <p>실패는 카운터로만 남기고 넘어간다 — 표시를 안 남겼으므로 다음 수집이 자동 재시도한다.
     * 워터마크를 건드리지 않는 이유는 그게 overview 재조회까지 유발해 예산을 두 배로 쓰기 때문이다.
     */
    private void fetchExtras(
            String lang,
            String serviceName,
            AreaBasedSyncItem item,
            StagingWriter.UpsertResult upserted,
            Counters counters) {
        if (upserted.hiddenAtSource()) {
            return;
        }

        if (!upserted.imageAlreadyFetched()) {
            try {
                List<String> urls = tourApiClient.fetchDetailImages(serviceName, item.contentid()).stream()
                        .map(DetailImageItem::bestUrl)
                        .filter(url -> url != null && !url.isBlank())
                        .distinct()
                        .toList();
                stagingWriter.applyImages(upserted.stagingId(), urls);
                counters.images += urls.size();
            } catch (RuntimeException e) {
                counters.imageFailed++;
                log.warn("[{}] 이미지 조회 실패 contentId={}", lang, item.contentid(), e);
            }
        }

        boolean festival = contentTypes
                .fallbackPlaceType(lang, upserted.sourceTypeId())
                .filter(type -> type == PlaceType.EV)
                .isPresent();
        if (festival && !upserted.introAlreadyFetched()) {
            try {
                Optional<DetailIntroItem> intro = tourApiClient.fetchDetailIntro(
                        serviceName, item.contentid(), upserted.sourceTypeId());
                stagingWriter.applyEventPeriod(
                        upserted.stagingId(),
                        intro.map(DetailIntroItem::eventstartdate).orElse(null),
                        intro.map(DetailIntroItem::eventenddate).orElse(null));
                counters.events++;
            } catch (RuntimeException e) {
                counters.eventFailed++;
                log.warn("[{}] 축제 기간 조회 실패 contentId={}", lang, item.contentid(), e);
            }
        }
    }

    private static final class Counters {
        int upserted;
        int unchanged;
        int skipped;
        int failed;
        int detailFailed;
        int images;
        int imageFailed;
        int events;
        int eventFailed;

        Result toResult(String lang) {
            return new Result(
                    lang, upserted, unchanged, skipped, failed, detailFailed,
                    images, imageFailed, events, eventFailed);
        }
    }
}
