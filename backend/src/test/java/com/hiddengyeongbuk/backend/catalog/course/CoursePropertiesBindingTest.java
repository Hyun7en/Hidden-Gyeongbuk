package com.hiddengyeongbuk.backend.catalog.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * 설정 바인딩은 단위 테스트에서 안 잡히고 실기동에서만 드러난다 — yml 키가 하나 어긋나면 서버가
 * 뜨다가 죽는다. 여기서 {@code application.yml}과 <b>같은 키</b>로 바인딩해 그 경로를 미리 밟는다.
 */
class CoursePropertiesBindingTest {

    private static Map<String, Object> validYaml() {
        Map<String, Object> map = new HashMap<>();
        map.put("course.weights.interest", "35");
        map.put("course.weights.access", "30");
        map.put("course.weights.dispersion", "20");
        map.put("course.weights.completeness", "15");
        map.put("course.constraints.places-per-day", "3.5");
        map.put("course.constraints.max-nights", "14");
        map.put("course.constraints.base-distance-km.EASY", "60");
        map.put("course.constraints.base-distance-km.NORMAL", "100");
        map.put("course.constraints.base-distance-km.LOCAL", "150");
        return map;
    }

    private static CourseProperties bind(Map<String, Object> yaml) {
        return new Binder(new MapConfigurationPropertySource(yaml))
                .bind("course", CourseProperties.class)
                .orElseThrow(() -> new IllegalStateException("바인딩 결과가 없습니다"));
    }

    @Test
    void 운영_설정_키가_그대로_바인딩된다() {
        CourseProperties properties = bind(validYaml());

        assertThat(properties.weights().interest()).isEqualTo(35);
        assertThat(properties.constraints().placesPerDay()).isEqualTo(3.5);
        assertThat(properties.constraints().maxNights()).isEqualTo(14);
        // 난이도 문자열 키가 enum으로 변환되는지 — 여기가 조용히 깨지기 쉬운 자리다.
        assertThat(properties.constraints().baseDistanceKm())
                .containsEntry(Difficulty.EASY, 60)
                .containsEntry(Difficulty.NORMAL, 100)
                .containsEntry(Difficulty.LOCAL, 150);
    }

    @Test
    void 가중치_합이_100이_아닌_설정은_바인딩_단계에서_실패한다() {
        Map<String, Object> broken = validYaml();
        broken.put("course.weights.completeness", "10");

        assertThatThrownBy(() -> bind(broken))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessageContaining("합계는 100");
    }

    @Test
    void 난이도가_빠진_설정은_바인딩_단계에서_실패한다() {
        Map<String, Object> broken = validYaml();
        broken.remove("course.constraints.base-distance-km.LOCAL");

        assertThatThrownBy(() -> bind(broken))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessageContaining("LOCAL");
    }
}
