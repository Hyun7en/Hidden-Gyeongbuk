package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import org.junit.jupiter.api.Test;

class PlaceTagAssignerTest {

    private final PlaceTagAssigner assigner = new PlaceTagAssigner();

    @Test
    void 음식점은_음식_태그만_부여한다() {
        assertThat(assigner.assign(PlaceCategory.음식점, "안동찜닭 맛집", "닭 요리 전문점")).containsExactly("음식");
    }

    @Test
    void 숙박은_휴식_태그만_부여한다() {
        assertThat(assigner.assign(PlaceCategory.숙박, "안동 게스트하우스", null)).containsExactly("휴식");
    }

    @Test
    void 축제는_축제_태그만_부여한다() {
        assertThat(assigner.assign(PlaceCategory.축제, "안동 국제탈춤페스티벌", null)).containsExactly("축제");
    }

    @Test
    void 사로_끝나는_관광지_제목은_사찰_태그를_포함한다() {
        assertThat(assigner.assign(PlaceCategory.관광지, "봉정사", "천등산에 위치한 고찰")).contains("사찰", "자연");
    }

    @Test
    void 한옥_키워드가_있으면_전통_태그를_포함한다() {
        assertThat(assigner.assign(PlaceCategory.관광지, "안동 하회마을", "전통 한옥과 서원이 잘 보존된 마을")).contains("전통");
    }

    @Test
    void 산_계곡_키워드가_있으면_자연_태그를_포함한다() {
        assertThat(assigner.assign(PlaceCategory.관광지, "주왕산 국립공원", "기암괴석과 계곡이 어우러진 산")).contains("자연");
    }

    @Test
    void 매칭되는_키워드가_없으면_자연을_기본값으로_부여한다() {
        assertThat(assigner.assign(PlaceCategory.관광지, "이상한 이름의 전시관", "특별한 설명 없음")).containsExactly("자연");
    }
}
