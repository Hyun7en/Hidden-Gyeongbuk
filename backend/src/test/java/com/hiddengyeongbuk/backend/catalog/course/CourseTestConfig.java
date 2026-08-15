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
                    3.5, 14, Map.of(Difficulty.EASY, 60, Difficulty.NORMAL, 100, Difficulty.LOCAL, 150)),
            new CourseProperties.Festival(20));

    static final CoursePlanningRules RULES = new CoursePlanningRules(PROPERTIES);

    /** 기준일. 축제 겹침 판정이 이 날짜를 기준으로 돈다. */
    static final LocalDate BASE_DATE = LocalDate.of(2026, 5, 1);

    static TravelDates nights(int nights) {
        return new TravelDates(BASE_DATE, BASE_DATE.plusDays(nights));
    }

    private CourseTestConfig() {}
}
