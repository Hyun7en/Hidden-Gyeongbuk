package com.hiddengyeongbuk.backend.domain.place;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceTranslationRepository extends JpaRepository<PlaceTranslation, Long> {

    /** 목록·코스 응답에서 여러 장소의 특정 언어 번역을 한 번에 조회(N+1 방지). */
    List<PlaceTranslation> findByLangAndPlaceIdIn(String lang, Collection<Long> placeIds);

    Optional<PlaceTranslation> findByPlaceIdAndLang(Long placeId, String lang);

    boolean existsByPlaceIdAndLang(Long placeId, String lang);
}
