package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import org.springframework.stereotype.Component;

/**
 * 코스 제약 계산(추천 알고리즘 명세서 2·4.2·4.4장). 기간별 룩업 표였던 것을 <b>공식</b>으로
 * 일반화했다 — 날짜 범위 입력이 임의 박 수를 허용하면서 3단계 표로는 표현할 수 없게 됐다(ADR-020).
 *
 * <p>여기 값들은 <b>채점 대상이 아니다.</b> 목표 장소 수·필요 숙박은 코스를 조립할 때 걸리는
 * 제약일 뿐이고, 그 결과를 다시 점수로 매기지 않는다 — 우리가 정한 값을 우리가 채점하는 구조라
 * 점수 항목에서 제외됐다(ADR-021).
 */
@Component
class CoursePlanningRules {

    private final CourseProperties.Constraints constraints;

    CoursePlanningRules(CourseProperties properties) {
        this.constraints = properties.constraints();
    }

    int maxNights() {
        return constraints.maxNights();
    }

    /** 목표 장소 수 ≈ {@code placesPerDay × days}. 숙박은 여기 포함되지 않는다. */
    int targetPlaceCount(int days) {
        return Math.max(1, (int) Math.round(constraints.placesPerDay() * days));
    }

    /** 거리 예산 {@code Dmax = base(difficulty) × days}. */
    double maxDistanceKm(Difficulty difficulty, int days) {
        return constraints.baseDistanceKm().get(difficulty) * (double) days;
    }

    /** 필요 숙박 = 박 수. 당일치기면 0. */
    int requiredLodging(int nights) {
        return nights;
    }

    /**
     * 지역분산 점수의 분모가 되는 목표 지역 수. 하루에 한 지역을 목표로 하되 <b>범위 안에 실제로
     * 있는 지역 수를 넘지 않는다</b> — 지역 한 곳만 고른 사용자(ADR-019의 "그 지역 집중")에게
     * 도달 불가능한 목표를 세워 놓고 감점하면 안 된다.
     */
    int targetRegionCount(int days, int availableRegions) {
        return Math.max(1, Math.min(days, availableRegions));
    }
}
