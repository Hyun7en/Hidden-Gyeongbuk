package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLogRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 결과를 {@code data_sync_log}에 남긴다(NFR-10).
 *
 * <p>{@code REQUIRES_NEW}로 별도 트랜잭션을 연다 — 호출부가 실패해 롤백되더라도 <b>실패했다는
 * 기록만은 남아야</b> 하기 때문이다. 같은 트랜잭션에 얹으면 실패 시 로그까지 함께 사라져
 * 연속 실패 감지가 무력해진다.
 */
@Component
public class SyncLogger {

    /** data_sync_log.message는 VARCHAR(500) — 예외 메시지에 응답 원문이 섞이면 넘친다. */
    private static final int MAX_MESSAGE = 500;

    private final DataSyncLogRepository dataSyncLogRepository;

    public SyncLogger(DataSyncLogRepository dataSyncLogRepository) {
        this.dataSyncLogRepository = dataSyncLogRepository;
    }

    /**
     * @param breakdown {@code recordCount}의 내역(예: {@code upserted=523, skipped=7}). <b>정상
     *     실행에도 반드시 남긴다.</b> recordCount는 합계 하나뿐이라, 이것 없이는 "530을 훑었는데
     *     staging은 523"이 왜 생겼는지 사후에 되짚을 방법이 없다 — 실제로 겪었다.
     * @param failMessage 진짜 실패만. 정상 판정 결과(quarantine 등)를 여기 넣으면 안 된다 —
     *     상태가 PARTIAL로 바뀌어 "정상인데 계속 PARTIAL"이 되고, 연속 실패 감지가 무뎌진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            SyncPhase phase, String lang, String target, int recordCount, String breakdown, String failMessage) {
        LocalDateTime now = LocalDateTime.now();
        dataSyncLogRepository.save(DataSyncLog.builder()
                .phase(phase)
                .lang(lang)
                .target(target)
                .startedAt(now)
                .finishedAt(now)
                .status(failMessage == null ? SyncStatus.SUCCESS : SyncStatus.PARTIAL)
                .recordCount(recordCount)
                .message(buildMessage(breakdown, failMessage))
                .build());
    }

    /** 내역을 앞에 둔다 — 잘려 나갈 쪽은 길이를 예측할 수 없는 예외 메시지여야 한다. */
    private static String buildMessage(String breakdown, String failMessage) {
        if (failMessage == null) {
            return truncate(breakdown);
        }
        if (breakdown == null) {
            return truncate(failMessage);
        }
        return truncate(breakdown + " | 오류: " + failMessage);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_MESSAGE ? value.substring(0, MAX_MESSAGE) : value;
    }
}
