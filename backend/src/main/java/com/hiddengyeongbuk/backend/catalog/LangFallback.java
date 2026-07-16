package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.Place;

/** lang=en 기본값·국문 폴백 규칙(API 명세서 1장)을 장소 제목·설명 조회 전반에서 공유한다. catalog 하위 패키지(course 등)에서도 써야 해서 public이다. */
public final class LangFallback {

    public static final String EN = "en";

    private LangFallback() {}

    public static String title(Place place, String lang) {
        if (EN.equalsIgnoreCase(lang) && isPresent(place.getTitleEn())) {
            return place.getTitleEn();
        }
        return place.getTitleKo();
    }

    public static String overview(Place place, String lang) {
        if (EN.equalsIgnoreCase(lang) && isPresent(place.getOverviewEn())) {
            return place.getOverviewEn();
        }
        return place.getOverviewKo();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
