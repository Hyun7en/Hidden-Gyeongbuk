package com.hiddengyeongbuk.backend.catalog.course.dto;

import java.util.List;

/**
 * 코스 추천 응답(API 명세서 §4.1).
 *
 * @param constraints 이 요청에 적용된 코스 제약. 체류 적합도가 점수에서 빠지면서 생긴
 *     블록이다(ADR-021) — 채점 결과가 아니라 "무엇을 기준으로 짰는지"를 알리는 값이라
 *     {@code breakdown}이 아니라 여기 있다. 클라이언트는 이걸로 "3곳 · 최대 100km · 숙박 1"
 *     같은 안내를 띄운다.
 * @param resolvedLang 실제로 사용된 언어. 요청 언어에 번역이 없어 폴백했을 수 있으므로
 *     클라이언트가 출처를 표시할 수 있도록 함께 내린다.
 */
public record CourseListResponse(List<CourseResponse> courses, Constraints constraints, String resolvedLang) {

    /**
     * @param placeCount 목표 장소 수(숙박 제외)
     * @param maxDistanceKm 거리 예산 Dmax
     * @param lodgingCount 필요 숙박 수 = 박 수
     */
    public record Constraints(int placeCount, double maxDistanceKm, int lodgingCount) {}
}
