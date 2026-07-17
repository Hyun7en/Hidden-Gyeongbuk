package com.hiddengyeongbuk.backend.ingest.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "translate")
public record TranslateProperties(String apiKey, String baseUrl) {}
