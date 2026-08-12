package com.hiddengyeongbuk.backend.domain.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByLdongRegnCdAndLdongSignguCd(String ldongRegnCd, String ldongSignguCd);

    List<Region> findByHubId(Long hubId);

    /** Collector 순회 대상이자 조회 노출 대상 — 지정 해제된 지역은 데이터를 남기되 감춘다(NFR-06). */
    List<Region> findByActiveTrue();
}
