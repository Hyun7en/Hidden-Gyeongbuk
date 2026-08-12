package com.hiddengyeongbuk.backend.domain.place;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long>, JpaSpecificationExecutor<Place> {

    @Query("SELECT DISTINCT p FROM Place p LEFT JOIN FETCH p.tags WHERE p.region.id IN :regionIds")
    List<Place> findByRegionIdInWithTags(@Param("regionIds") List<Long> regionIds);

    // findByContentId·findByHasEnFalse는 제거됐다. place에 자연키(content_id)가 없어졌고
    // (정체성은 Resolution 결과로만 결정된다), 언어 커버리지는 has_en 플래그가 아니라
    // place_translation 행 존재로 판단한다(ADR-017). 번역 백필 대상 조회는 Translator가
    // place_translation을 기준으로 수행한다.
}
