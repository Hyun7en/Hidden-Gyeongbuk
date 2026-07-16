package com.hiddengyeongbuk.backend.ingest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailCommonItem;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TourApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TourApiProperties properties;

    public TourApiClient(RestClient.Builder builder, ObjectMapper objectMapper, TourApiProperties properties) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<AreaBasedSyncItem> fetchAreaBasedSyncList(
            String lDongRegnCd, String lDongSignguCd, int pageNo, int numOfRows) {
        String body = get(
                properties.korBaseUrl(),
                "/areaBasedSyncList2",
                Map.of(
                        "lDongRegnCd", lDongRegnCd,
                        "lDongSignguCd", lDongSignguCd,
                        "pageNo", String.valueOf(pageNo),
                        "numOfRows", String.valueOf(numOfRows)));
        return requireSuccess(body, AreaBasedSyncItem.class);
    }

    public Optional<DetailCommonItem> fetchDetailCommonKor(String contentId) {
        return fetchDetailCommon(properties.korBaseUrl(), contentId);
    }

    public Optional<DetailCommonItem> fetchDetailCommonEng(String contentId) {
        return fetchDetailCommon(properties.engBaseUrl(), contentId);
    }

    private Optional<DetailCommonItem> fetchDetailCommon(String baseUrl, String contentId) {
        // detailCommon2(v2)는 v1의 defaultYN/overviewYN/addrinfoYN 등 YN 플래그를 받지 않는다.
        // contentId만 넘기면 overview를 포함한 기본 필드가 항상 반환된다 (외부 API 연동 설계서 2.7).
        String body = get(baseUrl, "/detailCommon2", Map.of("contentId", contentId));
        return requireSuccess(body, DetailCommonItem.class).stream().findFirst();
    }

    private <T> List<T> requireSuccess(String body, Class<T> itemType) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "TourAPI 응답이 JSON이 아님. 원문(앞 500자): " + abbreviate(body), e);
        }

        String resultCode = TourApiResponseParser.resultCode(root);
        if (!"0000".equals(resultCode)) {
            String resultMsg = root.path("response").path("header").path("resultMsg").asText("");
            throw new TourApiException(resultCode, resultMsg, abbreviate(body));
        }
        return TourApiResponseParser.extractItems(root, itemType, objectMapper);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "null";
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private String get(String baseUrl, String path, Map<String, String> params) {
        // UriComponentsBuilder의 기본 인코딩은 RFC 3986 기준 '+'를 허용 문자로 보고 그대로 둔다.
        // TourAPI serviceKey에는 '+'가 포함될 수 있는데, 서버가 이를 공백으로 오인해 인증이 깨진다.
        // URLEncoder로 모든 값을 직접 percent-encode해서 이 문제를 피한다 (curl --data-urlencode와 동일 효과).
        Map<String, String> allParams = new LinkedHashMap<>();
        allParams.put("serviceKey", properties.serviceKey());
        allParams.put("MobileOS", properties.mobileOs());
        allParams.put("MobileApp", properties.mobileApp());
        allParams.put("_type", "json");
        allParams.putAll(params);

        String queryString = allParams.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        URI uri = URI.create(baseUrl + path + "?" + queryString);

        return restClient.get().uri(uri).retrieve().body(String.class);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
