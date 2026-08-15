package com.hiddengyeongbuk.backend.catalog.course;

import java.time.LocalDate;

/**
 * 축제 개최 기간. 후보 선별 단계에서 여행 날짜와 겹치는지 판단하는 데만 쓴다(추천 알고리즘 §4.7).
 *
 * <p><b>계절 반영이 이것 하나인 이유</b>: "벚꽃·단풍" 같은 계절성 태그를 새로 설계하는 것은 자체
 * 어휘를 다시 소유하는 일이라 관광공사 분류 채택과 어긋나 폐기됐다(ADR-024, ADR-020 후속 주석).
 * 축제 날짜 겹침은 수집 데이터로 실제 계산되는 유일한 계절 근거다 — 추정치가 아니다.
 *
 * <p>시작일·종료일이 모두 있는 축제만 이 타입이 된다. 날짜를 모르는 행사는 판단하지 않는다.
 */
record FestivalPeriod(LocalDate startDate, LocalDate endDate) {

    /** 하루라도 겹치면 참여할 수 있다 — 경계 포함. */
    boolean overlaps(TravelDates trip) {
        return !startDate.isAfter(trip.endDate()) && !endDate.isBefore(trip.startDate());
    }
}
