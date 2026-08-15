package com.hiddengyeongbuk.backend.catalog.course;

import static com.hiddengyeongbuk.backend.catalog.course.CourseTestConfig.BASE_DATE;
import static com.hiddengyeongbuk.backend.catalog.course.CourseTestConfig.RULES;
import static com.hiddengyeongbuk.backend.catalog.course.CourseTestConfig.nights;
import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.region.Region;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseAssemblerTest {

    private final CourseAssembler assembler = new CourseAssembler(RULES, CourseTestConfig.PROPERTIES);

    private final Hub hub =
            Hub.builder().id(1L).code("ANDONG").lat(BigDecimal.valueOf(36.5750)).lng(BigDecimal.valueOf(128.6747)).build();

    @Test
    void 당일코스는_목표_장소수만큼_담고_숙박은_없다() {
        List<Place> attractions = List.of(
                nearbyPlace(1L, 0.01),
                nearbyPlace(2L, 0.02),
                nearbyPlace(3L, 0.03),
                nearbyPlace(4L, 0.04),
                nearbyPlace(5L, 0.05));

        Optional<CourseCandidate> result = assembler.assemble(
                hub,
                attractions,
                List.of(),
                List.of(),
                nights(0),
                Difficulty.NORMAL,
                Set.of(PlaceType.NA),
                Map.of(),
                CourseAssembler.SelectionStrategy.PRESCORE_FIRST);

        assertThat(result).isPresent();
        CourseCandidate candidate = result.get();
        assertThat(candidate.days()).hasSize(1);
        assertThat(candidate.visitPlaceCount()).isEqualTo(4); // 목표 장소수 = round(3.5 × 1일) = 4
        assertThat(candidate.lodgingCount()).isZero();
    }

    @Test
    void 마지막_날을_제외한_모든_날에_숙박이_포함된다() {
        List<Place> attractions = List.of(
                nearbyPlace(1L, 0.01), nearbyPlace(2L, 0.02), nearbyPlace(3L, 0.03),
                nearbyPlace(4L, 0.04), nearbyPlace(5L, 0.05), nearbyPlace(6L, 0.06));
        List<Place> food = List.of(nearbyPlace(20L, PlaceType.FD, 0.07), nearbyPlace(21L, PlaceType.FD, 0.08));
        List<Place> lodging = List.of(nearbyPlace(30L, PlaceType.AC, 0.09));

        Optional<CourseCandidate> result = assembler.assemble(
                hub,
                attractions,
                food,
                lodging,
                nights(1),
                Difficulty.NORMAL,
                Set.of(),
                Map.of(),
                CourseAssembler.SelectionStrategy.PRESCORE_FIRST);

        assertThat(result).isPresent();
        CourseCandidate candidate = result.get();
        assertThat(candidate.days()).hasSize(2);
        assertThat(candidate.days().get(0).lodging()).isNotNull();
        assertThat(candidate.days().get(1).lodging()).isNull();
        assertThat(candidate.lodgingCount()).isEqualTo(1);
    }

    @Test
    void 이동_예산을_초과하는_후보는_선택되지_않는다() {
        // hub에서 아주 멀리 떨어진(약 300km) 후보뿐이고 EASY 당일치기의 Dmax는 60 × 1 = 60km라 아무것도 담지 못한다.
        List<Place> farAttractions = List.of(place(1L, PlaceType.NA, 40.0, 128.7));

        Optional<CourseCandidate> result = assembler.assemble(
                hub,
                farAttractions,
                List.of(),
                List.of(),
                nights(0),
                Difficulty.EASY,
                Set.of(),
                Map.of(),
                CourseAssembler.SelectionStrategy.PRESCORE_FIRST);

        assertThat(result).isEmpty();
    }

    @Test
    void 같은_장소는_같은_코스에_중복으로_담기지_않는다() {
        List<Place> attractions = List.of(nearbyPlace(1L, 0.01), nearbyPlace(2L, 0.02));

        Optional<CourseCandidate> result = assembler.assemble(
                hub,
                attractions,
                List.of(),
                List.of(),
                nights(0),
                Difficulty.NORMAL,
                Set.of(),
                Map.of(),
                CourseAssembler.SelectionStrategy.NEAREST_FIRST);

        assertThat(result).isPresent();
        CourseCandidate candidate = result.get();
        assertThat(candidate.placeIdSet()).hasSize(candidate.visitPlaceCount());
    }

    @Test
    void 여행_기간에_안_열리는_축제는_후보에서_빠진다() {
        // 닫힌 축제를 일정에 넣는 것은 선호의 문제가 아니라 틀린 정보라 점수가 아니라 제외다.
        Place festival = nearbyPlace(1L, PlaceType.EV, 0.01);
        Place ordinary = nearbyPlace(2L, PlaceType.NA, 0.02);
        Map<Long, FestivalPeriod> closed =
                Map.of(1L, new FestivalPeriod(BASE_DATE.plusMonths(3), BASE_DATE.plusMonths(3).plusDays(2)));

        Optional<CourseCandidate> result = assemble(List.of(festival, ordinary), closed, nights(0));

        assertThat(result).isPresent();
        assertThat(result.get().placeIdSet()).containsExactly(2L);
    }

    @Test
    void 기간이_겹치는_축제는_후보에_남고_먼저_담긴다() {
        // 그 시기에만 가능한 경험이라 같은 조건이면 먼저 담을 이유가 있다(사전점수 가산).
        // 축제를 더 멀리 둬서, 가산이 없으면 가까운 일반 장소가 먼저 뽑히게 만든다.
        Place festival = nearbyPlace(1L, PlaceType.EV, 0.05);
        Place ordinary = nearbyPlace(2L, PlaceType.NA, 0.01);
        Map<Long, FestivalPeriod> open = Map.of(1L, new FestivalPeriod(BASE_DATE.minusDays(1), BASE_DATE.plusDays(1)));

        Optional<CourseCandidate> result = assemble(List.of(festival, ordinary), open, nights(0));

        assertThat(result).isPresent();
        assertThat(result.get().days().get(0).places().get(0).getId()).isEqualTo(1L);
    }

    @Test
    void 하루만_겹쳐도_참여할_수_있는_것으로_본다() {
        // 여행 마지막 날이 축제 첫날인 경우 — 경계를 포함한다.
        Place festival = nearbyPlace(1L, PlaceType.EV, 0.01);
        Map<Long, FestivalPeriod> lastDay =
                Map.of(1L, new FestivalPeriod(BASE_DATE.plusDays(2), BASE_DATE.plusDays(9)));

        Optional<CourseCandidate> result = assemble(List.of(festival), lastDay, nights(2));

        assertThat(result).isPresent();
        assertThat(result.get().placeIdSet()).contains(1L);
    }

    @Test
    void 날짜를_모르는_행사는_일반_장소처럼_다룬다() {
        // 결측을 "안 열린다"로 읽으면 수집된 콘텐츠를 근거 없이 잃는다 — 모르는 것은 판단하지 않는다.
        Place undatedFestival = nearbyPlace(1L, PlaceType.EV, 0.01);

        Optional<CourseCandidate> result = assemble(List.of(undatedFestival), Map.of(), nights(0));

        assertThat(result).isPresent();
        assertThat(result.get().placeIdSet()).contains(1L);
    }

    private Optional<CourseCandidate> assemble(
            List<Place> attractions, Map<Long, FestivalPeriod> festivals, TravelDates dates) {
        return assembler.assemble(
                hub,
                attractions,
                List.of(),
                List.of(),
                dates,
                Difficulty.NORMAL,
                Set.of(),
                festivals,
                CourseAssembler.SelectionStrategy.PRESCORE_FIRST);
    }

    private Place nearbyPlace(long id, double offsetDeg) {
        return nearbyPlace(id, PlaceType.NA, offsetDeg);
    }

    private Place nearbyPlace(long id, PlaceType placeType, double offsetDeg) {
        return place(id, placeType, hub.getLat().doubleValue() + offsetDeg, hub.getLng().doubleValue() + offsetDeg);
    }

    private Place place(long id, PlaceType placeType, double lat, double lng) {
        return Place.builder()
                .id(id)
                .placeType(placeType)
                .region(Region.builder().id(1L).build())
                .lat(BigDecimal.valueOf(lat))
                .lng(BigDecimal.valueOf(lng))
                .build();
    }
}
