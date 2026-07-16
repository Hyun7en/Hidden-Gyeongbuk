package com.hiddengyeongbuk.backend.catalog.course.dto;

import java.math.BigDecimal;
import java.util.List;

public record CourseResponse(
        int rank,
        double score,
        Breakdown breakdown,
        Long hubId,
        double totalDistanceKm,
        List<DayResponse> days,
        String shareUrl) {

    public record Breakdown(double interest, double access, double stay, double dispersion, double completeness) {}

    public record DayResponse(int day, List<CoursePlaceResponse> places, LodgingResponse lodging) {}

    public record CoursePlaceResponse(int order, Long placeId, String category, String title, BigDecimal lat, BigDecimal lng) {}

    public record LodgingResponse(Long placeId, String title) {}
}
