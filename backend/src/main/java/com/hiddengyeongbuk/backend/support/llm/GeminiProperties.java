package com.hiddengyeongbuk.backend.support.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String baseUrl, String model) {}
