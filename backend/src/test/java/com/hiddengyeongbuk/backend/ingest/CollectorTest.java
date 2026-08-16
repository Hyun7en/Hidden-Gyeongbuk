package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import com.hiddengyeongbuk.backend.ingest.client.TourApiClient;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailCommonItem;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

/**
 * 상세(detailCommon2) 실패가 <b>영구 손실이 되지 않는지</b>를 지키는 테스트.
 *
 * <p>목록 응답에 overview가 없어 장소마다 상세를 한 번 더 부르는데, upsert는 그 전에 별도
 * 트랜잭션으로 커밋된다. 워터마크를 되돌리지 않으면 다음 수집에서 unchanged로 판정돼 상세를
 * 영영 다시 부르지 않는다 — 호출 한도(오퍼레이션별 일 1,000건)에 걸리는 전량 수집에서 실제로
 * 밟게 되는 경로다.
 *
 * <p>외부 API도 DB도 쓰지 않는다. 여기서 실제 TourAPI를 부르면 검증하려는 그 한도를 테스트가
 * 갉아먹는다.
 */
class CollectorTest {

    private static final String LANG = "ko";
    private static final String SERVICE = "KorService2";
    private static final Long STAGING_ID = 42L;

    private final TourApiClient tourApiClient = Mockito.mock(TourApiClient.class);
    private final StagingWriter stagingWriter = Mockito.mock(StagingWriter.class);
    private final ContentTypeMapping contentTypes = Mockito.mock(ContentTypeMapping.class);
    private final LanguageRegistry languages = Mockito.mock(LanguageRegistry.class);
    private final RegionRepository regionRepository = Mockito.mock(RegionRepository.class);
    private final SyncLogger syncLogger = Mockito.mock(SyncLogger.class);

    private final Collector collector =
            new Collector(tourApiClient, stagingWriter, contentTypes, languages, regionRepository, syncLogger);

    private static AreaBasedSyncItem item() {
        return new AreaBasedSyncItem(
                "2465071", "32", "가경재", "경상북도 안동시 하회남촌길 69-5", "128.5175868107", "36.5376537450", null,
                null, "0", "20250922111143", "AC", "AC03", "AC030200");
    }

    /** 한 지역에 항목 하나만 있는 상황. 목록은 정상이고 상세만 흔들리게 둔다. */
    private Collector.Result collectOneItem() {
        Region region = Region.builder().ldongRegnCd("47").ldongSignguCd("170").build();
        BDDMockito.given(regionRepository.findById(1L)).willReturn(Optional.of(region));
        BDDMockito.given(languages.serviceNameOf(LANG)).willReturn(SERVICE);
        BDDMockito.given(contentTypes.isCollectable(eq(LANG), anyString())).willReturn(true);
        BDDMockito.given(tourApiClient.fetchAreaBasedSyncList(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .willReturn(List.of(item()));
        BDDMockito.given(stagingWriter.upsert(anyString(), anyString(), anyString(), any(AreaBasedSyncItem.class)))
                .willReturn(new StagingWriter.UpsertResult(STAGING_ID, true));

        return collector.collectRegion(LANG, 1L, 100);
    }

    @Test
    void 상세_조회가_실패하면_워터마크를_지워_다음_수집에서_재시도하게_한다() {
        // 호출 한도 초과(에러코드 22)가 이 경로로 온다.
        BDDMockito.given(tourApiClient.fetchDetailCommon(anyString(), anyString()))
                .willThrow(new IllegalStateException("22 요청 제한 초과"));

        Collector.Result result = collectOneItem();

        BDDMockito.then(stagingWriter).should().clearWatermark(STAGING_ID);
        assertThat(result.detailFailed()).isEqualTo(1);
        // 목록에서 훑은 항목 수는 그대로 1이어야 한다 — detailFailed는 upserted의 부분집합이라
        // total()에 더하면 산수가 어긋난다.
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void 상세를_받았는데_설명이_없으면_워터마크를_지우지_않는다() {
        // 원본에 overview가 없는 장소(실측: 음식점 92%)다. 이걸 실패로 보면 매 수집마다
        // 무한 재시도가 되어 호출 한도를 그날 다 쓴다.
        BDDMockito.given(tourApiClient.fetchDetailCommon(anyString(), anyString()))
                .willReturn(Optional.of(new DetailCommonItem(
                        "2465071", "32", "가경재", "경상북도 안동시 하회남촌길 69-5", null, null, null, null, null)));

        Collector.Result result = collectOneItem();

        BDDMockito.then(stagingWriter).should(Mockito.never()).clearWatermark(any());
        BDDMockito.then(stagingWriter).should(Mockito.never()).applyOverview(any(), any());
        assertThat(result.detailFailed()).isZero();
    }

    @Test
    void 상세를_정상적으로_받으면_overview를_채우고_워터마크를_유지한다() {
        BDDMockito.given(tourApiClient.fetchDetailCommon(anyString(), anyString()))
                .willReturn(Optional.of(new DetailCommonItem(
                        "2465071", "32", "가경재", "경상북도 안동시 하회남촌길 69-5", "하회마을 안에 있는 고택이다.",
                        null, null, null, null)));

        Collector.Result result = collectOneItem();

        BDDMockito.then(stagingWriter).should().applyOverview(STAGING_ID, "하회마을 안에 있는 고택이다.");
        BDDMockito.then(stagingWriter).should(Mockito.never()).clearWatermark(any());
        assertThat(result.detailFailed()).isZero();
        assertThat(result.upserted()).isEqualTo(1);
    }

    @Test
    void 안_바뀐_항목은_상세를_아예_부르지_않는다() {
        // 한도를 지키는 핵심 동작이라 회귀로 묶어 둔다.
        Region region = Region.builder().ldongRegnCd("47").ldongSignguCd("170").build();
        BDDMockito.given(regionRepository.findById(1L)).willReturn(Optional.of(region));
        BDDMockito.given(languages.serviceNameOf(LANG)).willReturn(SERVICE);
        BDDMockito.given(contentTypes.isCollectable(eq(LANG), anyString())).willReturn(true);
        BDDMockito.given(tourApiClient.fetchAreaBasedSyncList(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .willReturn(List.of(item()));
        BDDMockito.given(stagingWriter.upsert(anyString(), anyString(), anyString(), any(AreaBasedSyncItem.class)))
                .willReturn(new StagingWriter.UpsertResult(STAGING_ID, false));

        Collector.Result result = collector.collectRegion(LANG, 1L, 100);

        BDDMockito.then(tourApiClient).should(Mockito.never()).fetchDetailCommon(anyString(), anyString());
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.upserted()).isZero();
    }
}
