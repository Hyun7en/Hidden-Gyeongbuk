package com.hiddengyeongbuk.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

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
}
