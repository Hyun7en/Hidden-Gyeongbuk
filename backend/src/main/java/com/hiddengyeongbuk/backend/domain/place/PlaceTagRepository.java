package com.hiddengyeongbuk.backend.domain.place;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceTagRepository extends JpaRepository<PlaceTag, Long> {

    void deleteByPlaceId(Long placeId);
}
