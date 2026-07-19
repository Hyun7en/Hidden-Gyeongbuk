package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;

/** lang 정규화 + 국문 폴백 규칙(API 명세서 1장). catalog 하위 패키지(course 등)에서도 써서 public이다. */
public final class LangFallback {

    public static final String EN = "en";
    public static final String KO = "ko";

    private LangFallback() {}

    /**
     * 클라이언트가 보낸 lang을 "en"/"ko" 둘 중 하나로 정규화한다. lang은 @Cacheable 키에
     * 들어가므로 자유 문자열을 그대로 흘리면 임의 값마다 캐시 엔트리가 생겨 키 공간이
     * 무한해진다 — 컨트롤러에서 서비스로 넘기기 전에 항상 이걸 거칠 것. (다국어 확장 시 여기서
     * 지원 언어 집합을 늘린다.)
     */
    public static String normalize(String lang) {
        return KO.equalsIgnoreCase(lang) ? KO : EN;
    }

    /**
     * 제목 폴백: 외국어 요청이고 그 언어 번역행(place_translation)에 제목이 있으면 그걸, 없으면 국문.
     * 공식(OFFICIAL)·기계번역(TRANSLATE)은 같은 행에 통합돼 있어 source와 무관하게 title을 쓴다.
     */
    public static String title(Place place, PlaceTranslation translation, String lang) {
        if (isForeign(lang) && translation != null && isPresent(translation.getTitle())) {
            return translation.getTitle();
        }
        return place.getTitleKo();
    }

    public static String overview(Place place, PlaceTranslation translation, String lang) {
        if (isForeign(lang) && translation != null && isPresent(translation.getOverview())) {
            return translation.getOverview();
        }
        return place.getOverviewKo();
    }

    public static String addr(Place place, PlaceTranslation translation, String lang) {
        if (isForeign(lang) && translation != null && isPresent(translation.getAddr())) {
            return translation.getAddr();
        }
        return place.getAddr();
    }

    private static boolean isForeign(String lang) {
        return !KO.equalsIgnoreCase(lang);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
