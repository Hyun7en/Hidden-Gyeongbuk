package com.hiddengyeongbuk.backend.catalog.dto;

import java.util.List;

/**
 * 프론트 셀렉터 옵션(API 명세서 §2.3). durations는 제거됐다 — 기간 입력이 enum에서
 * 날짜 범위로 바뀌었다(ADR-020).
 *
 * <p>v0.5의 {@code {code, labelKo, labelEn}}은 언어가 늘면 필드가 늘어나므로 폐기하고,
 * 요청 언어로 해석된 단일 label을 준다.
 */
public record MetaOptionsResponse(
        List<Option> difficulties, List<Option> interests, List<Option> hubs, String resolvedLang) {

    public record Option(String code, String label) {}
}
