package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLogRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import com.hiddengyeongbuk.backend.ingest.EngPlaceMatchingService.EngMatchResult;
import com.hiddengyeongbuk.backend.ingest.client.TourApiClient;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailCommonItem;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 국문(KorService2) 목록·상세를 기준으로 place를 upsert하고, 이어서 영문(EngService2) 매칭
 * 패스로 공식 영문을 채운다. 외부 API 연동 설계서 2.5~2.6 흐름 참고.
 *
 * 국문/영문은 서로 다른 contentId 공간을 쓰므로(실측 확인), 국문 contentId로 영문 상세를 바로
 * 부를 수 없다. 대신 국문 수집을 끝낸 뒤 {@link EngPlaceMatchingService}가 영문 목록을 독자
 * 조회해 이름·좌표로 국문 place와 매칭하고 공식 영문 제목·설명을 붙인다. 짝을 못 찾은 장소의
 * 영문은 별도 번역 백필(PlaceTranslationService, /internal/summaries)이 담당한다 —
 * 매칭이 has_en=true로 바꾼 장소는 자동 제외되므로 "영문 매칭 먼저, 남은 장소만 번역" 순서가 된다.
 *
 * showflag!=1 이거나 지원하지 않는 contentTypeId는 건너뛴다. 이미 저장된 place는 TourAPI
 * modifiedtime을 place.sourceModifiedAt과 비교해, 변경이 없으면 국문 상세 조회를 생략한다 —
 * TourAPI 개발계정 일 1,000건 호출 한도를 아끼기 위함.
 *
 * 수집 메서드에는 @Transactional을 걸지 않는다 — 루프가 항목마다 외부 API를 부르는 네트워크
 * I/O 덩어리라, 전체를 트랜잭션으로 감싸면 DB 커넥션을 수 분씩 붙든 채 HTTP를 기다리게 되고
 * 중간 한 건의 DB 오류에 지역 전체가 롤백된다. DB 쓰기는 항목 단위로 PlaceWriter(별도 빈)에
 * 위임해 각자 독립 커밋시킨다. 자세한 배경은 PlaceWriter 주석 참고.
 */
@Service
public class PlaceCollectionService {

    private static final Logger log = LoggerFactory.getLogger(PlaceCollectionService.class);
    private static final DateTimeFormatter MODIFIED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final ContentTypeMapper contentTypeMapper;
    private final PlaceRepository placeRepository;
    private final PlaceWriter placeWriter;
    private final DataSyncLogRepository dataSyncLogRepository;
    private final EngPlaceMatchingService engPlaceMatchingService;
    private final ApplicationEventPublisher eventPublisher;

