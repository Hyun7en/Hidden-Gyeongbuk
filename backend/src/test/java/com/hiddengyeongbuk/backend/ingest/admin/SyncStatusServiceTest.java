package com.hiddengyeongbuk.backend.ingest.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLogRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SyncStatusServiceTest {

    private final DataSyncLogRepository syncLogRepository = Mockito.mock(DataSyncLogRepository.class);
    private final StagingPlaceRepository stagingPlaceRepository = Mockito.mock(StagingPlaceRepository.class);
    private final PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
    private final PlaceTranslationRepository placeTranslationRepository =
            Mockito.mock(PlaceTranslationRepository.class);

    private final SyncStatusService service = new SyncStatusService(
            syncLogRepository, stagingPlaceRepository, placeRepository, placeTranslationRepository);

    @BeforeEach
    void stubCounts() {
        given(stagingPlaceRepository.countGroupedByResolutionStatus())
                .willReturn(List.of(
                        new Object[] {ResolutionStatus.MATCHED, 776L},
                        new Object[] {ResolutionStatus.QUARANTINED, 44L}));
        given(stagingPlaceRepository.countGroupedByLang())
                .willReturn(List.of(new Object[] {"ko", 523L}, new Object[] {"en", 297L}));
        given(placeRepository.count()).willReturn(525L);
        given(placeTranslationRepository.countGroupedByLang())
                .willReturn(List.of(new Object[] {"ko", 525L}, new Object[] {"en", 251L}));
    }

    @Test
    void 마지막_성공_이후_연속_실패_횟수를_센다() {
        given(syncLogRepository.findAllByOrderByStartedAtDesc())
                .willReturn(List.of(
                        log(SyncPhase.COLLECT, "en", SyncStatus.FAIL, 3),
                        log(SyncPhase.COLLECT, "en", SyncStatus.FAIL, 2),
                        log(SyncPhase.COLLECT, "en", SyncStatus.SUCCESS, 1)));

        SyncStatusResponse.PhaseStatus en = service.status().phases().get(0);

        assertThat(en.consecutiveFailures()).isEqualTo(2);
        assertThat(en.lastStatus()).isEqualTo(SyncStatus.FAIL);
    }

    @Test
    void 부분성공은_실패로_세지_않는다() {
        // PARTIAL을 실패로 세면 "일부 결측이 정상"인 수집에서 카운터가 영영 0으로 안 돌아온다.
        given(syncLogRepository.findAllByOrderByStartedAtDesc())
                .willReturn(List.of(
                        log(SyncPhase.COLLECT, "ko", SyncStatus.PARTIAL, 2),
                        log(SyncPhase.COLLECT, "ko", SyncStatus.FAIL, 1)));

        assertThat(service.status().phases().get(0).consecutiveFailures()).isZero();
    }

    @Test
    void 언어별로_따로_집계한다() {
        // 한 언어가 계속 실패해도 다른 언어가 성공하면 전체는 멀쩡해 보인다 — 그걸 갈라 보는 게 요점이다.
        given(syncLogRepository.findAllByOrderByStartedAtDesc())
                .willReturn(List.of(
                        log(SyncPhase.COLLECT, "ko", SyncStatus.SUCCESS, 2),
                        log(SyncPhase.COLLECT, "en", SyncStatus.FAIL, 2),
                        log(SyncPhase.COLLECT, "en", SyncStatus.FAIL, 1)));

        List<SyncStatusResponse.PhaseStatus> phases = service.status().phases();

        assertThat(phases).hasSize(2);
        // 문제부터 위로 온다.
        assertThat(phases.get(0).lang()).isEqualTo("en");
        assertThat(phases.get(0).consecutiveFailures()).isEqualTo(2);
        assertThat(phases.get(1).lang()).isEqualTo("ko");
        assertThat(phases.get(1).consecutiveFailures()).isZero();
    }

    @Test
    void 언어_무관_단계는_lang이_null이어도_집계된다() {
        given(syncLogRepository.findAllByOrderByStartedAtDesc())
                .willReturn(List.of(log(SyncPhase.MERGE, null, SyncStatus.SUCCESS, 1)));

        SyncStatusResponse.PhaseStatus merge = service.status().phases().get(0);

        assertThat(merge.phase()).isEqualTo(SyncPhase.MERGE);
        assertThat(merge.lang()).isNull();
    }

    @Test
    void 데이터_현황은_상태별_언어별로_쪼개_내려준다() {
        given(syncLogRepository.findAllByOrderByStartedAtDesc()).willReturn(List.of());

        SyncStatusResponse.DataCounts data = service.status().data();

        assertThat(data.stagingByStatus()).containsEntry("QUARANTINED", 44L);
        assertThat(data.places()).isEqualTo(525L);
        // place 525건 중 en 번역이 251건 — 차이 274건이 번역 백필 잔량이다.
        assertThat(data.translationsByLang()).containsEntry("en", 251L);
    }

    private static DataSyncLog log(SyncPhase phase, String lang, SyncStatus status, int daysAgo) {
        return DataSyncLog.builder()
                .phase(phase)
                .lang(lang)
                .status(status)
                .startedAt(LocalDateTime.now().minusDays(daysAgo))
                .finishedAt(LocalDateTime.now().minusDays(daysAgo))
                .recordCount(10)
                .build();
    }
}
