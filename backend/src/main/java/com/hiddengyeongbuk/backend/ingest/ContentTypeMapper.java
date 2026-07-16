package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * KorService2 contentTypeId -> category 매핑. 외부 API 연동 설계서 2.4 기준.
 * 쇼핑(38)·여행코스(25)는 MVP category(관광지/음식점/숙박/축제)에 해당 없어 수집 제외.
 */
@Component
public class ContentTypeMapper {

    private static final Map<String, PlaceCategory> KOR_CONTENT_TYPE_TO_CATEGORY = Map.of(
            "12", PlaceCategory.관광지,
            "14", PlaceCategory.관광지,
            "28", PlaceCategory.관광지,
            "15", PlaceCategory.축제,
            "32", PlaceCategory.숙박,
            "39", PlaceCategory.음식점);

    public Optional<PlaceCategory> categoryOfKor(String contentTypeId) {
        return Optional.ofNullable(KOR_CONTENT_TYPE_TO_CATEGORY.get(contentTypeId));
    }
}
