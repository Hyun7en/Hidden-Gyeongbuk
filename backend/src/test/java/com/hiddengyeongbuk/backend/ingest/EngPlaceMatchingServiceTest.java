package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 매칭 후보 선택 규칙(정밀 우선, 애매하면 스킵) 검증 — DB·네트워크 없이 순수 로직만. */
class EngPlaceMatchingServiceTest {

    private static Place place(long id, PlaceCategory category, String lat, String lng) {
        return Place.builder()
                .id(id)
                .category(category)
                .titleKo("안동댐")
                .lat(lat == null ? null : new BigDecimal(lat))
                .lng(lng == null ? null : new BigDecimal(lng))
                .build();
    }

    @Test
    void 단일_후보는_좌표가_없어도_이름만으로_매칭한다() {
        Place p = place(1, PlaceCategory.관광지, null, null);
        Optional<Place> selected =
                EngPlaceMatchingService.selectCandidate(List.of(p), null, null, Optional.of(PlaceCategory.관광지));
        assertThat(selected).contains(p);
    }

    @Test
    void 단일_후보_좌표가_가드_이내면_매칭한다() {
        Place p = place(1, PlaceCategory.관광지, "36.5648", "128.7292");
        Optional<Place> selected = EngPlaceMatchingService.selectCandidate(
                List.of(p), new BigDecimal("36.5650"), new BigDecimal("128.7295"), Optional.of(PlaceCategory.관광지));
        assertThat(selected).contains(p);
    }

    @Test
    void 단일_후보라도_좌표가_가드를_넘으면_스킵한다() {
        // 같은 이름이라도 좌표가 수 km 떨어져 있으면 동명이소로 보고 매칭하지 않는다.
        Place p = place(1, PlaceCategory.관광지, "36.5648", "128.7292");
        Optional<Place> selected = EngPlaceMatchingService.selectCandidate(
                List.of(p), new BigDecimal("36.6100"), new BigDecimal("128.7292"), Optional.of(PlaceCategory.관광지));
        assertThat(selected).isEmpty();
    }

    @Test
    void 복수_후보는_카테고리로_가른다() {
        Place attraction = place(1, PlaceCategory.관광지, null, null);
        Place restaurant = place(2, PlaceCategory.음식점, null, null);
        Optional<Place> selected = EngPlaceMatchingService.selectCandidate(
                List.of(attraction, restaurant), null, null, Optional.of(PlaceCategory.음식점));
        assertThat(selected).contains(restaurant);
    }

    @Test
    void 카테고리로_못_가르면_좌표_최근접을_고른다() {
        Place near = place(1, PlaceCategory.관광지, "36.5648", "128.7292");
        Place far = place(2, PlaceCategory.관광지, "36.6000", "128.7500");
        Optional<Place> selected = EngPlaceMatchingService.selectCandidate(
                List.of(near, far), new BigDecimal("36.5649"), new BigDecimal("128.7293"), Optional.of(PlaceCategory.관광지));
        assertThat(selected).contains(near);
    }

    @Test
    void 복수_후보인데_좌표가_없으면_애매하므로_스킵한다() {
        Place a = place(1, PlaceCategory.관광지, null, null);
        Place b = place(2, PlaceCategory.관광지, null, null);
        Optional<Place> selected =
                EngPlaceMatchingService.selectCandidate(List.of(a, b), null, null, Optional.of(PlaceCategory.관광지));
        assertThat(selected).isEmpty();
    }

    @Test
    void 후보가_없으면_스킵한다() {
        assertThat(EngPlaceMatchingService.selectCandidate(List.of(), null, null, Optional.empty()))
                .isEmpty();
    }
}
