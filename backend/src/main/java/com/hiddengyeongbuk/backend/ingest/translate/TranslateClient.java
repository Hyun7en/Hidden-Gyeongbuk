package com.hiddengyeongbuk.backend.ingest.translate;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Cloud Translation API v2(REST) 호출. 번역 방향은 <b>호출부가 정한다</b> — 이 클래스는
 * 특정 언어를 전제하지 않는다(ADR-018).
 *
 * <p>Gemini(LLM)에서 이걸로 옮긴 이유(2026-07-17): 순수 번역에는 전용 번역 API가 더 저렴·안정적이고,
 * Gemini 무료 티어는 향후 챗봇 등 LLM이 실제로 필요한 기능을 위해 남겨둔다.
 *
 * <p>일일 문자 수 할당량(`v2 and v3 general model characters per day`)이 소진되면 429가 아니라
 * <b>403 {@code userRateLimitExceeded}</b>가 온다. 초당 요청 제한과 <b>같은 reason</b>이라 응답만
 * 보고는 둘을 가릴 수 없다(2026-07-17 속도 제한 / 2026-08-17 일일 소진 양쪽 실측). 할당량은
 * 태평양 시간 자정에 리셋되므로 한국 시간 16:00까지는 전날 몫을 쓴다.
 */
@Component
class TranslateClient {

    private static final int MAX_ATTEMPTS = 4;
    private static final long INITIAL_BACKOFF_MS = 3_000;

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

    /**
     * texts와 같은 순서·같은 개수의 번역 결과를 돌려준다. 한 번의 호출로 여러 필드를 같이 번역한다.
     *
     * <p><b>언어를 인자로 받는다.</b> 2026-08-18까지는 {@code source="ko"}, {@code target="en"}이
     * 이 메서드 안에 박혀 있었는데, 호출부는 언어를 골라 넘기는 구조였다. 그래서 두 가지가
     * 조용히 깨졌다 — (1) `lang=ja`로 부르면 영어 번역을 만들어 `ja` 행으로 저장했고,
     * (2) 국문이 없어 영문을 원문으로 고른 장소는 영어를 "한국어"라고 보냈다. 에러도 로그도
     * 남지 않아 틀린 데이터가 그대로 쌓이는 종류의 결함이었다.
     *
     * <p>언어를 추가할 때 이 파일을 고쳐야 한다면 그것 자체가 설계 위반이다(ADR-018).
     *
     * @param sourceLang 원문 언어. 추측하지 않는다 — 호출부가 실제로 어느 행을 원문으로 골랐는지 안다
     * @param targetLang 번역할 언어
     */
    List<String> translate(List<String> texts, String sourceLang, String targetLang) {
        String url = properties.baseUrl() + "?key=" + URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8);
        Map<String, Object> body = requestBody(texts, sourceLang, targetLang);

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

    /**
     * 요청 본문 조립. 번역 방향이 <b>넘겨받은 값 그대로</b> 실리는지가 이 클래스의 핵심 계약이라
     * 따로 빼서 테스트 대상으로 둔다 — 생성자가 요청 팩토리를 직접 만들어 HTTP 계층을 목으로
     * 바꿔치기할 수 없기 때문이다.
     */
    static Map<String, Object> requestBody(List<String> texts, String sourceLang, String targetLang) {
        if (sourceLang == null || targetLang == null) {
            throw new TranslateException("번역 방향이 지정되지 않았다: " + sourceLang + " → " + targetLang, null);
        }
        // 같은 언어끼리 부르면 할당량만 태우고 결과는 원문 그대로다. 호출 전에 막는다.
        if (sourceLang.equalsIgnoreCase(targetLang)) {
            throw new TranslateException("원문과 대상 언어가 같다: " + sourceLang, null);
        }
        return Map.of("q", texts, "source", sourceLang, "target", targetLang, "format", "text");
    }

    // 429가 아니라 403으로 내려오는 게 이 API의 특징이라(레거시 v2 인터페이스), 응답 본문에서
    // "userRateLimitExceeded" 문자열로 판별해야 한다 — 권한 오류(잘못된 키 등) 403과 구분하기
    // 위함. 재시도 없이 실패 처리했던 초당/분당 속도 제한(2026-07-17 실측, 백필 47건 연속 실패)을
    // 여기서 재시도 대상에 포함시켜 해결한다.
    private static boolean isTransient(RuntimeException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ResourceAccessException || cause instanceof HttpServerErrorException) {
            return true;
        }
        if (cause instanceof HttpClientErrorException.Forbidden forbidden) {
            return forbidden.getResponseBodyAsString().contains("userRateLimitExceeded");
        }
        return false;
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
