package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.Place;

/** lang=en 기본값·국문 폴백 규칙(API 명세서 1장)을 장소 제목·설명 조회 전반에서 공유한다. catalog 하위 패키지(course 등)에서도 써야 해서 public이다. */
public final class LangFallback {

    public static final String EN = "en";
    public static final String KO = "ko";

    private LangFallback() {}

    /**
     * 클라이언트가 보낸 lang을 "en"/"ko" 둘 중 하나로 정규화한다. lang은 @Cacheable 키에
     * 들어가므로 자유 문자열을 그대로 흘리면 임의 값마다 캐시 엔트리가 생겨 키 공간이
     * 무한해진다(ConcurrentMapCache는 만료·크기 제한 없음) — 컨트롤러에서 서비스로 넘기기
     * 전에 항상 이걸 거칠 것.
     */
    public static String normalize(String lang) {
        return KO.equalsIgnoreCase(lang) ? KO : EN;
    }

    public static String title(Place place, String lang) {
        return title(place, null, lang);
    }

    /**
     * 3단계 폴백: TourAPI 영문 제목(place.title_en) → 우리가 번역해둔 제목
     * (place_summary.title_en, {@link TranslatedTitleLookup}로 일괄 조회) → 국문 제목.
     * 번역 백필이 진행될수록 2단계에서 잡히는 장소가 늘어난다.
     */
    public static String title(Place place, String translatedTitleEn, String lang) {
        if (EN.equalsIgnoreCase(lang)) {
            if (isPresent(place.getTitleEn())) {
                return place.getTitleEn();
            }
            if (isPresent(translatedTitleEn)) {
                return translatedTitleEn;
            }
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
