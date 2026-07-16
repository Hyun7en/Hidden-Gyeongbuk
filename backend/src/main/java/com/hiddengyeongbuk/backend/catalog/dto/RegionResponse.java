package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;

public record RegionResponse(Long regionId, String nameKo, String nameEn, Long hubId, BigDecimal lat, BigDecimal lng) {}
