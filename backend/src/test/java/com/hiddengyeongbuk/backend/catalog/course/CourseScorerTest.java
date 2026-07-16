package com.hiddengyeongbuk.backend.catalog.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.catalog.Interest;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.region.Region;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseScorerTest {

    private final CourseScorer scorer = new CourseScorer();

    @Test
    void 관심사가_없으면_관심사_적합도는_항상_만점이다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceCategory.관광지, 1L, "자연"));
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL);
        assertThat(b.interest()).isEqualTo(100.0);
    }

    @Test
    void 코스가_관심사_일부만_커버하면_비율만큼_점수를_받는다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceCategory.관광지, 1L, "자연"));
        CourseScorer.Breakdown b =
                scorer.score(candidate, Set.of(Interest.NATURE, Interest.TRADITION), Duration.ONE_DAY, Difficulty.NORMAL);
        assertThat(b.interest()).isEqualTo(50.0);
    }

    @Test
    void 이동거리가_0이면_접근성은_만점이다() {
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceCategory.관광지, 1L, "자연")), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 0.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.EASY);
        assertThat(b.access()).isEqualTo(100.0);
    }

    @Test
    void 이동거리가_예산의_절반이면_접근성은_50점이다() {
        // EASY/ONE_NIGHT의 Dmax=120km
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceCategory.관광지, 1L, "자연")), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 60.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.EASY);
        assertThat(b.access()).isEqualTo(50.0);
    }

    @Test
    void 이동거리가_예산을_초과하면_접근성은_0으로_클램프된다() {
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceCategory.관광지, 1L, "자연")), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 150.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.EASY);
        assertThat(b.access()).isEqualTo(0.0);
    }

    @Test
    void 목표_장소수_범위내에_필요숙박도_충족하면_체류적합도는_만점이다() {
        // ONE_NIGHT: 목표 장소 수 5~7, 필요 숙박 1
        CourseDay day1 = new CourseDay(1, places(1, 3, PlaceCategory.관광지), place(100L, PlaceCategory.숙박, 1L, "휴식"));
        CourseDay day2 = new CourseDay(2, places(4, 3, PlaceCategory.관광지), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day1, day2), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.NORMAL);
        assertThat(b.stay()).isEqualTo(100.0);
    }

    @Test
    void 목표_장소수보다_적으면_이탈분만큼_감점된다() {
        // ONE_NIGHT 목표 최소 5, 실제 3개 -> 이탈 2개 -> 100 - 15*2 = 70 (숙박은 충족)
        CourseDay day = new CourseDay(1, places(1, 3, PlaceCategory.관광지), place(100L, PlaceCategory.숙박, 1L, "휴식"));
        CourseCandidate candidate = new CourseCandidate(List.of(day), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.NORMAL);
        assertThat(b.stay()).isEqualTo(70.0);
    }

    @Test
    void 필요_숙박이_누락되면_40점_감점된다() {
        CourseDay day1 = new CourseDay(1, places(1, 3, PlaceCategory.관광지), null);
        CourseDay day2 = new CourseDay(2, places(4, 3, PlaceCategory.관광지), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day1, day2), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.NORMAL);
        assertThat(b.stay()).isEqualTo(60.0);
    }

    @Test
    void 당일코스는_지역목표가_1이라_지역분산이_항상_만점이다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceCategory.관광지, 1L, "자연"));
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL);
        assertThat(b.dispersion()).isEqualTo(100.0);
    }

    @Test
    void 목표_지역수에_못미치면_지역분산이_비례해서_깎인다() {
        // TWO_NIGHT 목표 지역수 3, 실제 방문 2곳 -> 100*(0.5+0.5*(2-1)/(3-1)) = 75
        Place p1 = place(1L, PlaceCategory.관광지, 1L, "자연");
        Place p2 = place(2L, PlaceCategory.관광지, 2L, "자연");
        CourseDay day = new CourseDay(1, List.of(p1, p2), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.TWO_NIGHT, Difficulty.NORMAL);
        assertThat(b.dispersion()).isEqualTo(75.0);
    }

    @Test
    void 필수_5개_필드가_모두_있으면_데이터완성도는_만점이다() {
        Place complete = Place.builder()
                .id(1L)
                .contentId("c1")
                .contentTypeId(12)
                .category(PlaceCategory.관광지)
                .region(Region.builder().id(1L).build())
                .titleKo("완전한 장소")
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .addr("안동시 어딘가")
                .overviewKo("설명")
                .firstImage("https://img/1")
                .hasEn(true)
                .tags(List.of())
                .build();
        CourseCandidate candidate = candidateOf(complete);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL);
        assertThat(b.completeness()).isEqualTo(100.0);
    }

    @Test
    void 필드가_일부_없으면_데이터완성도가_비례해서_깎인다() {
        // firstImage, overview 없음 -> 5개 중 3개만 존재 -> 60점
        Place incomplete = Place.builder()
                .id(1L)
                .contentId("c1")
                .contentTypeId(12)
                .category(PlaceCategory.관광지)
                .region(Region.builder().id(1L).build())
                .titleKo("불완전한 장소")
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .addr("안동시 어딘가")
                .hasEn(true)
                .tags(List.of())
                .build();
        CourseCandidate candidate = candidateOf(incomplete);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL);
        assertThat(b.completeness()).isEqualTo(60.0);
    }

    @Test
    void 다섯개_항목이_모두_만점이면_최종점수도_만점이다() {
        // ONE_DAY 목표 장소수 3~4개를 모두 완전한 데이터로 채우고 이동거리 0, 관심사 제한 없음 -> 5개 항목 전부 만점
        List<Place> complete = new java.util.ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            complete.add(Place.builder()
                    .id((long) i)
                    .contentId("c" + i)
                    .contentTypeId(12)
                    .category(PlaceCategory.관광지)
                    .region(Region.builder().id(1L).build())
                    .titleKo("만점 장소" + i)
                    .lat(BigDecimal.valueOf(36.5))
                    .lng(BigDecimal.valueOf(128.7))
                    .addr("안동시 어딘가")
                    .overviewKo("설명")
                    .firstImage("https://img/" + i)
                    .hasEn(true)
                    .tags(List.of())
                    .build());
        }
        CourseCandidate candidate = new CourseCandidate(List.of(new CourseDay(1, complete, null)), 0.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL);
        assertThat(b.total()).isCloseTo(100.0, within(0.001));
    }

    private static CourseCandidate candidateOf(Place place) {
        return new CourseCandidate(List.of(new CourseDay(1, List.of(place), null)), 0.0);
    }

    private static List<Place> places(long startId, int count, PlaceCategory category) {
        List<Place> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(place(startId + i, category, 1L, "자연"));
        }
        return result;
    }

    private static Place place(long id, PlaceCategory category, long regionId, String... tagsKo) {
        return Place.builder()
                .id(id)
                .contentId("c" + id)
                .contentTypeId(12)
                .category(category)
                .region(Region.builder().id(regionId).build())
                .titleKo("장소" + id)
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .hasEn(false)
                .tags(Arrays.stream(tagsKo).map(t -> PlaceTag.builder().tag(t).build()).toList())
                .build();
    }
}
