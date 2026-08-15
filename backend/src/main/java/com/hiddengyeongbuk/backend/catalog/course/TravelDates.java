package com.hiddengyeongbuk.backend.catalog.course;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 여행 날짜 범위. {@code duration} enum(당일/1박2일/2박3일)을 대체한다(ADR-020).
 *
 * <p>박 수·일 수는 저장하지 않고 날짜에서 파생한다 — 둘을 따로 들고 다니면 어긋날 수 있고,
 * 어긋난 쪽이 어느 쪽인지 알 수 없게 된다.
 *
 * <p><b>지난 날짜를 막지 않는다.</b> 공유 URL에 날짜가 담기므로 시간이 지나면 과거 링크가
 * 생기는데, 그때 코스를 못 보게 하는 것보다 그대로 계산해 보여주고 클라이언트가 안내 배너를
 * 띄우는 편이 낫다(ADR-020 결과 ④).
 */
record TravelDates(LocalDate startDate, LocalDate endDate) {

    /**
     * @param maxNights 상한. 조립 비용과 거리 예산이 일 수에 비례해 늘어나므로 무한정 받을 수 없다.
     *     상한을 넘긴 요청은 400으로 돌려보낸다.
     */
    static TravelDates of(LocalDate startDate, LocalDate endDate, int maxNights) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate와 endDate는 필수입니다");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate는 startDate보다 앞설 수 없습니다: %s ~ %s".formatted(startDate, endDate));
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        if (nights > maxNights) {
            throw new IllegalArgumentException(
                    "여행 기간은 최대 %d박까지 지원합니다 — 요청은 %d박입니다".formatted(maxNights, nights));
        }
        return new TravelDates(startDate, endDate);
    }

    /** 숙박 일수. 당일치기는 0. */
    int nights() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate);
    }

    /** 여행 일수 = 박 수 + 1. */
    int days() {
        return nights() + 1;
    }
}
