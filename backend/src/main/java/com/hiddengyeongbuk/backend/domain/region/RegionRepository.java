package com.hiddengyeongbuk.backend.domain.region;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByLdongRegnCdAndLdongSignguCd(String ldongRegnCd, String ldongSignguCd);
}
