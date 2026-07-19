package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import org.junit.jupiter.api.Test;

class ContentTypeMapperTest {

    private final ContentTypeMapper mapper = new ContentTypeMapper();

    @Test
    void 영문_contentTypeId를_카테고리로_매핑한다() {
        assertThat(mapper.categoryOfEng("76")).contains(PlaceCategory.관광지);
        assertThat(mapper.categoryOfEng("78")).contains(PlaceCategory.관광지);
        assertThat(mapper.categoryOfEng("85")).contains(PlaceCategory.축제);
        assertThat(mapper.categoryOfEng("80")).contains(PlaceCategory.숙박);
        assertThat(mapper.categoryOfEng("79")).contains(PlaceCategory.쇼핑);
        assertThat(mapper.categoryOfEng("82")).contains(PlaceCategory.음식점);
    }

    @Test
    void 영문_여행코스75와_미지원_타입은_빈값이다() {
        assertThat(mapper.categoryOfEng("75")).isEmpty();
        assertThat(mapper.categoryOfEng("12")).isEmpty(); // 국문 코드는 영문 매핑에 없다
        assertThat(mapper.categoryOfEng(null)).isEmpty();
    }

    @Test
    void 국문_매핑은_그대로_동작한다() {
        assertThat(mapper.categoryOfKor("12")).contains(PlaceCategory.관광지);
        assertThat(mapper.categoryOfKor("39")).contains(PlaceCategory.음식점);
        assertThat(mapper.categoryOfKor("25")).isEmpty(); // 여행코스 제외
    }
}
