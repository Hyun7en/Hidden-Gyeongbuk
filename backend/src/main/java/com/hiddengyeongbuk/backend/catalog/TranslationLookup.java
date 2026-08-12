package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 여러 장소의 {@code place_translation} 행을 한 번에 읽어 {@link PlaceTexts}로 묶는다.
 * 장소별 findBy를 반복하면 N+1이 되므로 목록·코스·상세는 반드시 이걸로 묶어 조회할 것.
 *
 * <p><b>요청 언어 행만 가져오지 않고 그 장소의 행을 전부 가져온다.</b> 이유가 둘이다.
 *
 * <ul>
 *   <li>폴백 체인의 3순위가 "존재하는 아무 언어"라, 요청 언어와 ko가 모두 없을 때 쓸 행이
 *       손에 있어야 한다(ADR-016 — 어느 한 언어에만 존재하는 장소).
 *   <li>{@code addrLocal}(현지 표기 주소)은 요청 언어와 무관하게 항상 국문이라, en 요청에도
 *       ko 행이 필요하다(API 명세서 §3.2).
 * </ul>
 *
 * <p>과다 조회처럼 보이지만 행 수는 (페이지 크기 × 활성 언어 수)로 유계이고, 무엇보다
 * <b>쿼리 수가 언어 수와 무관하게 1개</b>다. 언어별로 조건을 나누면 언어를 늘릴 때마다
 * 쿼리가 늘어 ADR-018의 "언어 추가에 코드 변경 없음"이 깨진다.
 *
 * <p>구 구현은 {@code lang=ko}일 때 빈 맵을 돌려줬다 — 국문이 place 컬럼에 있다는 전제였고,
 * 국문도 행으로 옮겨진 지금은 성립하지 않는다.
 */
@Component
public class TranslationLookup {

    private final PlaceTranslationRepository placeTranslationRepository;

    TranslationLookup(PlaceTranslationRepository placeTranslationRepository) {
        this.placeTranslationRepository = placeTranslationRepository;
    }

    public Map<Long, PlaceTexts> forPlaces(Collection<Place> places, String requestedLang) {
        List<Long> ids = places.stream().map(Place::getId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<PlaceTranslation> rows = placeTranslationRepository.findByPlaceIdIn(ids);
        return PlaceTexts.group(rows, requestedLang);
    }

    public PlaceTexts forPlace(Long placeId, String requestedLang) {
        return PlaceTexts.single(placeTranslationRepository.findByPlaceId(placeId), requestedLang);
    }
}
