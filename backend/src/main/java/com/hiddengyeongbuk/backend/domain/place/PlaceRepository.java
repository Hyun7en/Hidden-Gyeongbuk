package com.hiddengyeongbuk.backend.domain.place;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long>, JpaSpecificationExecutor<Place> {

    Optional<Place> findByContentId(String contentId);

    @Query("SELECT DISTINCT p FROM Place p LEFT JOIN FETCH p.tags WHERE p.region.id IN :regionIds")
    List<Place> findByRegionIdInWithTags(@Param("regionIds") List<Long> regionIds);
}
