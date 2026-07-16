package com.hiddengyeongbuk.backend.catalog;

/** API 명세서 공통 Enum(difficulty). 추천 엔진(P2 후속)에서도 재사용한다. */
public enum Difficulty {
    EASY("쉬움", "Easy"),
    NORMAL("보통", "Normal"),
    LOCAL("로컬경험", "Local experience");

    private final String labelKo;
    private final String labelEn;

    Difficulty(String labelKo, String labelEn) {
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
