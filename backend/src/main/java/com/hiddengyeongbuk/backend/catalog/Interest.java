package com.hiddengyeongbuk.backend.catalog;

/**
 * API 명세서 공통 Enum(interest). place_tag.tag(자연/음식/전통/사찰/축제/휴식)와 1:1 대응.
 */
public enum Interest {
    NATURE("자연", "Nature"),
    FOOD("음식", "Food"),
    TRADITION("전통", "Tradition"),
    TEMPLE("사찰", "Temple"),
    FESTIVAL("축제", "Festival"),
    REST("휴식", "Rest");

    private final String labelKo;
    private final String labelEn;

    Interest(String labelKo, String labelEn) {
        this.labelKo = labelKo;
        this.labelEn = labelEn;
    }

    public String labelKo() {
        return labelKo;
    }

    public String labelEn() {
        return labelEn;
    }
}
