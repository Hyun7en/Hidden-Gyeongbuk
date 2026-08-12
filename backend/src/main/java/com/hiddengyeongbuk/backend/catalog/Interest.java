package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.PlaceTagType;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;

/**
 * API 명세서 공통 Enum(interest). place_tag.tag와의 1:1 대응은 {@link PlaceTagType}에
 * 위임한다 — 관심사 매칭이 한글 라벨 문자열 일치로 동작하므로, 어휘를 여기 중복 정의하면
 * 수집 쪽과 어긋났을 때 조용히 매칭이 깨진다.
 */
public enum Interest {
    NATURE(PlaceTagType.NATURE, "Nature"),
    FOOD(PlaceTagType.FOOD, "Food"),
    TRADITION(PlaceTagType.TRADITION, "Tradition"),
    TEMPLE(PlaceTagType.TEMPLE, "Temple"),
    FESTIVAL(PlaceTagType.FESTIVAL, "Festival"),
    REST(PlaceTagType.REST, "Rest");

    private final PlaceTagType tagType;
    private final String labelEn;

    Interest(PlaceTagType tagType, String labelEn) {
        this.tagType = tagType;
        this.labelEn = labelEn;
    }

    /** 대응하는 place_tag 값(언어 중립 enum 코드). */
    public PlaceTagType tagType() {
        return tagType;
    }

    /** 요청 언어의 표시 라벨. {@link Difficulty#label(String)}과 같은 한계가 있다. */
    public String label(String lang) {
        return LanguageRegistry.BASE.equalsIgnoreCase(lang) ? tagType.labelKo() : labelEn;
    }
}
