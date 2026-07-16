package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlaceSummaryResponse(
        Long placeId,
        String category,
        String title,
        String addr,
        BigDecimal lat,
        BigDecimal lng,
        String firstImage,
        List<String> tags) {}
