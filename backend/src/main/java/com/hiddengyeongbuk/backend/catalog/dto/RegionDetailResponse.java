package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 지역 상세(API 명세서 §2.5). 지역 소개 화면(S2)용이며 목록에 없는 {@code description}을 준다.
 *
 * <p>장소 목록은 담지 않는다 — 같은 화면에서 {@code GET /api/places?regionId=}로 따로 가져간다.
 * 상세가 목록을 중복 포함하면 두 곳의 페이징·필터 규칙이 어긋난다.
 */
public record RegionDetailResponse(
        Long regionId,
        String name,
        Long hubId,
        BigDecimal lat,
        BigDecimal lng,
        String description,
        List<String> images,
        String resolvedLang) {}
