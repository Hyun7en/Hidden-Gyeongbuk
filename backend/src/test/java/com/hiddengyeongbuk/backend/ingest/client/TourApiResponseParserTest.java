package com.hiddengyeongbuk.backend.ingest.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TourAPI 응답의 실측 변형 3종(0건=items가 빈 문자열, 1건=item이 배열 아닌 단일 객체, N건=배열)을
 * 모두 같은 List로 정규화하는지 검증한다 — 표준 Jackson 바인딩으로는 처리 안 되는 지점이라
 * 파서에 실수가 생기면 수집 전체가 조용히 깨진다.
 */
class TourApiResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 결과가_배열이면_전부_파싱한다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[
                  {"contentid":"1","title":"하회마을"},{"contentid":"2","title":"봉정사"}]}}}}
                """;
        List<AreaBasedSyncItem> items =
                TourApiResponseParser.extractItems(mapper.readTree(body), AreaBasedSyncItem.class, mapper);
        assertThat(items).hasSize(2);
        assertThat(items.get(1).title()).isEqualTo("봉정사");
    }

    @Test
    void 결과가_1건이면_단일_객체로_와도_리스트로_감싼다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":
                  {"contentid":"1","title":"하회마을"}}}}}
                """;
        List<AreaBasedSyncItem> items =
                TourApiResponseParser.extractItems(mapper.readTree(body), AreaBasedSyncItem.class, mapper);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).contentid()).isEqualTo("1");
    }

    @Test
    void 결과가_0건이면_items가_빈_문자열로_와도_빈_리스트를_돌려준다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":""}}}
                """;
        List<AreaBasedSyncItem> items =
                TourApiResponseParser.extractItems(mapper.readTree(body), AreaBasedSyncItem.class, mapper);
        assertThat(items).isEmpty();
    }

    @Test
    void resultCode를_헤더에서_읽는다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"05","resultMsg":"TIMEOUT"}}}
                """;
        assertThat(TourApiResponseParser.resultCode(mapper.readTree(body))).isEqualTo("05");
    }

    @Test
    void item_파싱_실패는_원본_노드를_담아_예외를_던진다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":
                  {"contentid":{"nested":"object-not-string"}}}}}}
                """;
        var root = mapper.readTree(body);
        assertThatThrownBy(() -> TourApiResponseParser.extractItems(root, AreaBasedSyncItem.class, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파싱 실패");
    }
}
