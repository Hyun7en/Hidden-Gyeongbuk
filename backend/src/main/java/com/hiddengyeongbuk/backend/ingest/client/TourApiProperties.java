package com.hiddengyeongbuk.backend.ingest.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 언어별 서비스명(KorService2 등)은 여기 없다 — 언어 레지스트리가 갖는다(ADR-018).
 * 여기 남는 건 언어와 무관한 접속 정보뿐이다.
 */
@ConfigurationProperties(prefix = "tourapi")
public record TourApiProperties(String serviceKey, String baseUrl, String mobileOs, String mobileApp) {}
