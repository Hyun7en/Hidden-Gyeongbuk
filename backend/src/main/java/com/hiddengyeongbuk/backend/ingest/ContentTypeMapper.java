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

    public Optional<PlaceCategory> categoryOfKor(String contentTypeId) {
        return Optional.ofNullable(KOR_CONTENT_TYPE_TO_CATEGORY.get(contentTypeId));
    }
}
