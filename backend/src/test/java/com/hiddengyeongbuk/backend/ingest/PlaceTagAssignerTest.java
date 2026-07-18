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
    void 매칭되는_키워드가_없으면_태그를_부여하지_않는다() {
        // 예전 기본값("자연")은 전시관·박물관까지 자연 코스에 섞이게 해 관심사 매칭을 오염시켰다.
        assertThat(assigner.assign(PlaceCategory.관광지, "이상한 이름의 전시관", "특별한 설명 없음")).isEmpty();
    }

    @Test
    void 동명이인_구분_괄호가_붙어도_사찰_접미사를_인식한다() {
        assertThat(assigner.assign(PlaceCategory.관광지, "개목사(안동)", null)).contains("사찰");
    }

    @Test
    void 전통시장_키워드가_있으면_쇼핑은_전통_태그를_부여한다() {
        assertThat(assigner.assign(PlaceCategory.쇼핑, "안동 구시장", "전통시장으로 먹거리가 풍부하다")).containsExactly("전통");
    }

    @Test
    void 시장_키워드가_없는_쇼핑은_태그가_없다() {
        assertThat(assigner.assign(PlaceCategory.쇼핑, "안동 특산품 판매점", "안동포와 간고등어를 판매한다")).isEmpty();
    }
}
