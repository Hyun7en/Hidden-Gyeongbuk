package com.hiddengyeongbuk.backend.domain.region;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionTranslationRepository extends JpaRepository<RegionTranslation, Long> {

    /** 지역 목록 응답에서 특정 언어 표시명을 한 번에 조회(N+1 방지). */
    List<RegionTranslation> findByLang(String lang);

    List<RegionTranslation> findByLangAndRegionIdIn(String lang, Collection<Long> regionIds);

    Optional<RegionTranslation> findByRegionIdAndLang(Long regionId, String lang);
}
