package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * KorService2 contentTypeId -> category 매핑. 외부 API 연동 설계서 2.4 기준.
 * 여행코스(25)는 TourAPI가 미리 짠 다중 스팟 루트라 place(장소 하나) 모델과 안 맞고,
 * 우리 서비스는 코스를 자체 계산하므로(ADR-003/009) 수집 제외.
 */
@Component
public class ContentTypeMapper {

    private static final Map<String, PlaceCategory> KOR_CONTENT_TYPE_TO_CATEGORY = Map.of(
            "12", PlaceCategory.관광지,
            "14", PlaceCategory.관광지,
            "28", PlaceCategory.관광지,
            "15", PlaceCategory.축제,
            "32", PlaceCategory.숙박,
            "38", PlaceCategory.쇼핑,
            "39", PlaceCategory.음식점);

    // EngService2 contentTypeId는 국문과 값이 다르다(76=관광지,78=문화시설,85=축제공연행사,
    // 80=숙박,79=쇼핑,82=음식점). 75=여행코스는 국문 25와 마찬가지로 제외 대상이라 매핑에서 뺀다.
    private static final Map<String, PlaceCategory> ENG_CONTENT_TYPE_TO_CATEGORY = Map.of(
            "76", PlaceCategory.관광지,
            "78", PlaceCategory.관광지,
            "85", PlaceCategory.축제,
            "80", PlaceCategory.숙박,
            "79", PlaceCategory.쇼핑,
            "82", PlaceCategory.음식점);

    public Optional<PlaceCategory> categoryOfKor(String contentTypeId) {
        return lookup(KOR_CONTENT_TYPE_TO_CATEGORY, contentTypeId);
    }

    public Optional<PlaceCategory> categoryOfEng(String contentTypeId) {
        return lookup(ENG_CONTENT_TYPE_TO_CATEGORY, contentTypeId);
    }

    // Map.of()는 null 키 조회 시 NPE를 던지므로(TourAPI 응답에 contentTypeId가 없을 수 있다) null을 먼저 거른다.
    private static Optional<PlaceCategory> lookup(Map<String, PlaceCategory> map, String contentTypeId) {
        return contentTypeId == null ? Optional.empty() : Optional.ofNullable(map.get(contentTypeId));
    }
}
