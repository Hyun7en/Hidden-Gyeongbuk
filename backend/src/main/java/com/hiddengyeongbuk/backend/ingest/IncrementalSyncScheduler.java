package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 15개 지역을 순회하며 증분 수집을 실행하는 일 1회 배치.
 * TourAPI 개발계정은 일 1,000건 호출 한도가 있어 초기 전체 적재가 끝나기 전까지는
 * sync.enabled=false(기본값)로 꺼둔다. 운영계정 전환(P4) 후 true로 켤 것.
 */
@Component
@ConditionalOnProperty(prefix = "sync", name = "enabled", havingValue = "true")
public class IncrementalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncrementalSyncScheduler.class);

    private final RegionRepository regionRepository;
    private final PlaceCollectionService placeCollectionService;

    public IncrementalSyncScheduler(RegionRepository regionRepository, PlaceCollectionService placeCollectionService) {
        this.regionRepository = regionRepository;
        this.placeCollectionService = placeCollectionService;
    }

    @Scheduled(cron = "${sync.cron:0 0 3 * * *}")
    public void runDailySync() {
        List<Region> regions = regionRepository.findAll();
        log.info("증분 수집 배치 시작: 지역 {}곳", regions.size());
        for (Region region : regions) {
            try {
                PlaceCollectionService.CollectionResult result = placeCollectionService.collectRegionIncremental(region);
                log.info("지역 {} 증분 수집 완료: {}", region.getNameKo(), result);
            } catch (RuntimeException e) {
                log.error("지역 {} 증분 수집 실패", region.getNameKo(), e);
            }
        }
        log.info("증분 수집 배치 종료");
    }
}
