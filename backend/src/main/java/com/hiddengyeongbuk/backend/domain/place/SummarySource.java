package com.hiddengyeongbuk.backend.domain.place;

/**
 * place_summary 텍스트의 생성 출처.
 * LLM은 Gemini 요약 시절(~2026-07-17)의 레거시 값 — 이후 새 행은 전부 TRANSLATE(Google
 * Cloud Translation)다. 기존 LLM 행은 어떤 엔진 산출물인지 구분할 수 없어 그대로 둔다.
 */
public enum SummarySource {
    TOURAPI,
    LLM,
    TRANSLATE
}
