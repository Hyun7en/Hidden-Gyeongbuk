package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.support.LanguageRegistry;

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
     * 요청 언어의 표시 라벨. 현재는 ko/en 두 벌만 갖고 있어 그 외 언어는 en으로 떨어진다 —
     * 언어를 늘리려면 MessageSource 기반 i18n으로 옮겨야 한다(후속 과제). enum 코드 자체는
     * 언어 중립이므로 프론트가 자체 i18n으로 표시해도 된다(API 명세서 §1.3).
     */
    public String label(String lang) {
        return LanguageRegistry.BASE.equalsIgnoreCase(lang) ? labelKo : labelEn;
    }
}
