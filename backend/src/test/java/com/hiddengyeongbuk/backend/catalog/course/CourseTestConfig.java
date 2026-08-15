package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import java.time.LocalDate;
import java.util.Map;

/**
 * 코스 단위 테스트가 공유하는 설정값. {@code application.yml}의 값을 그대로 복제한다 —
 * 테스트가 운영 설정과 다른 수치로 돌면 통과해도 아무것도 보증하지 못한다.
 */
final class CourseTestConfig {

    static final CourseProperties PROPERTIES = new CourseProperties(
            new CourseProperties.Weights(35, 30, 20, 15),
            new CourseProperties.Constraints(
                    3.5, 14, Map.of(Difficulty.EASY, 60, Difficulty.NORMAL, 100, Difficulty.LOCAL, 150)));

    static final CoursePlanningRules RULES = new CoursePlanningRules(PROPERTIES);

    /** 임의의 기준일 — 계절 가중이 없으므로 어느 날이든 결과가 같다. */
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 5, 1);

    static TravelDates nights(int nights) {
        return new TravelDates(BASE_DATE, BASE_DATE.plusDays(nights));
    }

    private CourseTestConfig() {}
}
