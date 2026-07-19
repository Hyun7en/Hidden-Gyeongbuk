package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PlaceDetailResponse(
        Long placeId,
        String contentId,
        String category,
        String title,
        String titleKo,
        String addr,
        String tel,
        BigDecimal lat,
        BigDecimal lng,
        String overview,
        String firstImage,
        List<String> images,
        List<String> tags,
        String translationSource,
        FestivalResponse festival) {

    public record FestivalResponse(LocalDate startDate, LocalDate endDate) {}
}
