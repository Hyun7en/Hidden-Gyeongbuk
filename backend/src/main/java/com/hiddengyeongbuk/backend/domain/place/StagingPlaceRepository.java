package com.hiddengyeongbuk.backend.domain.place;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StagingPlaceRepository extends JpaRepository<StagingPlace, Long> {

    /** 자연키 조회 — Collector가 멱등 upsert할 때 기존 행을 찾는다. */
    Optional<StagingPlace> findByLangAndSourceContentId(String lang, String sourceContentId);

    /** Resolver 대상 조회. 언어별로 나눠 처리해 한 언어의 실패가 전체를 막지 않게 한다. */
    List<StagingPlace> findByResolutionStatusAndLang(ResolutionStatus status, String lang);

    List<StagingPlace> findByResolutionStatus(ResolutionStatus status);

    /** QUARANTINED 누적 건수 — 데이터 품질 지표로 관측한다(NFR-10). */
    long countByResolutionStatus(ResolutionStatus status);

    /**
     * 판정 상태별 건수 — 운영 상태 조회(GET /internal/sync/status)가 쓴다.
     *
     * <p>상태마다 count 쿼리를 따로 날리면 상태가 늘 때 쿼리도 는다. 한 번에 묶어 받는다.
     */
    @Query("select s.resolutionStatus, count(s) from StagingPlace s group by s.resolutionStatus")
    List<Object[]> countGroupedByResolutionStatus();

    @Query("select s.lang, count(s) from StagingPlace s group by s.lang")
    List<Object[]> countGroupedByLang();
}
