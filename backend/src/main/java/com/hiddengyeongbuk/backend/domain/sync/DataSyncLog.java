package com.hiddengyeongbuk.backend.domain.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 수집 배치 관측 로그(NFR-10, ERD §3.11).
 *
 * <p><b>phase·lang별 기록이 부분 실패 처리와 연속 실패 감지의 근거다</b>(아키텍처 §5.1).
 * 언어별 수집은 서로 독립적이라 일부 언어가 실패해도 성공한 언어로 Resolve·Merge를 진행하는데,
 * 이때 "실패해도 진행"이 습관이 되면 장기 장애를 놓친다. 어느 단계의 어느 언어가 연속으로
 * 실패하는지 남겨야 그걸 잡을 수 있다.
 */
@Entity
@Table(
        name = "data_sync_log",
        indexes =
                @Index(
                        name = "idx_data_sync_log_phase_lang_started",
                        columnList = "phase, lang, started_at"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DataSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sync_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 20, nullable = false)
    private SyncPhase phase;

    /** 대상 언어(COLLECT·TRANSLATE 단계). RESOLVE·MERGE는 언어 무관이라 null이다. */
    @Column(name = "lang", length = 10)
    private String lang;

    /** 서비스명·지역 등 세부 대상. 단계에 따라 없을 수 있어 결측을 허용한다. */
    @Column(length = 50)
    private String target;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SyncStatus status;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(length = 500)
    private String message;
}
