package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.support.LanguageRegistry;

/**
 * 선택지 라벨의 언어 해석을 한곳에 모은다(API 명세서 §2.3).
 *
 * <p>enum마다 {@code label(lang)}을 각자 구현하면 "어느 언어를 고르는가" 규칙이 흩어지고,
 * 언어를 늘릴 때 세 군데를 따로 고쳐야 한다.
 *
 * <p><b>현재는 ko/en 두 벌만 갖고 있어 그 외 언어는 en으로 떨어진다.</b> 언어를 실제로
 * 늘릴 때는 여기를 MessageSource 기반 i18n으로 바꾼다 — enum 코드 자체는 언어 중립이라
 * 그 교체가 다른 계층에 파급되지 않는다.
 */
final class OptionLabel {

    private OptionLabel() {}

    static String of(String lang, String labelKo, String labelEn) {
        return LanguageRegistry.BASE.equalsIgnoreCase(lang) ? labelKo : labelEn;
    }
}
