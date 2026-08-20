package com.hiddengyeongbuk.backend.ingest.translate.title;

import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;

/**
 * 제목 하나를 다시 만드는 데 필요한 재료.
 *
 * <p><b>입력이 언제나 국문인 것이 핵심이다.</b> 직전에 만든 영문 제목을 다시 먹이지 않으므로
 * 몇 번을 재실행해도 번역이 누적 열화되지 않는다(ADR-026의 재실행 안전성 근거 ①).
 *
 * @param contentId 국문 {@code staging_place.source_content_id}. 교정·대장의 키다 —
 *     내부 {@code place_id}는 재판정 때 바뀌어 키가 될 수 없다(2026-08-20 실측)
 * @param target 덮어쓸 대상 행. 실패하면 이 행을 그대로 두는 것이 fallback이다
 */
record TitleCandidate(
        long placeId,
        String contentId,
        String placeType,
        String koTitle,
        String koOverview,
        String koAddr,
        PlaceTranslation target) {

    /** 화면에 나가는 현재 제목 — Gemini가 실패하면 이게 남는다. */
    String currentTitle() {
        return target.getTitle();
    }
}
