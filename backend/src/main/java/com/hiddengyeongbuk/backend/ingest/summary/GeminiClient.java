package com.hiddengyeongbuk.backend.ingest.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gemini Flash generateContent REST 호출(외부 API 연동 설계서 4장). has_en=false인 장소의
 * 국문 overview를 쉬운 영어 요약으로 바꾸는 용도로만 쓴다.
 *
 * gemini.model 기본값(gemini-2.0-flash)은 Google AI Studio에서 최신 모델명으로 아직 실측
 * 검증되지 않았다 — 처음 호출 시 404/모델명 오류가 나면 gemini.model 값을 교체할 것.
 */
@Component
class GeminiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiProperties properties;

    GeminiClient(RestClient.Builder builder, ObjectMapper objectMapper, GeminiProperties properties) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    String generateSummary(String prompt) {
        String url = properties.baseUrl() + "/models/" + properties.model() + ":generateContent?key="
                + URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8);
        Map<String, Object> body =
                Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

        String responseBody;
        try {
            responseBody = restClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new GeminiException("Gemini API 호출 실패: " + e.getMessage(), e);
        }
        return extractText(responseBody);
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
