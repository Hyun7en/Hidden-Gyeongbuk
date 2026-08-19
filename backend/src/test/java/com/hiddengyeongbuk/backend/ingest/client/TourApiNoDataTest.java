package com.hiddengyeongbuk.backend.ingest.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * "데이터가 없다"를 실패와 구별하는 판정.
 *
 * <p>사진(detailImage2)·소개정보(detailIntro2)는 <b>0건이 정상</b>인 오퍼레이션이다. 그런데
 * 0건이 NODATA 코드로 오면 {@code requireSuccess}가 예외로 올리고, 호출부는 "못 받았다"로 보고
 * <b>"물어봤다" 표시를 남기지 않는다.</b> 그러면 매 수집마다 영원히 다시 묻게 되고, 1건당 1회짜리
 * 호출이라 그날 예산을 그것만으로 태운다 — overview에서 겪은 함정이 예외 경로로 재현되는 것이다.
 *
 * <p>0건이 어느 형태로 오는지는 <b>실측하지 않았다.</b> 그래서 양쪽(빈 items / NODATA 코드)을
 * 같은 결과로 보낸다. 진짜 실패(한도 초과·키 오류·타임아웃)는 그대로 예외여야 한다.
 */
class TourApiNoDataTest {

    @Test
    void 결과없음_코드는_NODATA로_본다() {
        assertThat(TourApiClient.isNoData(new TourApiException("03", "NODATA_ERROR", ""))).isTrue();
    }

    @Test
    void 코드가_달라도_메시지에_NODATA가_있으면_인정한다() {
        // 서비스마다 코드가 흔들린 전례가 있어(v2 전환) 메시지도 함께 본다.
        assertThat(TourApiClient.isNoData(new TourApiException("0003", "NODATA_ERROR", ""))).isTrue();
        assertThat(TourApiClient.isNoData(new TourApiException("99", "nodata", ""))).isTrue();
    }

    @Test
    void 진짜_실패는_NODATA가_아니다() {
        // 22는 그날 예산 소진 — 빈 결과로 삼키면 "사진이 없다"로 오해해 표시를 남기고,
        // 그 장소의 사진을 영영 못 받는다. 반대 방향 손실이라 반드시 예외로 올라가야 한다.
        assertThat(TourApiClient.isNoData(new TourApiException("22", "LIMITED_NUMBER_OF_SERVICE_REQUESTS", "")))
                .isFalse();
        assertThat(TourApiClient.isNoData(new TourApiException("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR", "")))
                .isFalse();
        assertThat(TourApiClient.isNoData(new TourApiException("05", "TIMEOUT", ""))).isFalse();
    }
}
