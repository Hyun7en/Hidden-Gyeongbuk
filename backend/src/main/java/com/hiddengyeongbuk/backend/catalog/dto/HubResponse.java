package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;

public record HubResponse(
        Long hubId, String nameKo, String nameEn, BigDecimal lat, BigDecimal lng, String description, String descriptionEn) {}
