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

    /**
     * 요청 언어의 표시 라벨. 프론트는 자체 라벨 사전을 갖지 않고 이 값을 쓴다 —
     * 언어를 늘릴 때 백엔드 한 곳만 고치면 되게 하기 위함이다(API 명세서 §2.3).
     */
    public String label(String lang) {
        return OptionLabel.of(lang, labelKo, labelEn);
    }
}
