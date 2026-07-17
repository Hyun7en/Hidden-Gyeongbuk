package com.hiddengyeongbuk.backend.ingest.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiddengyeongbuk.backend.support.Retry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gemini Flash generateContent REST 호출(외부 API 연동 설계서 4장). has_en=false인 장소의
 * 국문 제목·overview를 영어로 전문 번역하는 용도로만 쓴다(2026-07-17부터 요약이 아니라 번역).
 *
 * gemini.model 기본값은 gemini-flash-latest다 — 구글이 "현재 권장 Flash 모델"을 계속
 * 가리켜주는 별칭이라, 특정 버전을 박아두면 몇 달 뒤 또 단종/차단으로 깨지는 문제를 피한다
 * (2026-07-16 실측: gemini-2.0-flash 429 단종, gemini-2.5-flash 404 신규계정 차단, 존재하지도
 * 않는 gemini-3-flash 404를 차례로 겪은 뒤 이 별칭으로 정착). application.yml 참고.
 */
@Component
class GeminiClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiProperties properties;

    GeminiClient(RestClient.Builder builder, ObjectMapper objectMapper, GeminiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        // Gemini flash-latest는 thinking 모델이라 응답에 시간이 좀 걸릴 수 있어 여유를 둔다.
        requestFactory.setReadTimeout(30_000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    String generateSummary(String prompt) {
        String url = properties.baseUrl() + "/models/" + properties.model() + ":generateContent?key="
                + URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8);
        Map<String, Object> body =
                Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        String responseBody = Retry.withBackoff(MAX_ATTEMPTS, INITIAL_BACKOFF_MS, GeminiClient::isTransient, () -> {
            try {
                return restClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientException e) {
                throw new GeminiException("Gemini API 호출 실패: " + e.getMessage(), e);
            }
        });
        return extractText(responseBody);
    }

    // 네트워크 타임아웃·5xx(예: 오늘 실측한 503 "high demand")만 재시도한다. 401/404 같은
    // 설정 오류나 429(쿼터 초과, 수십 초 단위 대기가 필요)는 짧은 재시도로 풀리지 않는다.
    private static boolean isTransient(RuntimeException e) {
        Throwable cause = e.getCause();
        return cause instanceof ResourceAccessException || cause instanceof HttpServerErrorException;
    }

    private String extractText(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new GeminiException("Gemini 응답이 JSON이 아님. 원문(앞 300자): " + abbreviate(responseBody), e);
        }
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new GeminiException(
                    "Gemini 응답에서 요약 텍스트를 찾지 못함. 원문(앞 300자): " + abbreviate(responseBody), null);
        }
        return textNode.asText().trim();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "null";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
