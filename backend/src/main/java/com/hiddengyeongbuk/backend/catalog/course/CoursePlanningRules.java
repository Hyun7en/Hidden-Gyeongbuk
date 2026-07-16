package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import java.util.Map;

/** 추천 알고리즘 명세서 2·4.2·4.3·4.4장의 기간·난이도별 상수(목표 장소 수, 필요 숙박, Dmax, 목표 지역 수)를 담는다. */
final class CoursePlanningRules {

    private record Range(int min, int max) {}

    private static final Map<Duration, Integer> DAYS =
            Map.of(Duration.ONE_DAY, 1, Duration.ONE_NIGHT, 2, Duration.TWO_NIGHT, 3);

    private static final Map<Duration, Range> PLACE_RANGE = Map.of(
            Duration.ONE_DAY, new Range(3, 4),
            Duration.ONE_NIGHT, new Range(5, 7),
            Duration.TWO_NIGHT, new Range(8, 11));

    private static final Map<Duration, Integer> REQUIRED_LODGING =
            Map.of(Duration.ONE_DAY, 0, Duration.ONE_NIGHT, 1, Duration.TWO_NIGHT, 2);

    private static final Map<Duration, Integer> TARGET_REGION_COUNT =
            Map.of(Duration.ONE_DAY, 1, Duration.ONE_NIGHT, 2, Duration.TWO_NIGHT, 3);

    private static final Map<Difficulty, Map<Duration, Integer>> DMAX = Map.of(
            Difficulty.EASY, Map.of(Duration.ONE_DAY, 60, Duration.ONE_NIGHT, 120, Duration.TWO_NIGHT, 180),
            Difficulty.NORMAL, Map.of(Duration.ONE_DAY, 100, Duration.ONE_NIGHT, 200, Duration.TWO_NIGHT, 300),
            Difficulty.LOCAL, Map.of(Duration.ONE_DAY, 150, Duration.ONE_NIGHT, 300, Duration.TWO_NIGHT, 450));

    private CoursePlanningRules() {}

    static int days(Duration duration) {
        return DAYS.get(duration);
    }

    static int minPlaces(Duration duration) {
        return PLACE_RANGE.get(duration).min();
    }

    static int maxPlaces(Duration duration) {
        return PLACE_RANGE.get(duration).max();
    }

    /** 코스 조립 시 목표로 삼는 장소 수(목표 범위의 중간값, 5장 조립 규칙에서 사용). */
    static int targetPlaceCount(Duration duration) {
        Range range = PLACE_RANGE.get(duration);
        return (int) Math.round((range.min() + range.max()) / 2.0);
    }

    static int requiredLodging(Duration duration) {
        return REQUIRED_LODGING.get(duration);
    }

    static int targetRegionCount(Duration duration) {
        return TARGET_REGION_COUNT.get(duration);
    }

    static double dmax(Difficulty difficulty, Duration duration) {
        return DMAX.get(difficulty).get(duration);
    }
}
