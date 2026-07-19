package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 여러 장소의 특정 언어 번역(place_translation)을 placeId -> 행 맵으로 일괄 조회한다.
 * 목록·코스·상세 응답에서 제목·설명·주소를 언어별로 채우는 재료 — 장소별 findBy를 반복하면
 * N+1이 되므로 반드시 이걸로 묶어서 조회할 것. catalog 하위 패키지(course)에서도 써서 public이다.
 */
@Component
public class TranslationLookup {

    private final PlaceTranslationRepository placeTranslationRepository;

    TranslationLookup(PlaceTranslationRepository placeTranslationRepository) {
        this.placeTranslationRepository = placeTranslationRepository;
    }

    /** lang=ko이거나 대상 장소가 없으면 빈 맵을 돌려준다(추가 쿼리 없음). */
    public Map<Long, PlaceTranslation> forPlaces(Collection<Place> places, String lang) {
        if (LangFallback.KO.equalsIgnoreCase(lang)) {
            return Map.of();
        }
        List<Long> ids = places.stream().map(Place::getId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return placeTranslationRepository.findByLangAndPlaceIdIn(lang, ids).stream()
                .collect(Collectors.toMap(PlaceTranslation::getPlaceId, Function.identity()));
    }
}
