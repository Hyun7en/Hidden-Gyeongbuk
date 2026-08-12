package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.PlaceTexts;
import com.hiddengyeongbuk.backend.domain.place.Place;

/**
 * 데이터 완성도 산정(추천 알고리즘 명세서 4.5장). 5개 필드의 존재 비율이다.
 *
 * <p><b>언어 커버리지를 {@code has_en}이 아니라 번역 행 존재로 판단한다</b>(ADR-017).
 * 구 구현은 "영문이 있는가"를 물었지만, 다국어에서 완성도가 답해야 할 질문은 "<b>요청 언어</b>의
 * 콘텐츠가 있는가"다 — 일본어 사용자에게 영문 유무로 완성도를 매기는 것은 무의미하다.
 * 설명·주소도 place 컬럼이 아니라 해석된 텍스트에서 읽는다.
 */
final class PlaceCompleteness {

    private static final int TOTAL_FIELDS = 5;

    private PlaceCompleteness() {}

    static double score(Place place, PlaceTexts texts) {
        int present = 0;
        if (isPresent(place.getFirstImage())) {
            present++;
        }
        if (isPresent(texts.overview())) {
            present++;
        }
        if (isPresent(texts.addr())) {
            present++;
        }
        if (place.getLat() != null && place.getLng() != null) {
            present++;
        }
        // 요청 언어 행이 실제로 있는가(폴백으로 때운 것이 아니라).
        if (texts.requested() != null) {
            present++;
        }
        return present * 100.0 / TOTAL_FIELDS;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
