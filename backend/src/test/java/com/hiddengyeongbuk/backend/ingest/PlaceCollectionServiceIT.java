package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 MySQL(docker compose up -d mysql)과 실제 TourAPI(.env의 TOURAPI_SERVICE_KEY)가
 * 필요한 통합 테스트. 기본 ./gradlew test에는 포함되지 않는다.
 *
 * 실행 방법:
 *   cd backend
 *   set -a; source ../.env; set +a
 *   ./gradlew integrationTest --tests "*PlaceCollectionServiceIT"
 *
 * maxItems를 작게 유지해 TourAPI 개발계정 일 1,000건 한도를 넘지 않도록 할 것
 * (item마다 상세 조회가 국문·영문 최대 2건씩 추가로 발생한다).
 */
@SpringBootTest
class PlaceCollectionServiceIT {

    private static final int MAX_ITEMS = 5;

    @Autowired
    private PlaceCollectionService placeCollectionService;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void collectsAndongPlacesFromRealTourApi() {
        Region andong = regionRepository
                .findByLdongRegnCdAndLdongSignguCd("47", "170")
                .orElseThrow(() -> new IllegalStateException("안동 region 시드 데이터가 없습니다. V2 마이그레이션 확인 필요."));

        PlaceCollectionService.CollectionResult result = placeCollectionService.collectRegion(andong, MAX_ITEMS);

        System.out.println("수집 결과: " + result);
        assertThat(result.saved()).isGreaterThan(0);
    }
}
