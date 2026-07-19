package com.hiddengyeongbuk.backend.domain.place;

/**
 * place_translation 텍스트의 출처.
 * OFFICIAL = 해당 언어 TourAPI 서비스(EngService2 등)의 공식 콘텐츠를 이름·좌표 매칭으로 확보한 것.
 * TRANSLATE = 공식이 없어 국문을 Google Cloud Translation으로 기계 번역한 것.
 * 언어당 1행이며, 공식이 매칭되면 기존 TRANSLATE 행을 OFFICIAL로 덮어쓴다.
 */
public enum TranslationSource {
    OFFICIAL,
    TRANSLATE
}
