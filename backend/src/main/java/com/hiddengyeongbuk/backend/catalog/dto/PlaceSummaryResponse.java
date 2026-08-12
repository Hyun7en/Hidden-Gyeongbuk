package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 목록 항목(API 명세서 §6 PlaceSummary). title·addr은 요청 언어로 해석된 값이다.
 * tags는 언어 중립 enum 코드(NATURE 등)이며 표시 라벨은 프론트 i18n이 책임진다(§1.3).
 */
public record PlaceSummaryResponse(
        Long placeId,
        String placeType,
        String title,
        String addr,
        BigDecimal lat,
        BigDecimal lng,
        String firstImage,
        List<String> tags) {}
