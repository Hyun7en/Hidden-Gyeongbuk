package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KoreanNameExtractorTest {

    @Test
    void 단순_괄호_국문명을_뽑는다() {
        assertThat(KoreanNameExtractor.extract("Andong Dam (안동댐)")).isEqualTo("안동댐");
        assertThat(KoreanNameExtractor.extract("Andong Folk Village (안동민속촌)")).isEqualTo("안동민속촌");
    }

    @Test
    void 영문_부제가_앞_괄호에_있으면_맨_끝_한글_괄호를_고른다() {
        assertThat(KoreanNameExtractor.extract(
                        "Andong Gunja Village (Ocheon Historic Site) (안동 군자마을(오천유적지))"))
                .isEqualTo("안동 군자마을(오천유적지)");
    }

    @Test
    void 대괄호_뒤에_붙은_국문명도_뽑는다() {
        assertThat(KoreanNameExtractor.extract("A HILO Andong Branch[Tax Refund Shop](아일로 안동점)"))
                .isEqualTo("아일로 안동점");
    }

    @Test
    void 한글_괄호가_없으면_null이다() {
        assertThat(KoreanNameExtractor.extract("Andong Dam")).isNull();
        assertThat(KoreanNameExtractor.extract("Some Place (Branch Office)")).isNull();
        assertThat(KoreanNameExtractor.extract(null)).isNull();
    }

    @Test
    void 정규화는_공백을_제거한다() {
        assertThat(KoreanNameExtractor.normalize("안동 군자마을")).isEqualTo("안동군자마을");
        assertThat(KoreanNameExtractor.normalize("  안동댐 ")).isEqualTo("안동댐");
        assertThat(KoreanNameExtractor.normalize(null)).isNull();
        assertThat(KoreanNameExtractor.normalize("   ")).isNull();
    }

    @Test
    void 추출과_정규화를_엮으면_띄어쓰기_차이를_흡수한다() {
        String extracted = KoreanNameExtractor.extract("Andong Hahoe Folk Village (안동 하회마을)");
        assertThat(KoreanNameExtractor.normalize(extracted)).isEqualTo("안동하회마을");
    }
}
