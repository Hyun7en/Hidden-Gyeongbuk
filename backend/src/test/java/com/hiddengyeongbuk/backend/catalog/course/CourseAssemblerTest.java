package com.hiddengyeongbuk.backend.catalog.course;

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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CourseAssemblerTest {

    private final CourseAssembler assembler = new CourseAssembler(RULES);

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
                CourseAssembler.SelectionStrategy.NEAREST_FIRST);

        assertThat(result).isPresent();
        CourseCandidate candidate = result.get();
        assertThat(candidate.placeIdSet()).hasSize(candidate.visitPlaceCount());
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
