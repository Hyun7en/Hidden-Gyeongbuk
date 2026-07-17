package com.hiddengyeongbuk.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.catalog.course.dto.CourseListResponse;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseRecommendRequest;
import com.hiddengyeongbuk.backend.catalog.dto.ApiErrorResponse;
import com.hiddengyeongbuk.backend.catalog.dto.HubResponse;
import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PageResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceSummaryResponse;
import com.hiddengyeongbuk.backend.catalog.dto.RegionResponse;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.place.PlaceTagRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 실제 MySQL(docker compose up -d mysql)이 필요한 조회·추천 API 통합 테스트. 슬라이스
 * 테스트(HubControllerTest 등)는 레포지토리를 mock으로 대체하지만, 여기서는 실제 HTTP 요청 →
 * 컨트롤러 → 서비스 → JPA → DB 전체 경로가 맞물려 동작하는지 확인한다(캐싱 AOP·예외 핸들러
 * 전역 등록 등 슬라이스 테스트로는 못 잡는 배선 문제가 목표).
 *
 * hub/region 시드 데이터(V2 마이그레이션)는 그대로 참조하고, place는 이 테스트 전용으로
 * content_id에 "IT-TEST-" 접두사를 붙여 만들었다가 각 테스트 후 정리한다 — 실제 TourAPI로
 * 수집된 place와 절대 충돌하지 않고, 진행 중인 M1 수집량과 무관하게 항상 같은 결과를 보장한다.
 *
 * 실행 방법:
 *   cd backend
 *   ./gradlew integrationTest --tests "*CatalogApiIT"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceTagRepository placeTagRepository;

    private String testTitle;
    private Place testPlace;
    private PlaceTag testPlaceTag;

    @BeforeEach
    void seedTestPlace() {
        Region andong = regionRepository
                .findByLdongRegnCdAndLdongSignguCd("47", "170")
                .orElseThrow(() -> new IllegalStateException("안동 region 시드 데이터가 없습니다. V2 마이그레이션 확인 필요."));

        testTitle = "통합테스트 전용 장소 " + UUID.randomUUID();
        // content_id는 VARCHAR(20)이라 UUID 전체를 못 넣는다 — 앞 18자리 hex만 붙여 20자에 맞춘다.
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        testPlace = placeRepository.save(Place.builder()
                .contentId("IT" + shortId)
                .contentTypeId(12)
                .category(PlaceCategory.관광지)
                .region(andong)
                .titleKo(testTitle)
                .titleEn("Integration Test Place")
                .addr("테스트 주소")
                .lat(new BigDecimal("36.5684249"))
                .lng(new BigDecimal("128.7294000"))
                .overviewKo("통합테스트용 설명")
                .hasEn(true)
                .build());
        testPlaceTag = placeTagRepository.save(PlaceTag.builder().place(testPlace).tag("자연").build());
    }

    @AfterEach
    void cleanupTestPlace() {
        if (testPlace == null) {
            return; // seedTestPlace가 실패했으면 지울 것도 없다
        }
        // deleteByPlaceId(파생 삭제 쿼리)는 자체 트랜잭션을 안 열고 호출부 트랜잭션에 얹혀간다 —
        // 프로덕션에서는 @Transactional 서비스 메서드 안에서 불러서 문제없지만, 여기 @AfterEach에는
        // 그런 트랜잭션이 없어 "No EntityManager with actual transaction available"로 터진다.
        // save()가 돌려준 엔티티를 그대로 delete(entity)로 지우면(SimpleJpaRepository의 기본
        // CRUD 메서드라 자체 트랜잭션을 가짐) 앰비언트 트랜잭션 없이도 안전하다.
        placeTagRepository.delete(testPlaceTag);
        placeRepository.delete(testPlace);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void 허브_목록에_시드된_안동_허브가_있다() {
        ResponseEntity<HubResponse[]> response = restTemplate.getForEntity(url("/api/hubs"), HubResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(HubResponse::nameKo).contains("안동");
    }

    @Test
    void 지역_목록을_허브로_필터링한다() {
        ResponseEntity<RegionResponse[]> response =
                restTemplate.getForEntity(url("/api/regions?hubId=1"), RegionResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(RegionResponse::nameKo).contains("안동");
    }

    @Test
    void 메타_옵션에_6개_관심사가_있다() {
        ResponseEntity<MetaOptionsResponse> response =
                restTemplate.getForEntity(url("/api/meta/options"), MetaOptionsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().interests()).hasSize(6);
    }

    @Test
    void 키워드로_시드된_테스트_장소를_검색한다() {
        ResponseEntity<PageResponse<PlaceSummaryResponse>> response = restTemplate.exchange(
                url("/api/places?keyword=" + testTitle + "&lang=ko"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<PageResponse<PlaceSummaryResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).extracting(PlaceSummaryResponse::title).contains(testTitle);
    }

    @Test
    void 장소_상세를_조회하면_태그가_포함된다() {
        ResponseEntity<PlaceDetailResponse> response =
                restTemplate.getForEntity(url("/api/places/" + testPlace.getId() + "?lang=ko"), PlaceDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo(testTitle);
        assertThat(response.getBody().tags()).contains("자연");
    }

    @Test
    void 존재하지_않는_장소는_404와_표준_에러_포맷을_반환한다() {
        ResponseEntity<ApiErrorResponse> response =
                restTemplate.getForEntity(url("/api/places/999999999"), ApiErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void 코스_추천_요청이_정상_구조로_응답한다() {
        CourseRecommendRequest request =
                new CourseRecommendRequest(1L, Duration.ONE_DAY, List.of(Interest.NATURE), Difficulty.EASY);

        ResponseEntity<CourseListResponse> response =
                restTemplate.postForEntity(url("/api/courses/recommend"), request, CourseListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().courses()).isNotNull();
    }
}
