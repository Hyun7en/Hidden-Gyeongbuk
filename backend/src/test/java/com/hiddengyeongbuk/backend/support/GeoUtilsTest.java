package com.hiddengyeongbuk.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void 같은_좌표는_거리가_0이다() {
        assertThat(GeoUtils.haversineKm(36.5684, 128.7294, 36.5684, 128.7294)).isEqualTo(0.0);
    }

    @Test
    void 안동과_서울시청_직선거리는_대략_190km_안팎이다() {
        double distance = GeoUtils.haversineKm(36.5684, 128.7294, 37.5665, 126.9780);
        assertThat(distance).isBetween(170.0, 210.0);
    }

    @Test
    void 정상_좌표는_그대로_파싱한다() {
        assertThat(GeoUtils.parseCoordinate("36.5684249")).isEqualByComparingTo(new BigDecimal("36.5684249"));
    }

    @Test
    void 빈값이나_공백은_null이다() {
        assertThat(GeoUtils.parseCoordinate(null)).isNull();
        assertThat(GeoUtils.parseCoordinate("")).isNull();
        assertThat(GeoUtils.parseCoordinate("   ")).isNull();
    }

    @Test
    void 좌표_미확보를_뜻하는_0은_null로_취급한다() {
        assertThat(GeoUtils.parseCoordinate("0")).isNull();
        assertThat(GeoUtils.parseCoordinate("0.0")).isNull();
    }

    @Test
    void 숫자가_아니면_예외_대신_null이다() {
        assertThat(GeoUtils.parseCoordinate("N/A")).isNull();
    }
}
