package com.hiddengyeongbuk.backend.ingest.translate.title;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 모델이 돌려준 JSON 배열을 읽는다.
 *
 * <p><b>왜 배열인가.</b> 무료 티어가 분당 5회라 1건씩 보내면 1,105건에 3.7시간이 걸린다
 * (2026-08-20 실측 429: {@code GenerateRequestsPerMinutePerProjectPerModel-FreeTier, limit: 5}).
 * 20건씩 묶으면 56회로 끝난다 — 호출 수가 작업 시간을 지배하므로 묶는 것이 유일한 지렛대다.
 *
 * <p>모델이 코드펜스나 앞뒤 설명을 붙여도 대괄호 구간만 떼어낸다. 배열 전체를 못 읽으면
 * 그 배치는 통째로 실패로 보고, <b>대장에 적지 않으므로 다음 실행이 다시 시도한다.</b>
 */
@Component
class TitleBatchParser {

    private final ObjectMapper objectMapper;

    TitleBatchParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    <T> List<T> parse(String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("빈 응답");
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("JSON 배열을 찾지 못함: " + abbreviate(raw));
        }
        try {
            return objectMapper.readValue(
                    raw.substring(start, end + 1),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new IllegalStateException("배치 응답 파싱 실패: " + abbreviate(raw), e);
        }
    }

    private static String abbreviate(String value) {
        return value.length() > 300 ? value.substring(0, 300) + "…" : value;
    }
}
