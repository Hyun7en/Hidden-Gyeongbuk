package com.hiddengyeongbuk.backend.domain.place;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {

    List<PlaceImage> findByPlaceIdOrderBySortOrder(Long placeId);

    /** 여러 장소의 이미지를 한 번에 — 정리 작업이 장소별 조회를 돌지 않게 한다. */
    List<PlaceImage> findByPlaceIdIn(java.util.Collection<Long> placeIds);
}
