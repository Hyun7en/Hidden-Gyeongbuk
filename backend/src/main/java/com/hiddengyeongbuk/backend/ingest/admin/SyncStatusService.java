package com.hiddengyeongbuk.backend.ingest.admin;

import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLogRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 상태 집계(API 명세서 §5.2).
 *
 * <p>이 화면이 필요한 이유는 <b>부분 실패를 언어 단위로 격리하기 때문</b>이다(아키텍처 §5.1).
 * 한 언어 수집이 실패해도 성공한 언어로 Resolve·Merge가 진행되므로 배치는 계속 "돌아가는 것처럼"
 * 보인다. 어느 (단계, 언어)가 연속으로 실패하는지 따로 세지 않으면 장기 장애를 못 잡는다.
 */
@Service
@Transactional(readOnly = true)
public class SyncStatusService {

    private final DataSyncLogRepository syncLogRepository;
    private final StagingPlaceRepository stagingPlaceRepository;
    private final PlaceRepository placeRepository;
    private final PlaceTranslationRepository placeTranslationRepository;

    public SyncStatusService(
            DataSyncLogRepository syncLogRepository,
            StagingPlaceRepository stagingPlaceRepository,
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository) {
        this.syncLogRepository = syncLogRepository;
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
    }

    public SyncStatusResponse status() {
        return new SyncStatusResponse(phases(), dataCounts());
    }

    private List<SyncStatusResponse.PhaseStatus> phases() {
        // 최근 순으로 한 번 읽고 (phase, lang)별로 나눈다. 로그가 실행당 한 행이라 이 규모에서는
        // 그룹별 쿼리를 따로 날리는 것보다 단순하고 빠르다.
        Map<String, List<DataSyncLog>> grouped = new LinkedHashMap<>();
        for (DataSyncLog log : syncLogRepository.findAllByOrderByStartedAtDesc()) {
            grouped.computeIfAbsent(log.getPhase().name() + "|" + log.getLang(), k -> new ArrayList<>())
                    .add(log);
        }

        List<SyncStatusResponse.PhaseStatus> result = new ArrayList<>();
        for (List<DataSyncLog> runs : grouped.values()) {
            DataSyncLog latest = runs.get(0);
            result.add(new SyncStatusResponse.PhaseStatus(
                    latest.getPhase(),
                    latest.getLang(),
                    latest.getStatus(),
                    latest.getStartedAt(),
                    latest.getFinishedAt(),
                    latest.getRecordCount(),
                    latest.getMessage(),
                    consecutiveFailures(runs)));
        }
        // 문제부터 눈에 들어오게 — 연속 실패가 많은 순, 같으면 단계 순.
        result.sort(Comparator.comparingInt(SyncStatusResponse.PhaseStatus::consecutiveFailures)
                .reversed()
                .thenComparing(p -> p.phase().ordinal()));
        return result;
    }

    /**
     * 마지막 성공 이전까지 연속으로 실패한 횟수.
     *
     * <p>PARTIAL은 실패로 세지 않는다 — 일부 항목이 걸러졌을 뿐 단계 자체는 진행됐고, 이걸
     * 실패로 세면 "일부 결측이 정상"인 수집에서 카운터가 영영 0으로 안 돌아온다.
     */
    private static int consecutiveFailures(List<DataSyncLog> runsNewestFirst) {
        int count = 0;
        for (DataSyncLog run : runsNewestFirst) {
            if (run.getStatus() != SyncStatus.FAIL) {
                break;
            }
            count++;
        }
        return count;
    }

    private SyncStatusResponse.DataCounts dataCounts() {
        return new SyncStatusResponse.DataCounts(
                toCountMap(stagingPlaceRepository.countGroupedByResolutionStatus()),
                toCountMap(stagingPlaceRepository.countGroupedByLang()),
                placeRepository.count(),
                toCountMap(placeTranslationRepository.countGroupedByLang()));
    }

    /** group by 결과 {@code [key, count]}를 문자열 키 맵으로. 키가 enum이어도 이름으로 나간다. */
    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }
}
