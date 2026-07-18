package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.PageResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceNearbyResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceSummaryResponse;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    // size가 그대로 조회 규모·캐시 엔트리 크기가 되므로 상한을 둔다(초과 시 400).
    private static final int MAX_PAGE_SIZE = 100;
    private static final double MAX_NEARBY_RADIUS_KM = 50;

    private final PlaceQueryService placeQueryService;

    public PlaceController(PlaceQueryService placeQueryService) {
        this.placeQueryService = placeQueryService;
    }

    @GetMapping
    public PageResponse<PlaceSummaryResponse> search(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "en") String lang) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다: " + size);
        }
        PlaceCategory categoryEnum = category == null ? null : PlaceCategory.valueOf(category);
        String tagKo = tag == null ? null : Interest.valueOf(tag).labelKo();
        return placeQueryService.search(regionId, categoryEnum, tagKo, keyword, LangFallback.normalize(lang), page, size);
    }

    @GetMapping("/{placeId}")
    public PlaceDetailResponse detail(@PathVariable Long placeId, @RequestParam(defaultValue = "en") String lang) {
        return placeQueryService.getDetail(placeId, LangFallback.normalize(lang));
    }

    @GetMapping("/nearby")
    public List<PlaceNearbyResponse> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radius,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "en") String lang) {
        // 캐시가 없는 엔드포인트라 요청 하나의 계산 비용에 상한을 둔다(서비스의 결과 개수 상한과 한 쌍).
        if (radius <= 0 || radius > MAX_NEARBY_RADIUS_KM) {
            throw new IllegalArgumentException("radius는 0보다 크고 " + MAX_NEARBY_RADIUS_KM + "km 이하여야 합니다: " + radius);
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("좌표 범위를 벗어났습니다: lat=" + lat + ", lng=" + lng);
        }
        PlaceCategory categoryEnum = category == null ? null : PlaceCategory.valueOf(category);
        return placeQueryService.nearby(lat, lng, radius, categoryEnum, LangFallback.normalize(lang));
    }
}
