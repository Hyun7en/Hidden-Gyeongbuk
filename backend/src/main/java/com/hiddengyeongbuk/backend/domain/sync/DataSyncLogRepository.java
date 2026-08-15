package com.hiddengyeongbuk.backend.domain.sync;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSyncLogRepository extends JpaRepository<DataSyncLog, Long> {

    /**
     * 최근 실행부터. 운영 상태 조회가 (phase, lang)별로 묶어 최근 실행과 연속 실패 횟수를 센다.
     *
     * <p>전량을 읽는 것은 이 테이블이 실행당 한 행씩만 쌓이기 때문이다 — 단계 4개 × 언어 몇 개를
     * 하루 한 번이면 연 단위로도 수천 행이다. 이 규모를 넘기 시작하면 (phase, lang)별 상위 N개만
     * 읽도록 바꿀 것.
     */
    List<DataSyncLog> findAllByOrderByStartedAtDesc();
}
