package com.hiddengyeongbuk.backend.catalog.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.catalog.Interest;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceTagType;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.catalog.PlaceTexts;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.region.Region;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseScorerTest {

    private final CourseScorer scorer = new CourseScorer();

    @Test
    void 관심사가_없으면_관심사_적합도는_항상_만점이다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceType.ATTRACTION, 1L, PlaceTagType.NATURE.name()));
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL, Map.of());
        assertThat(b.interest()).isEqualTo(100.0);
    }

    @Test
    void 코스가_관심사_일부만_커버하면_비율만큼_점수를_받는다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceType.ATTRACTION, 1L, PlaceTagType.NATURE.name()));
        CourseScorer.Breakdown b =
                scorer.score(candidate, Set.of(Interest.NATURE, Interest.TRADITION), Duration.ONE_DAY, Difficulty.NORMAL, Map.of());
        assertThat(b.interest()).isEqualTo(50.0);
    }

    @Test
    void 이동거리가_0이면_접근성은_만점이다() {
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceType.ATTRACTION, 1L, PlaceTagType.NATURE.name())), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 0.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.EASY, Map.of());
        assertThat(b.access()).isEqualTo(100.0);
    }

    @Test
    void 이동거리가_예산의_절반이면_접근성은_50점이다() {
        // EASY/ONE_NIGHT의 Dmax=120km
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceType.ATTRACTION, 1L, PlaceTagType.NATURE.name())), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 60.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.EASY, Map.of());
        assertThat(b.access()).isEqualTo(50.0);
    }

    @Test
    void 이동거리가_예산을_초과하면_접근성은_0으로_클램프된다() {
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceType.ATTRACTION, 1L, PlaceTagType.NATURE.name())), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 150.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.EASY, Map.of());
        assertThat(b.access()).isEqualTo(0.0);
    }

    @Test
    void 목표_장소수_범위내에_필요숙박도_충족하면_체류적합도는_만점이다() {
        // ONE_NIGHT: 목표 장소 수 5~7, 필요 숙박 1
        CourseDay day1 = new CourseDay(1, places(1, 3, PlaceType.ATTRACTION), place(100L, PlaceType.STAY, 1L, PlaceTagType.REST.name()));
        CourseDay day2 = new CourseDay(2, places(4, 3, PlaceType.ATTRACTION), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day1, day2), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.NORMAL, Map.of());
        assertThat(b.stay()).isEqualTo(100.0);
    }

    @Test
    void 목표_장소수보다_적으면_이탈분만큼_감점된다() {
        // ONE_NIGHT 목표 최소 5, 실제 3개 -> 이탈 2개 -> 100 - 15*2 = 70 (숙박은 충족)
        CourseDay day = new CourseDay(1, places(1, 3, PlaceType.ATTRACTION), place(100L, PlaceType.STAY, 1L, PlaceTagType.REST.name()));
        CourseCandidate candidate = new CourseCandidate(List.of(day), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.NORMAL, Map.of());
        assertThat(b.stay()).isEqualTo(70.0);
    }

    @Test
    void 필요_숙박이_누락되면_40점_감점된다() {
        CourseDay day1 = new CourseDay(1, places(1, 3, PlaceType.ATTRACTION), null);
        CourseDay day2 = new CourseDay(2, places(4, 3, PlaceType.ATTRACTION), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day1, day2), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_NIGHT, Difficulty.NORMAL, Map.of());
        assertThat(b.stay()).isEqualTo(60.0);
    }

    @Test
    void 당일코스는_지역목표가_1이라_지역분산이_항상_만점이다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceType.ATTRACTION, 1L, PlaceTagType.NATURE.name()));
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL, Map.of());
        assertThat(b.dispersion()).isEqualTo(100.0);
    }

    @Test
    void 목표_지역수에_못미치면_지역분산이_비례해서_깎인다() {
        // TWO_NIGHT 목표 지역수 3, 실제 방문 2곳 -> 100*(0.5+0.5*(2-1)/(3-1)) = 75
        Place p1 = place(1L, PlaceType.ATTRACTION, 1L, PlaceTagType.NATURE.name());
        Place p2 = place(2L, PlaceType.ATTRACTION, 2L, PlaceTagType.NATURE.name());
        CourseDay day = new CourseDay(1, List.of(p1, p2), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), Duration.TWO_NIGHT, Difficulty.NORMAL, Map.of());
        assertThat(b.dispersion()).isEqualTo(75.0);
    }

    @Test
    void 필수_5개_필드가_모두_있으면_데이터완성도는_만점이다() {
        // 설명·주소·언어 커버리지는 이제 place가 아니라 요청 언어의 번역 행에서 온다(ADR-017).
        Place complete = Place.builder()
                .id(1L)
                .region(Region.builder().id(1L).build())
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .firstImage("https://img/1")
                .tags(List.of())
                .build();
        CourseCandidate candidate = candidateOf(complete);
        CourseScorer.Breakdown b = scorer.score(
                candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL, textsOf(1L, "설명", "안동시 어딘가"));
        assertThat(b.completeness()).isEqualTo(100.0);
    }

    @Test
    void 요청_언어_번역이_없으면_완성도가_깎인다() {
        // firstImage 없음 + 요청 언어 행 없음(설명·주소도 함께 없음) -> 5개 중 좌표 1개만 -> 20점
        Place incomplete = Place.builder()
                .id(1L)
                .region(Region.builder().id(1L).build())
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .tags(List.of())
                .build();
        CourseCandidate candidate = candidateOf(incomplete);
        CourseScorer.Breakdown b =
                scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL, Map.of());
        assertThat(b.completeness()).isEqualTo(20.0);
    }

    @Test
    void 폴백으로_텍스트만_채워지면_언어_커버리지_몫은_못_받는다() {
        // ko 행만 있고 요청 언어(en) 행이 없는 상태 — 설명·주소는 폴백으로 보이지만
        // "요청 언어의 콘텐츠가 있는가"에는 해당하지 않는다.
        Place place = Place.builder()
                .id(1L)
                .region(Region.builder().id(1L).build())
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .firstImage("https://img/1")
                .tags(List.of())
                .build();
        PlaceTranslation ko = translation("ko", "설명", "안동시 어딘가");
        Map<Long, PlaceTexts> fallbackOnly = Map.of(1L, new PlaceTexts(null, ko, ko, "ko"));

        CourseScorer.Breakdown b =
                scorer.score(candidateOf(place), Set.of(), Duration.ONE_DAY, Difficulty.NORMAL, fallbackOnly);
        assertThat(b.completeness()).isEqualTo(80.0);
    }

    private static PlaceTranslation translation(String lang, String overview, String addr) {
        return PlaceTranslation.builder()
                .lang(lang)
                .title("제목")
                .overview(overview)
                .addr(addr)
                .source(TranslationSource.OFFICIAL)
                .build();
    }

    private static Map<Long, PlaceTexts> textsOf(long placeId, String overview, String addr) {
        PlaceTranslation row = translation("en", overview, addr);
        return Map.of(placeId, new PlaceTexts(row, row, row, "en"));
    }

    @Test
    void 다섯개_항목이_모두_만점이면_최종점수도_만점이다() {
        // ONE_DAY 목표 장소수 3~4개를 모두 완전한 데이터로 채우고 이동거리 0, 관심사 제한 없음 -> 5개 항목 전부 만점
        List<Place> complete = new java.util.ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            complete.add(Place.builder()
                    .id((long) i)
                    .region(Region.builder().id(1L).build())
                    .lat(BigDecimal.valueOf(36.5))
                    .lng(BigDecimal.valueOf(128.7))
                    .firstImage("https://img/" + i)
                    .tags(List.of())
                    .build());
        }
        CourseCandidate candidate = new CourseCandidate(List.of(new CourseDay(1, complete, null)), 0.0);
        // 완성도 만점에는 요청 언어의 번역 행이 필요하다(ADR-017) — 4개 장소 모두 채운다.
        Map<Long, PlaceTexts> allComplete = new java.util.HashMap<>();
        for (long i = 1; i <= 4; i++) {
            allComplete.putAll(textsOf(i, "설명", "안동시 어딘가"));
        }
        CourseScorer.Breakdown b =
                scorer.score(candidate, Set.of(), Duration.ONE_DAY, Difficulty.NORMAL, allComplete);
        assertThat(b.total()).isCloseTo(100.0, within(0.001));
    }

    private static CourseCandidate candidateOf(Place place) {
        return new CourseCandidate(List.of(new CourseDay(1, List.of(place), null)), 0.0);
    }

    private static List<Place> places(long startId, int count, PlaceType placeType) {
        List<Place> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(place(startId + i, placeType, 1L, PlaceTagType.NATURE.name()));
        }
        return result;
    }

    private static Place place(long id, PlaceType placeType, long regionId, String... tagsKo) {
        return Place.builder()
                .id(id)
                .placeType(placeType)
                .region(Region.builder().id(regionId).build())
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .tags(Arrays.stream(tagsKo).map(t -> PlaceTag.builder().tag(t).build()).toList())
                .build();
    }
}
