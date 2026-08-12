package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;

/**
 * 교통 거점(API 명세서 §2.1). nameKo/nameEn 대신 요청 언어로 해석된 단일 name을 준다 —
 * 언어별 필드를 두면 언어를 늘릴 때마다 응답 스키마가 바뀐다.
 */
public record HubResponse(
        Long hubId,
        String code,
        String name,
        String description,
        BigDecimal lat,
        BigDecimal lng,
        String resolvedLang) {}