    public PlaceCollectionService(
            TourApiClient tourApiClient,
            ContentTypeMapper contentTypeMapper,
            PlaceRepository placeRepository,
            PlaceWriter placeWriter,
            DataSyncLogRepository dataSyncLogRepository,
            EngPlaceMatchingService engPlaceMatchingService,
            ApplicationEventPublisher eventPublisher) {
        this.tourApiClient = tourApiClient;
        this.contentTypeMapper = contentTypeMapper;
        this.placeRepository = placeRepository;
        this.placeWriter = placeWriter;
        this.dataSyncLogRepository = dataSyncLogRepository;
        this.engPlaceMatchingService = engPlaceMatchingService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 로직 검증·소규모 테스트용. maxItems로 처리 건수를 제한해 1페이지만 조회한다.
     */
    public CollectionResult collectRegion(Region region, int maxItems) {
        LocalDateTime startedAt = LocalDateTime.now();
        Counters counters = new Counters();
        String failMessage = null;

        try {
            List<AreaBasedSyncItem> items = tourApiClient.fetchAreaBasedSyncList(
                    region.getLdongRegnCd(), region.getLdongSignguCd(), 1, maxItems);
            for (AreaBasedSyncItem item : items) {
                collectItem(item, region, counters);
            }
        } catch (RuntimeException e) {
            failMessage = e.getMessage();
            log.error("지역 {} 수집 중 오류", region.getNameKo(), e);
        }

        return finishAndLog(region, "제한 수집(maxItems=" + maxItems + ")", startedAt, counters, failMessage);
    }

    /**
     * 지역 전체를 페이지네이션으로 순회하는 증분 수집. 스케줄러에서 사용한다.
     */
    public CollectionResult collectRegionIncremental(Region region) {
        LocalDateTime startedAt = LocalDateTime.now();
        Counters counters = new Counters();
        String failMessage = null;

        try {
            int pageNo = 1;
            List<AreaBasedSyncItem> page;
            do {
                page = tourApiClient.fetchAreaBasedSyncList(
                        region.getLdongRegnCd(), region.getLdongSignguCd(), pageNo, PAGE_SIZE);
                for (AreaBasedSyncItem item : page) {
                    collectItem(item, region, counters);
                }
                pageNo++;
            } while (page.size() == PAGE_SIZE);

            // 국문 수집을 마친 뒤 영문 매칭 패스로 공식 영문을 채운다(이름·좌표 매칭).
            EngMatchResult eng = engPlaceMatchingService.matchRegion(region);
            counters.matched = eng.matched();
            counters.engUnchanged = eng.unchanged();
            counters.engFailed = eng.failed();
        } catch (RuntimeException e) {
            failMessage = e.getMessage();
            log.error("지역 {} 증분 수집 중 오류", region.getNameKo(), e);
        }

        return finishAndLog(region, "전체 수집(페이지네이션)", startedAt, counters, failMessage);
    }

    private void collectItem(AreaBasedSyncItem item, Region region, Counters counters) {
        try {
            collectItemInternal(item, region, counters);
        } catch (RuntimeException e) {
            // 항목 하나의 오류로 지역 전체 수집이 중단되지 않도록 여기서 잡아 이 항목만 건너뛴다.
            // sourceModifiedAt이 저장되지 않았으므로 다음 수집 때 unchanged로 잘못 스킵되지 않고
            // 자동으로 재시도된다.
            //
            // DB 쓰기를 PlaceWriter가 항목마다 독립 트랜잭션으로 커밋하기 때문에 이 catch가
            // 실제로 유효하다. 예전처럼 수집 루프 전체가 트랜잭션 하나였을 땐 DB 예외가
            // 트랜잭션을 rollback-only로 마킹해서, 여기서 삼켜도 최종 커밋에서
            // UnexpectedRollbackException이 터지며 지역 전체가 날아갔다(무력한 catch였음).
            counters.failed++;
            log.warn("항목 {} 처리 중 오류 — 건너뜀: {}", item.contentid(), e.getMessage());
        }
    }

    private void collectItemInternal(AreaBasedSyncItem item, Region region, Counters counters) {
        if (!"1".equals(item.showflag())) {
            counters.skipped++;
            return;
        }
        Optional<PlaceCategory> category = contentTypeMapper.categoryOfKor(item.contenttypeid());
        if (category.isEmpty()) {
            counters.skipped++;
            return;
        }

        LocalDateTime sourceModifiedAt = parseModifiedTime(item.modifiedtime());
        Optional<Place> existing = placeRepository.findByContentId(item.contentid());
        if (isUnchanged(existing, sourceModifiedAt)) {
            // 국문 상세 재조회(TourAPI 호출)만 건너뛴다. 태그와 분류체계 코드는 목록 데이터만으로
            // 처리하는 무료 연산이라, 규칙이 바뀌어도 다음 실행에서 자동 반영되게 매번 갱신한다.
            // (영문은 뒤이어 도는 매칭 패스가 지역 단위로 처리하므로 여기서 건드리지 않는다.)
            placeWriter.reassignTagsAndClassification(
                    existing.get(), item.lclsSystm1(), item.lclsSystm2(), item.lclsSystm3());
            counters.unchanged++;
            return;
        }

        Optional<DetailCommonItem> korDetail = tourApiClient.fetchDetailCommonKor(item.contentid());
        if (korDetail.isEmpty()) {
            counters.skipped++;
            return;
        }

        Place place = existing.orElseGet(() -> Place.builder().build());
        applyDetail(place, item, korDetail.get(), category.get(), region, sourceModifiedAt);
        // 이미 있던 place는 detached라 save()가 merge()로 처리하므로, 영속 인스턴스는 인자가
        // 아니라 반환값이다. 영문은 이후 매칭 패스가 채운다.
        placeWriter.savePlaceWithTags(place);
        counters.saved++;
    }

    private static boolean isUnchanged(Optional<Place> existing, LocalDateTime sourceModifiedAt) {
        if (existing.isEmpty() || sourceModifiedAt == null) {
            return false;
        }
        LocalDateTime existingModifiedAt = existing.get().getSourceModifiedAt();
        return existingModifiedAt != null && !sourceModifiedAt.isAfter(existingModifiedAt);
    }

    private CollectionResult finishAndLog(
            Region region, String scopeLabel, LocalDateTime startedAt, Counters counters, String failMessage) {
        SyncStatus status;
        if (failMessage != null) {
            status = counters.saved > 0 ? SyncStatus.PARTIAL : SyncStatus.FAIL;
        } else if (counters.failed > 0) {
            status = SyncStatus.PARTIAL;
        } else {
            status = SyncStatus.SUCCESS;
        }
        // failMessage에는 TourApiException처럼 응답 원문 스니펫이 포함될 수 있다 —
        // message 컬럼(VARCHAR 500)보다 길면 로그 저장 자체가 truncation 오류로 실패하므로 자른다.
        String message = failMessage != null
                ? truncate(failMessage, 500)
                : "%s, skipped=%d, unchanged=%d, failed=%d, matched=%d, engUnchanged=%d, engFailed=%d"
                        .formatted(
                                scopeLabel,
                                counters.skipped,
                                counters.unchanged,
                                counters.failed,
                                counters.matched,
                                counters.engUnchanged,
                                counters.engFailed);

        // target 컬럼은 VARCHAR(50)인데 접두사만 43자라 지역명이 조금만 길어도 저장이 깨진다 — message처럼 자른다.
        dataSyncLogRepository.save(DataSyncLog.builder()
                .target(truncate("areaBasedSyncList2:" + region.getNameKo(), 50))
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .status(status)
                .recordCount(counters.saved)
                .message(message)
                .build());

        // saved=0이어도 영문 매칭으로 공식 영문이 새로 붙었을 수 있다 — 그 경우도 catalog 캐시가
        // stale해지므로 이벤트를 발행한다.
        if (counters.saved > 0 || counters.matched > 0) {
            eventPublisher.publishEvent(new PlaceDataChangedEvent(this));
        }
        return new CollectionResult(
                counters.saved,
                counters.skipped,
                counters.unchanged,
                counters.failed,
                counters.matched,
                counters.engUnchanged,
                counters.engFailed,
                status);
    }

    // 국문 필드만 채운다. 영문(title_en/overview_en/has_en/content_id_en)은 이후 매칭 패스가
    // 담당하므로 여기서 건드리지 않는다 — 기존 place가 국문 변경으로 재수집돼도 이미 붙은 공식
    // 영문을 덮어쓰지 않게 하기 위함.
    private void applyDetail(
            Place place,
            AreaBasedSyncItem item,
            DetailCommonItem korDetail,
            PlaceCategory category,
            Region region,
            LocalDateTime sourceModifiedAt) {
        place.setContentId(item.contentid());
        place.setContentTypeId(Integer.valueOf(item.contenttypeid()));
        place.setCategory(category);
        place.setRegion(region);
        place.setTitleKo(korDetail.title());
        place.setAddr(korDetail.addr1());
        place.setLat(parseCoordinate(korDetail.mapy()));
        place.setLng(parseCoordinate(korDetail.mapx()));
        place.setTel(korDetail.tel());
        place.setOverviewKo(korDetail.overview());
        place.setFirstImage(korDetail.firstimage());
        place.setSourceModifiedAt(sourceModifiedAt);
        // 분류체계 코드(목록 응답 포함)를 함께 적재 — 현재는 저장만, 태그 개편은 후속 단계.
        place.setLclsSystm1(item.lclsSystm1());
        place.setLclsSystm2(item.lclsSystm2());
        place.setLclsSystm3(item.lclsSystm3());
    }

    static BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal parsed = new BigDecimal(value);
        // TourAPI는 좌표를 확보하지 못한 항목을 "0"으로 내려주기도 한다 — 결측치로 취급한다.
        if (parsed.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return parsed;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // 영문 매칭 패스(EngPlaceMatchingService)도 modifiedtime을 같은 포맷으로 파싱하므로 공유한다.
    static LocalDateTime parseModifiedTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, MODIFIED_TIME_FORMAT);
    }

    private static final class Counters {
        int saved;
        int skipped;
        int unchanged;
        int failed;
        int matched;
        int engUnchanged;
        int engFailed;
    }

    public record CollectionResult(
            int saved, int skipped, int unchanged, int failed, int matched, int engUnchanged, int engFailed, SyncStatus status) {}
}
