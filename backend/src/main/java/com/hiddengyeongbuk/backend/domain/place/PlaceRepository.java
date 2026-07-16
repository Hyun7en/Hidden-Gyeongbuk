package com.hiddengyeongbuk.backend.domain.place;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByContentId(String contentId);
}
