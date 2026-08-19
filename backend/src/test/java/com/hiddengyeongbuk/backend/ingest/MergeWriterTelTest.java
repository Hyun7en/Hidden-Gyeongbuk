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
    void br_태그를_공백으로_바꾼다() {
        String raw = "• 1330 Travel Hotline: +82-2-1330<br>(Korean, English, Japanese, Chinese)";

        assertThat(MergeWriter.displayableTel(raw))
                .isEqualTo("• 1330 Travel Hotline: +82-2-1330 (Korean, English, Japanese, Chinese)");
    }

    @Test
    void 대문자와_자기닫는_형태도_처리한다() {
        // 실측 8건 안에서도 <br>, <BR>, <br> 앞뒤 공백이 섞여 있었다.
        assertThat(MergeWriter.displayableTel("054-000-0000<BR>안내")).isEqualTo("054-000-0000 안내");
        assertThat(MergeWriter.displayableTel("054-000-0000<br />안내")).isEqualTo("054-000-0000 안내");
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

        assertThat(MergeWriter.displayableTel(raw)).isEqualTo("축제사무국 054-000-0000 관광안내 1330");
    }

    @Test
    void 자르지_않는다() {
        // 컬럼이 255자로 충분하다. 잘라내면 정보를 잃는다.
        String raw = "• 1330 Travel Hotline: +82-2-1330<br>(Korean, English, Japanese, Chinese)";

        assertThat(MergeWriter.displayableTel(raw).length()).isGreaterThan(50);
    }

    @Test
    void 태그만_있으면_null이다() {
        assertThat(MergeWriter.displayableTel("<br>")).isNull();
        assertThat(MergeWriter.displayableTel("   ")).isNull();
        assertThat(MergeWriter.displayableTel(null)).isNull();
    }
}
