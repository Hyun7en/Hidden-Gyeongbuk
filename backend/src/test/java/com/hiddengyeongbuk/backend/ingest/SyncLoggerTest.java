package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.hiddengyeongbuk.backend.domain.sync.DataSyncLog;
import com.hiddengyeongbuk.backend.domain.sync.DataSyncLogRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

class SyncLoggerTest {

    private final DataSyncLogRepository repository = Mockito.mock(DataSyncLogRepository.class);
    private final SyncLogger syncLogger = new SyncLogger(repository);

    private DataSyncLog record(int recordCount, String breakdown, String failMessage) {
        BDDMockito.given(repository.save(any(DataSyncLog.class))).willAnswer(i -> i.getArgument(0));
        syncLogger.record(SyncPhase.COLLECT, "ko", "전체 지역", recordCount, breakdown, failMessage);
        ArgumentCaptor<DataSyncLog> captor = ArgumentCaptor.forClass(DataSyncLog.class);
        BDDMockito.then(repository).should().save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 정상_실행에도_내역을_남긴다() {
        // recordCount는 합계 하나뿐이라, 이게 없으면 "530을 훑었는데 staging은 523"의 원인을
        // 사후에 되짚을 수 없다.
        DataSyncLog saved = record(530, "upserted=523, unchanged=0, skipped=7, failed=0", null);

        assertThat(saved.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(saved.getRecordCount()).isEqualTo(530);
        assertThat(saved.getMessage()).isEqualTo("upserted=523, unchanged=0, skipped=7, failed=0");
    }

    @Test
    void 실패가_있으면_내역과_오류를_함께_남기고_PARTIAL로_기록한다() {
        DataSyncLog saved = record(10, "upserted=8, unchanged=0, skipped=0, failed=2", "timeout");

        assertThat(saved.getStatus()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(saved.getMessage()).isEqualTo("upserted=8, unchanged=0, skipped=0, failed=2 | 오류: timeout");
    }

    @Test
    void 잘릴_때_내역이_아니라_예외_메시지가_잘린다() {
        // 예외 메시지에 응답 원문이 섞이면 길이를 예측할 수 없다. 구조화된 내역이 살아남아야 한다.
        String breakdown = "upserted=1, unchanged=0, skipped=0, failed=1";
        DataSyncLog saved = record(2, breakdown, "x".repeat(1000));

        assertThat(saved.getMessage()).hasSize(500).startsWith(breakdown);
    }

    @Test
    void 내역도_오류도_없으면_메시지는_비운다() {
        assertThat(record(0, null, null).getMessage()).isNull();
    }
}
