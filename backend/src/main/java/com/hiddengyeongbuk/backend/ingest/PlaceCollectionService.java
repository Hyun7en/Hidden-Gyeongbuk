package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.place.PlaceTagRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLogRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import com.hiddengyeongbuk.backend.ingest.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.ingest.dto.DetailCommonItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 국문(KorService2) 목록을 기준으로 국문/영문 상세를 병행 수집해 place에 upsert한다.
 * 외부 API 연동 설계서 2.5~2.6 흐름 참고.
 *
 * showflag!=1 이거나 지원하지 않는 contentTypeId는 건너뛴다. 이미 저장된 place는
 * TourAPI가 내려주는 modifiedtime을 place.sourceModifiedAt과 비교해, 변경이 없으면
 * 상세 조회(국문+영문 2회)를 생략한다 — TourAPI 개발계정 일 1,000건 호출 한도를 아끼기 위함.
 */
@Service
public class PlaceCollectionService {

    private static final Logger log = LoggerFactory.getLogger(PlaceCollectionService.class);
    private static final DateTimeFormatter MODIFIED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final ContentTypeMapper contentTypeMapper;
    private final PlaceTagAssigner placeTagAssigner;
    private final PlaceRepository placeRepository;
    private final PlaceTagRepository placeTagRepository;
    private final DataSyncLogRepository dataSyncLogRepository;

    public PlaceCollectionService(
            TourApiClient tourApiClient,
            ContentTypeMapper contentTypeMapper,
            PlaceTagAssigner placeTagAssigner,
            PlaceRepository placeRepository,
            PlaceTagRepository placeTagRepository,
            DataSyncLogRepository dataSyncLogRepository) {
        this.tourApiClient = tourApiClient;
        this.contentTypeMapper = contentTypeMapper;
        this.placeTagAssigner = placeTagAssigner;
        this.placeRepository = placeRepository;
        this.placeTagRepository = placeTagRepository;
        this.dataSyncLogRepository = dataSyncLogRepository;
    }

    /**
     * 로직 검증·소규모 테스트용. maxItems로 처리 건수를 제한해 1페이지만 조회한다.
     */
    @Transactional
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
    @Transactional
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
        } catch (RuntimeException e) {
            failMessage = e.getMessage();
            log.error("지역 {} 증분 수집 중 오류", region.getNameKo(), e);
        }

        return finishAndLog(region, "전체 수집(페이지네이션)", startedAt, counters, failMessage);
    }

    private void collectItem(AreaBasedSyncItem item, Region region, Counters counters) {
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
            counters.unchanged++;
            return;
        }

        Optional<DetailCommonItem> korDetail = tourApiClient.fetchDetailCommonKor(item.contentid());
        if (korDetail.isEmpty()) {
            counters.skipped++;
            return;
        }
        Optional<DetailCommonItem> engDetail = tourApiClient.fetchDetailCommonEng(item.contentid());

        Place place = existing.orElseGet(() -> Place.builder().build());
        applyDetail(place, item, korDetail.get(), engDetail, category.get(), region, sourceModifiedAt);
        placeRepository.save(place);
        assignTags(place);
        counters.saved++;
    }

    private void assignTags(Place place) {
        Set<String> tags = placeTagAssigner.assign(place.getCategory(), place.getTitleKo(), place.getOverviewKo());
        placeTagRepository.deleteByPlaceId(place.getId());
        for (String tag : tags) {
            placeTagRepository.save(PlaceTag.builder().place(place).tag(tag).build());
        }
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
        SyncStatus status = failMessage == null
                ? SyncStatus.SUCCESS
                : (counters.saved > 0 ? SyncStatus.PARTIAL : SyncStatus.FAIL);
        String message = failMessage != null
                ? failMessage
                : "%s, skipped=%d, unchanged=%d".formatted(scopeLabel, counters.skipped, counters.unchanged);

        dataSyncLogRepository.save(DataSyncLog.builder()
                .target("KorService2/EngService2:areaBasedSyncList2:" + region.getNameKo())
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .status(status)
                .recordCount(counters.saved)
                .message(message)
                .build());

        return new CollectionResult(counters.saved, counters.skipped, counters.unchanged, status);
    }

    private void applyDetail(
            Place place,
            AreaBasedSyncItem item,
            DetailCommonItem korDetail,
            Optional<DetailCommonItem> engDetail,
            PlaceCategory category,
            Region region,
            LocalDateTime sourceModifiedAt) {
        place.setContentId(item.contentid());
        place.setContentTypeId(Integer.valueOf(item.contenttypeid()));
        place.setCategory(category);
        place.setRegion(region);
        place.setTitleKo(korDetail.title());
        place.setTitleEn(engDetail.map(DetailCommonItem::title).orElse(null));
        place.setAddr(korDetail.addr1());
        place.setLat(parseCoordinate(korDetail.mapy()));
        place.setLng(parseCoordinate(korDetail.mapx()));
        place.setTel(korDetail.tel());
        place.setOverviewKo(korDetail.overview());
        place.setOverviewEn(engDetail.map(DetailCommonItem::overview).orElse(null));
        place.setFirstImage(korDetail.firstimage());
        place.setHasEn(engDetail.isPresent());
        place.setSourceModifiedAt(sourceModifiedAt);
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

    private static LocalDateTime parseModifiedTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, MODIFIED_TIME_FORMAT);
    }

    private static final class Counters {
        int saved;
        int skipped;
        int unchanged;
    }

    public record CollectionResult(int saved, int skipped, int unchanged, SyncStatus status) {}
}
