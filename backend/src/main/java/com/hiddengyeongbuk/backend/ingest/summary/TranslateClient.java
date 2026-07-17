package com.hiddengyeongbuk.backend.ingest.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiddengyeongbuk.backend.support.Retry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Google Cloud Translation API v2(REST) 호출. has_en=false인 장소의 국문 제목·overview를
 * 영어로 번역하는 용도로만 쓴다. Gemini(LLM)에서 이걸로 옮긴 이유(2026-07-17): 순수 번역에는
 * 전용 번역 API가 더 저렴·안정적이고, Gemini 무료 티어는 향후 챗봇 등 LLM이 실제로 필요한
 * 기능을 위해 남겨둔다.
 */
@Component
class TranslateClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TranslateProperties properties;

    TranslateClient(RestClient.Builder builder, ObjectMapper objectMapper, TranslateProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** texts와 같은 순서·같은 개수의 번역 결과를 돌려준다. 한 번의 호출로 여러 필드를 같이 번역한다. */
    List<String> translate(List<String> texts) {
        String url = properties.baseUrl() + "?key=" + URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8);
        Map<String, Object> body = Map.of("q", texts, "source", "ko", "target", "en", "format", "text");

        String responseBody = Retry.withBackoff(MAX_ATTEMPTS, INITIAL_BACKOFF_MS, TranslateClient::isTransient, () -> {
            try {
                return restClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientException e) {
                throw new TranslateException("Google Translate API 호출 실패: " + e.getMessage(), e);
            }
        });
        return extractTranslations(responseBody, texts.size());
    }

    private static boolean isTransient(RuntimeException e) {
        Throwable cause = e.getCause();
        return cause instanceof ResourceAccessException || cause instanceof HttpServerErrorException;
    }

    private List<String> extractTranslations(String responseBody, int expectedCount) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new TranslateException("Google Translate 응답이 JSON이 아님. 원문(앞 300자): " + abbreviate(responseBody), e);
        }
        JsonNode translations = root.path("data").path("translations");
        if (!translations.isArray() || translations.size() != expectedCount) {
            throw new TranslateException(
                    "Google Translate 응답에서 번역 결과를 찾지 못함. 원문(앞 300자): " + abbreviate(responseBody), null);
        }
        List<String> result = new ArrayList<>(expectedCount);
        for (JsonNode translation : translations) {
            result.add(translation.path("translatedText").asText());
        }
        return result;
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "null";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
