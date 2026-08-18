package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

/**
 * 소스가 내린 행(showflag=0)을 판정 대상에서 빼는 규칙.
 *
 * <p>동기화 목록은 소스에서 내려간 항목도 계속 내려보낸다. 지금까지 그 표시를 읽지 않아
 * 폐업한 식당·끝난 축제를 그대로 노출하고 있었다 — 2026-08-18 실측으로 15개 지역 국문
 * 2,204건 중 581건(26.4%)이 showflag=0이었다.
 *
 * <p>여기서 빼면 MATCHED가 되지 않고, MATCHED가 아니면 {@link StalePlaceSweeper}가 그 place를
 * 근거 없는 것으로 보고 지운다. 그래서 정리 로직을 따로 두지 않는다.
 */
class ResolverTest {

    private final StagingPlaceRepository repository = Mockito.mock(StagingPlaceRepository.class);
    private final SyncLogger syncLogger = Mockito.mock(SyncLogger.class);
    private final Resolver resolver = new Resolver(repository, new IdentityGrouper(), syncLogger);

    private static StagingPlace staging(String lang, String contentId, Integer showFlag, double lat, double lng) {
        StagingPlace staging = StagingPlace.builder()
                .lang(lang)
                .sourceContentId(contentId)
                .sourceTypeId(12)
                .title("안동 하회마을")
                .lat(BigDecimal.valueOf(lat))
                .lng(BigDecimal.valueOf(lng))
                .build();
        staging.setShowFlag(showFlag);
        return staging;
    }

    private List<StagingPlace> givenPending(StagingPlace... rows) {
        List<StagingPlace> pending = new ArrayList<>(List.of(rows));
        BDDMockito.given(repository.findByResolutionStatus(ResolutionStatus.PENDING)).willReturn(pending);
        return pending;
    }

    @Test
    void 미표출_행은_EXCLUDED가_되고_판정에서_빠진다() {
        StagingPlace hidden = staging("ko", "1", 0, 36.5, 128.6);
        givenPending(hidden);

        Resolver.Result result = resolver.resolve();

        assertThat(hidden.getResolutionStatus()).isEqualTo(ResolutionStatus.EXCLUDED);
        assertThat(result.excluded()).isEqualTo(1);
        assertThat(result.matched()).isZero();
        assertThat(result.groups()).isZero();
    }

    @Test
    void showflag가_null이면_모름이라_그대로_판정한다() {
        // 이 컬럼이 생기기 전에 수집한 행이 null이다. 미표출로 읽으면 확인도 안 한 장소를 지운다.
        StagingPlace unknown = staging("ko", "1", null, 36.5, 128.6);
        givenPending(unknown);

        Resolver.Result result = resolver.resolve();

        assertThat(unknown.getResolutionStatus()).isEqualTo(ResolutionStatus.MATCHED);
        assertThat(result.excluded()).isZero();
    }

    @Test
    void 표출_행은_그대로_판정한다() {
        StagingPlace shown = staging("ko", "1", 1, 36.5, 128.6);
        givenPending(shown);

        Resolver.Result result = resolver.resolve();

        assertThat(shown.getResolutionStatus()).isEqualTo(ResolutionStatus.MATCHED);
        assertThat(result.excluded()).isZero();
    }

    @Test
    void 국문이_내려가도_영문이_살아_있으면_그_장소는_남는다() {
        // "어느 한 언어에만 존재하는 장소도 버리지 않는다"(ADR-016). 언어별로 따로 판정한다.
        StagingPlace hiddenKo = staging("ko", "1", 0, 36.5, 128.6);
        StagingPlace shownEn = staging("en", "9", 1, 36.5, 128.6);
        givenPending(hiddenKo, shownEn);

        Resolver.Result result = resolver.resolve();

        assertThat(hiddenKo.getResolutionStatus()).isEqualTo(ResolutionStatus.EXCLUDED);
        assertThat(shownEn.getResolutionStatus()).isEqualTo(ResolutionStatus.MATCHED);
        assertThat(result.excluded()).isEqualTo(1);
        assertThat(result.matched()).isEqualTo(1);
    }
}
