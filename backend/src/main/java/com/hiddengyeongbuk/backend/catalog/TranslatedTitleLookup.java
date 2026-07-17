package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummary;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummaryRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 우리가 번역해둔 영문 제목(place_summary.title_en)을 placeId -> 제목 맵으로 일괄 조회한다.
 * {@link LangFallback#title(Place, String, String)}의 2단계 폴백 재료 — TourAPI 영문이 없는
 * 장소의 목록·코스 응답 제목을 번역본으로 채우기 위함이다. 장소별 findById를 반복하면
 * 목록 한 페이지에 N+1 쿼리가 되므로 반드시 이걸로 묶어서 조회할 것.
 * catalog 하위 패키지(course)에서도 써야 해서 public이다.
 */
@Component
public class TranslatedTitleLookup {

    private final PlaceSummaryRepository placeSummaryRepository;

    TranslatedTitleLookup(PlaceSummaryRepository placeSummaryRepository) {
        this.placeSummaryRepository = placeSummaryRepository;
    }

    /** lang=en이 아니거나 폴백이 필요한 장소가 없으면 빈 맵을 돌려준다(추가 쿼리 없음). */
    public Map<Long, String> forPlaces(Collection<Place> places, String lang) {
        if (!LangFallback.EN.equalsIgnoreCase(lang)) {
            return Map.of();
        }
        List<Long> ids = places.stream()
                .filter(p -> p.getTitleEn() == null || p.getTitleEn().isBlank())
                .map(Place::getId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return placeSummaryRepository.findAllById(ids).stream()
                .filter(s -> s.getTitleEn() != null && !s.getTitleEn().isBlank())
                .collect(Collectors.toMap(PlaceSummary::getPlaceId, PlaceSummary::getTitleEn));
    }
}
