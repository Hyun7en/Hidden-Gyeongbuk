package com.hiddengyeongbuk.backend.domain.place;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {

    List<PlaceImage> findByPlaceIdOrderBySortOrder(Long placeId);
}
