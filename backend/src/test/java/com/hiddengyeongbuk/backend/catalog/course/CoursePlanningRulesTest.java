package com.hiddengyeongbuk.backend.catalog.course;

import static com.hiddengyeongbuk.backend.catalog.course.CourseTestConfig.RULES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 룩업 표에서 공식으로 바뀐 코스 제약(ADR-020)과 가중치 설정 검증(ADR-021). */
class CoursePlanningRulesTest {

    @Test
    void 목표_장소수는_일수에_비례한다() {
        // 3.5 × days. 표에 없던 기간도 계산된다는 것이 공식화의 요점이다.
        assertThat(RULES.targetPlaceCount(1)).isEqualTo(4);
        assertThat(RULES.targetPlaceCount(2)).isEqualTo(7);
        assertThat(RULES.targetPlaceCount(3)).isEqualTo(11);
        assertThat(RULES.targetPlaceCount(6)).isEqualTo(21);
    }

    @Test
    void 거리예산은_난이도_기준값에_일수를_곱한다() {
        assertThat(RULES.maxDistanceKm(Difficulty.EASY, 1)).isEqualTo(60.0);
        assertThat(RULES.maxDistanceKm(Difficulty.NORMAL, 3)).isEqualTo(300.0);
        assertThat(RULES.maxDistanceKm(Difficulty.LOCAL, 4)).isEqualTo(600.0);
    }

    @Test
    void 필요_숙박은_박수와_같다() {
        assertThat(RULES.requiredLodging(0)).isZero();
        assertThat(RULES.requiredLodging(3)).isEqualTo(3);
    }

    @Test
    void 목표_지역수는_일수와_실제_지역수_중_작은_쪽이다() {
        assertThat(RULES.targetRegionCount(3, 5)).isEqualTo(3);
        assertThat(RULES.targetRegionCount(5, 2)).isEqualTo(2);
        assertThat(RULES.targetRegionCount(1, 0)).isEqualTo(1);
    }

    @Test
    void 박수는_날짜에서_파생하고_같은_날이면_당일치기다() {
        TravelDates sameDay = TravelDates.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1), 14);
        assertThat(sameDay.nights()).isZero();
        assertThat(sameDay.days()).isEqualTo(1);

        TravelDates twoNights = TravelDates.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3), 14);
        assertThat(twoNights.nights()).isEqualTo(2);
        assertThat(twoNights.days()).isEqualTo(3);
    }

    @Test
    void 종료일이_시작일보다_앞서면_거부된다() {
        assertThatThrownBy(() -> TravelDates.of(LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 1), 14))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상한을_넘는_기간은_거부된다() {
        assertThatThrownBy(() -> TravelDates.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), 14))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 14박");
    }

    @Test
    void 지난_날짜는_막지_않는다() {
        // 공유 URL에 날짜가 담기므로 시간이 지나면 과거 링크가 생긴다 — 계산은 그대로 하고
        // 안내는 클라이언트가 한다(ADR-020 결과 ④).
        TravelDates past = TravelDates.of(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 3, 2), 14);
        assertThat(past.nights()).isEqualTo(1);
    }

    @Test
    void 가중치_합계가_100이_아니면_기동에_실패한다() {
        assertThatThrownBy(() -> new CourseProperties.Weights(35, 30, 20, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("합계는 100");
    }

    @Test
    void 난이도가_빠진_거리예산_설정은_기동에_실패한다() {
        assertThatThrownBy(() -> new CourseProperties.Constraints(3.5, 14, Map.of(Difficulty.EASY, 60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NORMAL");
    }
}
