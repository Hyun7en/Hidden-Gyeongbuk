package com.hiddengyeongbuk.backend.ingest.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 번역 방향이 <b>호출부가 넘긴 값 그대로</b> 요청에 실리는지 지킨다.
 *
 * <p>2026-08-18까지 이 클래스는 {@code source="ko"}, {@code target="en"}을 안에 박아두고 호출부의
 * 언어 인자를 무시했다. 그래서 두 가지가 조용히 깨졌다 — `lang=ja`로 부르면 영어 번역을 만들어
 * `ja` 행으로 저장하고, 국문이 없어 영문을 원문으로 고른 장소(ADR-016)는 영어를 "한국어"라고
 * 보냈다. <b>에러도 로그도 남지 않아</b> 틀린 데이터가 그대로 쌓이는 종류의 결함이라 고정해 둔다.
 *
 * <p>HTTP를 태우지 않는다 — 무료 한도가 걸린 API라 테스트가 할당량을 태우면 안 되고, 애초에
 * 생성자가 요청 팩토리를 직접 만들어 목으로 바꿔치기할 수도 없다.
 */
class TranslateClientTest {

    @Test
    void 호출부가_넘긴_번역_방향을_그대로_싣는다() {
        Map<String, Object> body = TranslateClient.requestBody(List.of("안동"), "ko", "ja");

        assertThat(body).containsEntry("source", "ko").containsEntry("target", "ja");
    }

    @Test
    void 원문이_국문이_아니어도_그_언어로_보낸다() {
        // 국문이 없어 영문을 원문으로 고른 장소. "항상 ko"로 단정하면 영어를 한국어라고 보내게 되고,
        // 구글은 그대로 돌려주거나 엉뚱한 결과를 낸다.
        Map<String, Object> body = TranslateClient.requestBody(List.of("Ulleungdo"), "en", "ja");

        assertThat(body).containsEntry("source", "en").containsEntry("target", "ja");
    }

    @Test
    void 여러_필드를_한_번의_호출로_보낸다() {
        // 호출 수를 줄이는 것이 무료 한도 관리의 핵심이다 — 필드마다 부르면 3배가 된다.
        Map<String, Object> body =
                TranslateClient.requestBody(List.of("하회마을", "안동의 마을...", "안동시"), "ko", "en");

        assertThat(body.get("q")).isEqualTo(List.of("하회마을", "안동의 마을...", "안동시"));
        assertThat(body).containsEntry("format", "text");
    }

    @Test
    void 원문과_대상_언어가_같으면_요청을_만들지_않는다() {
        // 결과가 원문 그대로인데 할당량만 태운다.
        assertThatThrownBy(() -> TranslateClient.requestBody(List.of("안동"), "ko", "ko"))
                .isInstanceOf(TranslateException.class)
                .hasMessageContaining("같다");
    }

    @Test
    void 대소문자가_달라도_같은_언어로_본다() {
        assertThatThrownBy(() -> TranslateClient.requestBody(List.of("안동"), "ko", "KO"))
                .isInstanceOf(TranslateException.class);
    }

    @Test
    void 번역_방향이_비어_있으면_거부한다() {
        assertThatThrownBy(() -> TranslateClient.requestBody(List.of("안동"), null, "en"))
                .isInstanceOf(TranslateException.class)
                .hasMessageContaining("지정되지 않았다");
    }
}
