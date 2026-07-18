package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.PlaceTagType;

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

    public String labelKo() {
        return tagType.labelKo();
    }

    public String labelEn() {
        return labelEn;
    }
}
