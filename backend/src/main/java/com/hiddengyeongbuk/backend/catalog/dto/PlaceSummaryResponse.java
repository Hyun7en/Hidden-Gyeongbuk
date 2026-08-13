package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;

/**
 * 목록 항목(API 명세서 §6 PlaceSummary). title·addr은 요청 언어로 해석된 값이다.
 * placeType은 언어 중립 대분류 코드이며, 표시 라벨은 {@code GET /api/meta/options}가 준다(§2.3).
 * tags는 폐기됐다 — 분류축이 placeType 하나로 합쳐졌다(ADR-024).
 */
public record PlaceSummaryResponse(
        Long placeId,
        String placeType,
        String title,
        String addr,
        BigDecimal lat,
        BigDecimal lng,
        String firstImage) {}
