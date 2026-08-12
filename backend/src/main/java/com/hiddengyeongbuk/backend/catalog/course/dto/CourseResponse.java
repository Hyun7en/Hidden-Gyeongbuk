package com.hiddengyeongbuk.backend.catalog.course.dto;

import java.math.BigDecimal;
import java.util.List;

// shareUrl 필드는 두지 않는다 — 공유 링크는 프론트가 자기 라우팅 기준으로 조립한다.
// 백엔드가 프론트 경로를 알게 하면 라우트가 바뀔 때마다 어긋난다(실제로 어긋났었음, FR-C-06).
public record CourseResponse(
        int rank,
        double score,
        Breakdown breakdown,
        Long hubId,
        double totalDistanceKm,
        List<DayResponse> days) {

    public record Breakdown(double interest, double access, double stay, double dispersion, double completeness) {}

    public record DayResponse(int day, List<CoursePlaceResponse> places, LodgingResponse lodging) {}

    public record CoursePlaceResponse(
            int order, Long placeId, String placeType, String title, BigDecimal lat, BigDecimal lng) {}

    public record LodgingResponse(Long placeId, String title) {}
}
