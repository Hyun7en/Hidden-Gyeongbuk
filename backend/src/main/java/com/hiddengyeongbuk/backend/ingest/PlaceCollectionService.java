package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLogRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import com.hiddengyeongbuk.backend.ingest.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.ingest.dto.DetailCommonItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 국문(KorService2) 목록을 기준으로 국문/영문 상세를 병행 수집해 place에 upsert한다.
 * 외부 API 연동 설계서 2.5~2.6 흐름 참고.
 *
 * 이 메서드는 "수집 로직" 검증이 목적이라 maxItems로 처리 건수를 제한한다.
 * 지역 전체(수백 건) 수집과 스케줄링·재시도는 후속 작업(증분 수집 배치)에서 다룬다.
 * TourAPI 개발계정 일 1,000건 한도를 넘지 않도록 항상 제한된 건수로 테스트할 것.
 */
@Service
public class PlaceCollectionService {

    private static final Logger log = LoggerFactory.getLogger(PlaceCollectionService.class);

    private final TourApiClient tourApiClient;
    private final ContentTypeMapper contentTypeMapper;
    private final PlaceRepository placeRepository;
    private final DataSyncLogRepository dataSyncLogRepository;

    public PlaceCollectionService(
            TourApiClient tourApiClient,
            ContentTypeMapper contentTypeMapper,
            PlaceRepository placeRepository,
            DataSyncLogRepository dataSyncLogRepository) {
        this.tourApiClient = tourApiClient;
        this.contentTypeMapper = contentTypeMapper;
        this.placeRepository = placeRepository;
        this.dataSyncLogRepository = dataSyncLogRepository;
    }

    @Transactional
    public CollectionResult collectRegion(Region region, int maxItems) {
        LocalDateTime startedAt = LocalDateTime.now();
        int saved = 0;
        int skipped = 0;
        String failMessage = null;

        try {
            List<AreaBasedSyncItem> items = tourApiClient.fetchAreaBasedSyncList(
                    region.getLdongRegnCd(), region.getLdongSignguCd(), 1, maxItems);

            for (AreaBasedSyncItem item : items) {
                if (!"1".equals(item.showflag())) {
                    skipped++;
                    continue;
                }
                Optional<PlaceCategory> category = contentTypeMapper.categoryOfKor(item.contenttypeid());
                if (category.isEmpty()) {
                    skipped++;
                    continue;
                }

                Optional<DetailCommonItem> korDetail = tourApiClient.fetchDetailCommonKor(item.contentid());
                if (korDetail.isEmpty()) {
                    skipped++;
                    continue;
                }
                Optional<DetailCommonItem> engDetail = tourApiClient.fetchDetailCommonEng(item.contentid());

                Place place = placeRepository
                        .findByContentId(item.contentid())
                        .orElseGet(() -> Place.builder().build());
                applyDetail(place, item, korDetail.get(), engDetail, category.get(), region);
                placeRepository.save(place);
                saved++;
            }
        } catch (RuntimeException e) {
            failMessage = e.getMessage();
            log.error("지역 {} 수집 중 오류", region.getNameKo(), e);
        }

        SyncStatus status = failMessage == null ? SyncStatus.SUCCESS : (saved > 0 ? SyncStatus.PARTIAL : SyncStatus.FAIL);
        dataSyncLogRepository.save(DataSyncLog.builder()
                .target("KorService2/EngService2:areaBasedSyncList2:" + region.getNameKo())
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .status(status)
                .recordCount(saved)
                .message(failMessage != null ? failMessage : "skipped=" + skipped)
                .build());

        return new CollectionResult(saved, skipped, status);
    }

    private void applyDetail(
            Place place,
            AreaBasedSyncItem item,
            DetailCommonItem korDetail,
            Optional<DetailCommonItem> engDetail,
            PlaceCategory category,
            Region region) {
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
    }

    private static BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    public record CollectionResult(int saved, int skipped, SyncStatus status) {}
}
