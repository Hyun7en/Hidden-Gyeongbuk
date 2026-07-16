package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlaceCollectionServiceCoordinateTest {

    @Test
    void 정상_좌표는_그대로_파싱한다() {
        assertThat(PlaceCollectionService.parseCoordinate("36.5684249")).isEqualByComparingTo(new BigDecimal("36.5684249"));
    }

    @Test
    void 빈값이나_공백은_null이다() {
        assertThat(PlaceCollectionService.parseCoordinate(null)).isNull();
        assertThat(PlaceCollectionService.parseCoordinate("")).isNull();
        assertThat(PlaceCollectionService.parseCoordinate("   ")).isNull();
    }

    @Test
    void 좌표_미확보를_뜻하는_0은_null로_취급한다() {
        assertThat(PlaceCollectionService.parseCoordinate("0")).isNull();
        assertThat(PlaceCollectionService.parseCoordinate("0.0")).isNull();
    }
}
