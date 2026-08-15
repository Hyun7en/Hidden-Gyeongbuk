package com.hiddengyeongbuk.backend.ingest.admin;

import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 수집 상태 요약(API 명세서 §5.2).
 *
 * @param phases 단계·언어별 최근 실행. 언어별 수집이 독립적이라(ADR-016) 한 언어가 계속 실패해도
 *     전체는 성공으로 보인다 — 그래서 합계가 아니라 (phase, lang) 쌍으로 쪼개 본다.
 * @param data 지금 DB에 뭐가 얼마나 있는지. 파이프라인이 "성공"인데 결과가 안 늘어나는 상황을
 *     잡으려면 실행 로그만으로는 부족하다.
 */
public record SyncStatusResponse(List<PhaseStatus> phases, DataCounts data) {

    /**
     * @param consecutiveFailures 마지막 성공 이후 연속 실패 횟수. <b>이 화면의 핵심 숫자다</b> —
     *     부분 실패를 언어 단위로 격리해 계속 진행하는 설계라, 실패가 누적되는 것을 여기서
     *     보지 못하면 장기 장애를 놓친다(아키텍처 §5.1).
     * @param lang RESOLVE·MERGE는 언어 무관이라 null이다.
     */
    public record PhaseStatus(
            SyncPhase phase,
            String lang,
            SyncStatus lastStatus,
            LocalDateTime lastStartedAt,
            LocalDateTime lastFinishedAt,
            Integer lastRecordCount,
            String lastMessage,
            int consecutiveFailures) {}

    /**
     * @param stagingByStatus staging 행의 판정 상태별 건수. QUARANTINED가 계속 늘면 매칭 규칙을
     *     손봐야 한다는 신호다 — 오매칭보다 미매칭을 택한 대가라 0이 목표는 아니지만, 비율이
     *     튀는 것은 봐야 한다.
     * @param translationsByLang 언어별 번역 행 수. {@code places}와의 차이가 곧 번역 백필 잔량이다.
     */
    public record DataCounts(
            Map<String, Long> stagingByStatus,
            Map<String, Long> stagingByLang,
            long places,
            Map<String, Long> translationsByLang) {}
}
