package com.hiddengyeongbuk.backend.catalog.dto;

public record PlaceNearbyResponse(PlaceSummaryResponse place, double distanceKm) {}
