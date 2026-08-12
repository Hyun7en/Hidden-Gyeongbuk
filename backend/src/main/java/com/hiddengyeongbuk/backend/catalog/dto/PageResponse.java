package com.hiddengyeongbuk.backend.catalog.dto;

import java.util.List;

/**
 * 페이지 응답. resolvedLang은 목록 전체에 대한 요청 언어 해석 결과이며, 개별 항목이
 * 폴백된 경우는 상세 조회의 translationSource로 확인한다(API 명세서 §3.1).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, String resolvedLang) {}
