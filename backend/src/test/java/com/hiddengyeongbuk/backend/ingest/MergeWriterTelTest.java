package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 연락처 정제 규칙.
 *
 * <p>소스가 `tel` 칸에 전화번호만 넣지 않는다. 영문 축제는 안내문을 넣는데(2026-08-19 실측 8건,
 * 111~113자) 컬럼이 VARCHAR(50)이라 적재가 실패하고 있었다 — 영문 수집의 failed=8이 이것이었다.
 * V6에서 폭을 255로 넓혔고, 화면에 그대로 나가는 place.tel은 여기서 태그를 걷어낸다.
 */
class MergeWriterTelTest {

    @Test
    void 실측_원문은_연락처_두_개다() {
        // 봉화은어축제 실제 응답(112자). 1330 관광통역안내 + 축제사무국 번호를 <br>로 이었다.
        String raw = "• 1330 Travel Hotline: +82-2-1330 <br>(Korean, English, Japanese, Chinese)"
                + " <br> • For more info: +82-54-674-3053";

        assertThat(MergeWriter.displayableTel(raw))
                .isEqualTo("• 1330 Travel Hotline: +82-2-1330\n(Korean, English, Japanese, Chinese)\n"
                        + "• For more info: +82-54-674-3053");
    }

    @Test
    void br은_줄바꿈으로_남긴다() {
        // 소스가 "여기서 끊어라"를 알려준 자리다. 공백으로 이으면 103자 한 줄이 된다.
        assertThat(MergeWriter.displayableTel("054-000-0000<BR>안내")).isEqualTo("054-000-0000\n안내");
        assertThat(MergeWriter.displayableTel("054-000-0000<br />안내")).isEqualTo("054-000-0000\n안내");
        assertThat(MergeWriter.displayableTel("054-000-0000 <br> 안내")).isEqualTo("054-000-0000\n안내");
    }

    @Test
    void 일반_전화번호는_그대로_둔다() {
        assertThat(MergeWriter.displayableTel("054-840-6394")).isEqualTo("054-840-6394");
    }

    @Test
    void 전화번호를_골라내려_들지_않는다() {
        // 무엇이 번호이고 무엇이 설명인지 판정하는 규칙을 우리가 소유하지 않는다.
        // 태그만 걷어내고 나머지는 원문 순서를 지킨다.
        String raw = "축제사무국 054-000-0000<br>관광안내 1330";

        assertThat(MergeWriter.displayableTel(raw)).isEqualTo("축제사무국 054-000-0000\n관광안내 1330");
    }

    @Test
    void 자르지_않는다() {
        // 컬럼이 255자로 충분하다. 잘라내면 축제사무국 번호를 잃는다.
        String raw = "• 1330 Travel Hotline: +82-2-1330 <br>(Korean, English, Japanese, Chinese)"
                + " <br> • For more info: +82-54-674-3053";

        String cleaned = MergeWriter.displayableTel(raw);
        assertThat(cleaned.length()).isGreaterThan(50);
        assertThat(cleaned).contains("+82-54-674-3053");
    }

    @Test
    void 태그만_있으면_null이다() {
        assertThat(MergeWriter.displayableTel("<br>")).isNull();
        assertThat(MergeWriter.displayableTel("   ")).isNull();
        assertThat(MergeWriter.displayableTel(null)).isNull();
    }

    // ── 이미지 URL 스킴 ────────────────────────────────────────────────────────
    // 소스가 같은 호스트를 http와 https로 섞어 준다(실측 685 : 486). http를 그대로 두면
    // HTTPS로 배포했을 때만 mixed content로 막혀 카드 절반이 빈 이미지가 된다 —
    // 로컬 개발에서는 구조적으로 드러나지 않는 고장이라 수집 단계에서 맞춰 둔다.

    @Test
    void http_이미지는_https로_올린다() {
        assertThat(MergeWriter.httpsImageUrl("http://tong.visitkorea.or.kr/cms/resource/48/2993048_image2_1.jpg"))
                .isEqualTo("https://tong.visitkorea.or.kr/cms/resource/48/2993048_image2_1.jpg");
    }

    @Test
    void 이미_https면_건드리지_않는다() {
        String url = "https://tong.visitkorea.or.kr/cms/resource/76/3516376_image2_1.jpg";
        assertThat(MergeWriter.httpsImageUrl(url)).isEqualTo(url);
    }

    @Test
    void 스킴_대소문자를_가리지_않는다() {
        assertThat(MergeWriter.httpsImageUrl("HTTP://example.com/a.jpg")).isEqualTo("https://example.com/a.jpg");
    }

    @Test
    void 빈_값은_null이다() {
        // 이미지 없음은 정상 상태다(실측 134건). 빈 문자열을 그대로 두면 화면이 "있다"로 오해한다.
        assertThat(MergeWriter.httpsImageUrl(null)).isNull();
        assertThat(MergeWriter.httpsImageUrl("   ")).isNull();
    }

    @Test
    void http로_시작하지_않는_값은_그대로_둔다() {
        // 프로토콜 상대 경로나 예상 밖의 값을 임의로 고치지 않는다 — 우리가 모르는 형식이다.
        assertThat(MergeWriter.httpsImageUrl("//cdn.example.com/a.jpg")).isEqualTo("//cdn.example.com/a.jpg");
    }
}
