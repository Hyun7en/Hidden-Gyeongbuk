package com.hiddengyeongbuk.backend.catalog.dto;

import java.util.List;

/**
 * 프론트 셀렉터 옵션(API 명세서 §2.3). {@code durations}는 제거됐다 — 기간 입력이 enum에서
 * 날짜 범위로 바뀌었다(ADR-020).
 *
 * <p>v0.5의 {@code {code, labelKo, labelEn}}은 언어가 늘면 필드가 늘어나므로 폐기하고,
 * 요청 언어로 해석된 단일 label을 준다.
 *
 * <p>{@code placeTypes}는 S5 필터 칩과 S5·S6 카드 배지가 쓴다(화면 설계서). 프론트가
 * 자체 라벨 사전을 갖지 않게 하려면 목록에 포함돼야 한다.
 */
public record MetaOptionsResponse(
        List<Option> placeTypes,
        List<Option> difficulties,
        List<Option> interests,
        List<Option> hubs,
        String resolvedLang) {

    public record Option(String code, String label) {}
}
