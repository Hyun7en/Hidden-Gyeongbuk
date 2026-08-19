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

    /**
     * 판정이 MATCHED가 아닌데도 place를 가리키고 있는 행. <b>ADR-016 위반 상태</b>다 —
     * 보류 건은 place를 만들지 않는 것이 원칙인데, 이전 판정에서 붙은 링크가 남아 있는 것이다.
     *
     * <p>이 링크가 살아 있으면 place를 지울 수도 없다. {@code resolved_place_id} FK가 RESTRICT라
     * (V1 스키마 주석 — SET NULL로 두면 MATCHED인데 링크가 끊긴 모순 상태가 생긴다) 링크를
     * 먼저 끊어야 정리가 가능하다.
     */
    @Query("select s from StagingPlace s where s.resolvedPlaceId is not null and s.resolutionStatus <> :matched")
    List<StagingPlace> findLinkedButNotMatched(ResolutionStatus matched);

    /** MATCHED 행이 실제로 가리키는 place id 전체. 이 집합 밖의 place는 살아 있을 근거가 없다. */
    @Query("select distinct s.resolvedPlaceId from StagingPlace s "
            + "where s.resolvedPlaceId is not null and s.resolutionStatus = :matched")
    List<Long> findPlaceIdsReferencedByMatched(ResolutionStatus matched);

    /**
     * MATCHED가 뒷받침하는 (place, 언어) 조합 전체.
     *
     * <p>place 단위가 아니라 <b>언어 단위</b>로 보는 것이 요점이다. 한 place의 국문은 살아 있고
     * 영문만 소스에서 내려간 경우, place는 남아야 하지만 그 영문 행은 근거를 잃는다 —
     * place 단위 정리로는 잡히지 않는 자리다.
     */
    @Query("select distinct s.resolvedPlaceId, s.lang from StagingPlace s "
            + "where s.resolvedPlaceId is not null and s.resolutionStatus = :matched")
    List<Object[]> findPlaceLangPairsReferencedByMatched(ResolutionStatus matched);
}
