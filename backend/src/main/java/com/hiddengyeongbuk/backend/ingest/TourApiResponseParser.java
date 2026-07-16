package com.hiddengyeongbuk.backend.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * TourAPI JSON 응답은 결과 0건일 때 items가 빈 문자열("")로, 1건일 때 item이 배열이 아닌
 * 단일 객체로 내려오는 경우가 있어 표준 Jackson 레코드 바인딩만으로는 처리가 안 된다.
 * response.body.items.item 경로를 직접 순회해 0/1/N건을 동일하게 다룬다.
 */
final class TourApiResponseParser {

    private TourApiResponseParser() {}

    static String resultCode(JsonNode root) {
        return root.path("response").path("header").path("resultCode").asText("");
    }

    static <T> List<T> extractItems(JsonNode root, Class<T> itemType, ObjectMapper mapper) {
        JsonNode itemNode = root.path("response").path("body").path("items").path("item");

        if (itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }
        if (itemNode.isArray()) {
            List<T> result = new ArrayList<>();
            for (JsonNode node : itemNode) {
                result.add(convert(node, itemType, mapper));
            }
            return result;
        }
        if (itemNode.isObject()) {
            return List.of(convert(itemNode, itemType, mapper));
        }
        // items가 "" (빈 문자열)로 오는 결과 0건 케이스
        return List.of();
    }

    private static <T> T convert(JsonNode node, Class<T> itemType, ObjectMapper mapper) {
        try {
            return mapper.treeToValue(node, itemType);
        } catch (Exception e) {
            throw new IllegalStateException("TourAPI item 파싱 실패: " + node, e);
        }
    }
}
