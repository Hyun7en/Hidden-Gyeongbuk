package com.hiddengyeongbuk.backend.catalog;

/** API 명세서 공통 Enum(duration). 추천 엔진(P2 후속)에서도 재사용한다. */
public enum Duration {
    ONE_DAY("당일", "One day"),
    ONE_NIGHT("1박2일", "One night"),
    TWO_NIGHT("2박3일", "Two nights");

    private final String labelKo;
    private final String labelEn;

    Duration(String labelKo, String labelEn) {
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
