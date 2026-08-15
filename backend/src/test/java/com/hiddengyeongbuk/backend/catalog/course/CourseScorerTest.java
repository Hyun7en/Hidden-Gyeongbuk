package com.hiddengyeongbuk.backend.catalog.course;

import static com.hiddengyeongbuk.backend.catalog.course.CourseTestConfig.PROPERTIES;
import static com.hiddengyeongbuk.backend.catalog.course.CourseTestConfig.RULES;
import static com.hiddengyeongbuk.backend.catalog.course.CourseTestConfig.nights;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.PlaceTexts;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.domain.region.Region;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseScorerTest {

    private final CourseScorer scorer = new CourseScorer(PROPERTIES, RULES);

    @Test
    void 관심사가_없으면_관심사_적합도는_항상_만점이다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceType.NA, 1L));
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(0), Difficulty.NORMAL, 1, Map.of());
        assertThat(b.interest()).isEqualTo(100.0);
    }

    @Test
    void 코스가_관심사_일부만_커버하면_비율만큼_점수를_받는다() {
        // 장소 하나의 분류는 하나뿐이라(ADR-024), 자연 장소 1개짜리 코스는 관심사 2개 중
        // 1개만 덮는다 — 구 태그 방식과 달리 장소 수가 커버 상한이다.
        CourseCandidate candidate = candidateOf(place(1L, PlaceType.NA, 1L));
        CourseScorer.Breakdown b =
                scorer.score(candidate, Set.of(PlaceType.NA, PlaceType.HS), nights(0), Difficulty.NORMAL, 1, Map.of());
        assertThat(b.interest()).isEqualTo(50.0);
    }

    @Test
    void 이동거리가_0이면_접근성은_만점이다() {
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceType.NA, 1L)), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 0.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(1), Difficulty.EASY, 1, Map.of());
        assertThat(b.access()).isEqualTo(100.0);
    }

    @Test
    void 이동거리가_예산의_절반이면_접근성은_50점이다() {
        // Dmax = base(EASY 60) × days(2) = 120km
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceType.NA, 1L)), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 60.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(1), Difficulty.EASY, 1, Map.of());
        assertThat(b.access()).isEqualTo(50.0);
    }

    @Test
    void 이동거리가_예산을_초과하면_접근성은_0으로_클램프된다() {
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceType.NA, 1L)), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 150.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(1), Difficulty.EASY, 1, Map.of());
        assertThat(b.access()).isEqualTo(0.0);
    }

    @Test
    void 거리_예산은_박수에_비례해_늘어난다() {
        // 룩업 표를 공식으로 일반화했으므로(ADR-020), 표에 없던 5박도 계산된다 — NORMAL 100 × 6일 = 600km.
        CourseDay day = new CourseDay(1, List.of(place(1L, PlaceType.NA, 1L)), null);
        CourseCandidate candidate = new CourseCandidate(List.of(day), 300.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(5), Difficulty.NORMAL, 1, Map.of());
        assertThat(b.access()).isEqualTo(50.0);
    }

    @Test
    void 당일코스는_지역목표가_1이라_지역분산이_항상_만점이다() {
        CourseCandidate candidate = candidateOf(place(1L, PlaceType.NA, 1L));
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(0), Difficulty.NORMAL, 1, Map.of());
        assertThat(b.dispersion()).isEqualTo(100.0);
    }

    @Test
    void 목표_지역수에_못미치면_지역분산이_비례해서_깎인다() {
        // 2박3일 + 범위에 지역 3곳 -> 목표 3, 실제 방문 2곳 -> 100*(0.5+0.5*(2-1)/(3-1)) = 75
        Place p1 = place(1L, PlaceType.NA, 1L);
        Place p2 = place(2L, PlaceType.NA, 2L);
        CourseCandidate candidate = new CourseCandidate(List.of(new CourseDay(1, List.of(p1, p2), null)), 10.0);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(2), Difficulty.NORMAL, 3, Map.of());
        assertThat(b.dispersion()).isEqualTo(75.0);
    }

    @Test
    void 범위에_지역이_하나뿐이면_긴_일정이어도_지역분산은_만점이다() {
        // 지역을 하나만 고른 사용자(ADR-019 "그 지역 집중")에게 도달 불가능한 목표를 세우고
        // 감점하면 안 된다 — 2박3일이어도 갈 수 있는 지역이 1곳뿐이면 그게 만점이다.
        CourseCandidate candidate = candidateOf(place(1L, PlaceType.NA, 1L));
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(2), Difficulty.NORMAL, 1, Map.of());
        assertThat(b.dispersion()).isEqualTo(100.0);
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
                .build();
        CourseCandidate candidate = candidateOf(complete);
        CourseScorer.Breakdown b = scorer.score(
                candidate, Set.of(), nights(0), Difficulty.NORMAL, 1, textsOf(1L, "설명", "안동시 어딘가"));
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
                .build();
        CourseCandidate candidate = candidateOf(incomplete);
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(0), Difficulty.NORMAL, 1, Map.of());
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
                .build();
        PlaceTranslation ko = translation("ko", "설명", "안동시 어딘가");
        Map<Long, PlaceTexts> fallbackOnly = Map.of(1L, new PlaceTexts(null, ko, ko, "ko"));

        CourseScorer.Breakdown b =
                scorer.score(candidateOf(place), Set.of(), nights(0), Difficulty.NORMAL, 1, fallbackOnly);
        assertThat(b.completeness()).isEqualTo(80.0);
    }

    @Test
    void 네개_항목이_모두_만점이면_최종점수도_만점이다() {
        // 체류 적합도가 빠져 4개가 됐다(ADR-021). 이동거리 0, 관심사 제한 없음, 지역 1곳, 데이터 완비.
        List<Place> complete = new java.util.ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            complete.add(Place.builder()
                    .id((long) i)
                    .region(Region.builder().id(1L).build())
                    .lat(BigDecimal.valueOf(36.5))
                    .lng(BigDecimal.valueOf(128.7))
                    .firstImage("https://img/" + i)
                    .build());
        }
        CourseCandidate candidate = new CourseCandidate(List.of(new CourseDay(1, complete, null)), 0.0);
        Map<Long, PlaceTexts> allComplete = new java.util.HashMap<>();
        for (long i = 1; i <= 4; i++) {
            allComplete.putAll(textsOf(i, "설명", "안동시 어딘가"));
        }
        CourseScorer.Breakdown b = scorer.score(candidate, Set.of(), nights(0), Difficulty.NORMAL, 1, allComplete);
        assertThat(b.total()).isCloseTo(100.0, within(0.001));
    }

    @Test
    void 최종점수는_설정된_가중치대로_합산된다() {
        // 관심사만 0점이고 나머지가 만점이면 35점이 빠진다 — 가중치가 실제로 반영되는지 본다.
        Place complete = Place.builder()
                .id(1L)
                .placeType(PlaceType.NA)
                .region(Region.builder().id(1L).build())
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .firstImage("https://img/1")
                .build();
        CourseCandidate candidate = candidateOf(complete);
        CourseScorer.Breakdown b = scorer.score(
                candidate, Set.of(PlaceType.HS), nights(0), Difficulty.NORMAL, 1, textsOf(1L, "설명", "주소"));

        assertThat(b.interest()).isZero();
        assertThat(b.total()).isCloseTo(65.0, within(0.001));
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

    private static CourseCandidate candidateOf(Place place) {
        return new CourseCandidate(List.of(new CourseDay(1, List.of(place), null)), 0.0);
    }

    private static Place place(long id, PlaceType placeType, long regionId) {
        return Place.builder()
                .id(id)
                .placeType(placeType)
                .region(Region.builder().id(regionId).build())
                .lat(BigDecimal.valueOf(36.5))
                .lng(BigDecimal.valueOf(128.7))
                .build();
    }
}
