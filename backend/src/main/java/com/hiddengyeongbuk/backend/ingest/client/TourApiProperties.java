package com.hiddengyeongbuk.backend.ingest.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourapi")
public record TourApiProperties(
        String serviceKey, String korBaseUrl, String engBaseUrl, String mobileOs, String mobileApp) {}
