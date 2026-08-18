package com.hiddengyeongbuk.backend.catalog.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 관심사가 <b>필터</b>로 작동하는지(아키텍처 §5.4 2단계).
 *
 * <p>2026-08-18까지는 관심사가 사전점수에만 반영돼 후보를 줄이지 않았다. 실측으로 안동 거점 ·
 * HS+NA 요청에 7곳 중 6곳이 요청 밖(상점 3, 음식 2, 체험 1)으로 나왔다. 사전점수는 순서만 바꾸는데
 * {@code NEAREST_FIRST} 전략은 거리에 1000을 곱하고 사전점수는 최대 100이라, 관심사가 거리에
 * 완전히 밀렸기 때문이다.
 */
class AttractionPoolTest {

    private static Place place(long id, PlaceType type) {
        return Place.builder()
                .id(id)
                .placeType(type)
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.6))
                .build();
    }

    private static final List<Place> ALL = List.of(
            place(1L, PlaceType.HS),
            place(2L, PlaceType.NA),
            place(3L, PlaceType.SH),
            place(4L, PlaceType.EX),
            place(5L, PlaceType.FD),
            place(6L, PlaceType.AC));

    private static List<PlaceType> typesOf(List<Place> places) {
        return places.stream().map(Place::getPlaceType).toList();
    }

    @Test
    void 고른_관심사만_남는다() {
        List<Place> pool = CourseRecommendationService.attractionPool(ALL, Set.of(PlaceType.HS, PlaceType.NA));

        assertThat(typesOf(pool)).containsExactly(PlaceType.HS, PlaceType.NA);
    }

    @Test
    void 관심사를_고르지_않으면_음식_숙박만_빠진다() {
        List<Place> pool = CourseRecommendationService.attractionPool(ALL, Set.of());

        assertThat(typesOf(pool)).containsExactly(PlaceType.HS, PlaceType.NA, PlaceType.SH, PlaceType.EX);
    }

    @Test
    void 음식_숙박은_관심사로_골라도_관광_후보에서_빠진다() {
        // 끼니·숙박 자리에서 따로 뽑으므로 관광 자리에 넣으면 중복이다(추천 알고리즘 §5).
        List<Place> pool = CourseRecommendationService.attractionPool(ALL, Set.of(PlaceType.HS, PlaceType.FD));

        assertThat(typesOf(pool)).containsExactly(PlaceType.HS);
    }

    @Test
    void 관심사가_음식_숙박뿐이면_관광은_거르지_않는다() {
        // 여기서 필터를 걸면 관광 자리가 통째로 비어 코스가 끼니·숙박만 남는다.
        List<Place> pool = CourseRecommendationService.attractionPool(ALL, Set.of(PlaceType.FD, PlaceType.AC));

        assertThat(typesOf(pool)).containsExactly(PlaceType.HS, PlaceType.NA, PlaceType.SH, PlaceType.EX);
    }

    @Test
    void 일치하는_장소가_없으면_빈_풀을_돌려준다() {
        // 조용히 다른 분류로 채우지 않는다 — 호출부가 422로 조건을 넓히라고 안내한다.
        List<Place> pool = CourseRecommendationService.attractionPool(ALL, Set.of(PlaceType.LS));

        assertThat(pool).isEmpty();
    }
}
