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
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * 실제 MySQL(docker compose up -d mysql)이 필요한 조회·추천 API 통합 테스트. 슬라이스 테스트가
 * 레포지토리를 mock으로 대체하는 것과 달리, 여기서는 HTTP → 컨트롤러 → 서비스 → JPA → DB
 * 전체 경로가 맞물려 도는지 본다(캐싱 AOP·전역 예외 핸들러 등록처럼 슬라이스로는 못 잡는 배선).
 *
 * <p>hub/region 시드(V2)는 그대로 참조하고, place는 이 테스트 전용으로 만들었다가 정리한다.
 * <b>구 스키마와 달리 place에 자연키가 없어</b> content_id 접두사로 격리하던 방식을 못 쓴다 —
 * 대신 제목에 UUID를 넣어 실제 수집 데이터와 겹치지 않게 하고, 저장한 엔티티를 들고 있다가 지운다.
 *
 * <p>실행: {@code ./gradlew integrationTest --tests "*CatalogApiIT"}
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
    private PlaceTranslationRepository placeTranslationRepository;

    private String testTitleKo;
    private Place testPlace;
    private PlaceTranslation testKoTranslation;

    @BeforeEach
    void seedTestPlace() {
        Region andong = regionRepository
                .findByLdongRegnCdAndLdongSignguCd("47", "170")
                .orElseThrow(() -> new IllegalStateException("안동 region 시드가 없다. V2 마이그레이션 확인 필요."));

        testTitleKo = "통합테스트 전용 장소 " + UUID.randomUUID();
        testPlace = placeRepository.save(Place.builder()
                .region(andong)
                .placeType(PlaceType.NA)
                .lat(new BigDecimal("36.5684249"))
                .lng(new BigDecimal("128.7294000"))
                .build());

        // 제목·주소·설명은 이제 place가 아니라 번역 행에 있다. 국문 행만 만들어 en 요청이
        // ko로 폴백하는 것까지 확인한다.
        testKoTranslation = placeTranslationRepository.save(PlaceTranslation.builder()
                .placeId(testPlace.getId())
                .lang("ko")
                .title(testTitleKo)
                .overview("통합테스트용 설명")
                .addr("경상북도 안동시 테스트로 1")
                .source(TranslationSource.OFFICIAL)
                .generatedAt(LocalDateTime.now())
                .build());

    }

    @AfterEach
    void cleanupTestPlace() {
        if (testPlace == null) {
            return;
        }
        // 파생 삭제 쿼리(deleteByPlaceId)는 자체 트랜잭션을 안 열고 호출부 트랜잭션에 얹혀간다 —
        // @AfterEach에는 앰비언트 트랜잭션이 없어 터진다. 저장 시 받은 엔티티를 기본 CRUD로 지운다.
        placeTranslationRepository.delete(testKoTranslation);
        placeRepository.delete(testPlace);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void 허브_목록은_요청_언어로_해석된_이름을_준다() {
        ResponseEntity<HubResponse[]> response =
                restTemplate.getForEntity(url("/api/hubs?lang=ko"), HubResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(HubResponse::name).contains("안동");
        assertThat(response.getBody()).extracting(HubResponse::code).contains("ANDONG");
    }

    @Test
    void 허브_목록은_영문_요청에_영문_이름을_준다() {
        ResponseEntity<HubResponse[]> response =
                restTemplate.getForEntity(url("/api/hubs?lang=en"), HubResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(HubResponse::name).contains("Andong");
        assertThat(response.getBody()).extracting(HubResponse::resolvedLang).contains("en");
    }

    @Test
    void 지역_목록은_거점_소속을_먼저_보여준다() {
        ResponseEntity<RegionResponse[]> response =
                restTemplate.getForEntity(url("/api/regions?hubId=1&lang=ko"), RegionResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(RegionResponse::name).contains("안동");
        // 정렬이 제외가 아니라 우선순위라, 다른 거점 지역도 뒤에 남아 있어야 한다(ADR-019).
        assertThat(response.getBody()).hasSizeGreaterThan(1);
    }

    @Test
    void 메타_옵션에_6개_관심사가_있고_durations는_없다() {
        ResponseEntity<MetaOptionsResponse> response =
                restTemplate.getForEntity(url("/api/meta/options?lang=en"), MetaOptionsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().interests()).hasSize(6);
        assertThat(response.getBody().resolvedLang()).isEqualTo("en");
    }

    @Test
    void 키워드로_시드된_테스트_장소를_검색한다() {
        ResponseEntity<PageResponse<PlaceSummaryResponse>> response = restTemplate.exchange(
                url("/api/places?keyword=" + testTitleKo + "&lang=ko"),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<PageResponse<PlaceSummaryResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).extracting(PlaceSummaryResponse::title).contains(testTitleKo);
        assertThat(response.getBody().resolvedLang()).isEqualTo("ko");
    }

    @Test
    void 장소_상세는_태그를_enum_코드로_준다() {
        ResponseEntity<PlaceDetailResponse> response = restTemplate.getForEntity(
                url("/api/places/" + testPlace.getId() + "?lang=ko"), PlaceDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo(testTitleKo);
        assertThat(response.getBody().placeType()).isEqualTo("NA");
        // 한글 라벨("자연")이 아니라 코드가 내려와야 한다(ERD §3.8).
    }

    @Test
    void 영문_행이_없으면_국문으로_폴백하고_resolvedLang으로_알린다() {
        ResponseEntity<PlaceDetailResponse> response = restTemplate.getForEntity(
                url("/api/places/" + testPlace.getId() + "?lang=en"), PlaceDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo(testTitleKo);
        assertThat(response.getBody().resolvedLang()).isEqualTo("ko");
        // addrLocal은 요청 언어와 무관하게 항상 국문이다.
        assertThat(response.getBody().addrLocal()).isEqualTo("경상북도 안동시 테스트로 1");
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
                new CourseRecommendRequest(1L, Duration.ONE_DAY, List.of(PlaceType.NA), Difficulty.EASY);

        ResponseEntity<CourseListResponse> response =
                restTemplate.postForEntity(url("/api/courses/recommend"), request, CourseListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().courses()).isNotNull();
    }
}
