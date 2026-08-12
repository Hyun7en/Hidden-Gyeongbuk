package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.PageResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceNearbyResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceSummaryResponse;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
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
    private final LanguageRegistry languages;

    public PlaceController(PlaceQueryService placeQueryService, LanguageRegistry languages) {
        this.placeQueryService = placeQueryService;
        this.languages = languages;
    }

    @GetMapping
    public PageResponse<PlaceSummaryResponse> search(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String placeType,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String lang) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다: " + size);
        }
        // 태그는 이제 DB에도 enum 코드로 저장되므로 라벨 변환 없이 그대로 넘긴다.
        // valueOf로 한 번 튕겨 알 수 없는 값이 쿼리까지 내려가는 것은 막는다.
        String tagCode = tag == null ? null : Interest.valueOf(tag).name();
        return placeQueryService.search(
                regionId, parsePlaceType(placeType), tagCode, keyword, languages.resolve(lang), page, size);
    }

    @GetMapping("/{placeId}")
    public PlaceDetailResponse detail(@PathVariable Long placeId, @RequestParam(required = false) String lang) {
        return placeQueryService.getDetail(placeId, languages.resolve(lang));
    }

    @GetMapping("/nearby")
    public List<PlaceNearbyResponse> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radius,
            @RequestParam(required = false) String placeType,
            @RequestParam(required = false) String lang) {
        // 캐시가 없는 엔드포인트라 요청 하나의 계산 비용에 상한을 둔다(서비스의 결과 개수 상한과 한 쌍).
        if (radius <= 0 || radius > MAX_NEARBY_RADIUS_KM) {
            throw new IllegalArgumentException(
                    "radius는 0보다 크고 " + MAX_NEARBY_RADIUS_KM + "km 이하여야 합니다: " + radius);
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("좌표 범위를 벗어났습니다: lat=" + lat + ", lng=" + lng);
        }
        return placeQueryService.nearby(lat, lng, radius, parsePlaceType(placeType), languages.resolve(lang));
    }

    private static PlaceType parsePlaceType(String raw) {
        return raw == null || raw.isBlank() ? null : PlaceType.valueOf(raw);
    }
}
