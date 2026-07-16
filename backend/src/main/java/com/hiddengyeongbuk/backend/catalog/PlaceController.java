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
        PlaceCategory categoryEnum = category == null ? null : PlaceCategory.valueOf(category);
        String tagKo = tag == null ? null : Interest.valueOf(tag).labelKo();
        return placeQueryService.search(regionId, categoryEnum, tagKo, keyword, lang, page, size);
    }

    @GetMapping("/{placeId}")
    public PlaceDetailResponse detail(@PathVariable Long placeId, @RequestParam(defaultValue = "en") String lang) {
        return placeQueryService.getDetail(placeId, lang);
    }

    @GetMapping("/nearby")
    public List<PlaceNearbyResponse> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radius,
            @RequestParam(required = false) String category) {
        PlaceCategory categoryEnum = category == null ? null : PlaceCategory.valueOf(category);
        return placeQueryService.nearby(lat, lng, radius, categoryEnum);
    }
}
