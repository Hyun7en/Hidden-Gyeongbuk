package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;

/** 지역 목록 항목(API 명세서 §2.2). 소개 글(description)은 목록에 없고 상세에서만 준다. */
public record RegionResponse(
        Long regionId,
        String name,
        Long hubId,
        BigDecimal lat,
        BigDecimal lng,
        String firstImage,
        String resolvedLang) {}
