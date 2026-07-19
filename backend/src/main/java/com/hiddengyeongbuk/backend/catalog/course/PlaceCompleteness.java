package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.domain.place.Place;

/** 추천 알고리즘 명세서 4.5장 데이터 완성도 산정(필드 5개: firstImage/overview/addr/lat·lng/hasEn). */
final class PlaceCompleteness {

    private static final int TOTAL_FIELDS = 5;

    private PlaceCompleteness() {}

    static double score(Place place) {
        int present = 0;
        if (isPresent(place.getFirstImage())) {
            present++;
        }
        if (isPresent(place.getOverviewKo())) {
            present++;
        }
        if (isPresent(place.getAddr())) {
            present++;
        }
        if (place.getLat() != null && place.getLng() != null) {
            present++;
        }
        if (place.isHasEn()) {
            present++;
        }
        return present * 100.0 / TOTAL_FIELDS;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
