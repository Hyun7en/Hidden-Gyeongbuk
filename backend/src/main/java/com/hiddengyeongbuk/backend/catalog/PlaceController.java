package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.PageResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceNearbyResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceSummaryResponse;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String lang) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다: " + size);
        }
        // tag 파라미터는 폐기됐다 — 관심사와 장소 타입이 같은 어휘로 합쳐져 placeType 하나가
        // 그 역할을 겸한다(ADR-024).
        return placeQueryService.search(
                regionId, parsePlaceTypes(placeType), keyword, languages.resolve(lang), page, size);
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
        return placeQueryService.nearby(lat, lng, radius, parsePlaceTypes(placeType), languages.resolve(lang));
    }

    /**
     * {@code placeType}은 <b>콤마로 복수 지정할 수 있다</b>(API 명세서 §3.1). 단일 값도 그대로
     * 동작하므로 기존 호출부는 영향받지 않는다.
     *
     * <p>{@code PlaceType.valueOf()}를 그대로 부르지 않는 이유는 실패 메시지 때문이다 — 그 예외의
     * 메시지가 {@code "No enum constant com.hiddengyeongbuk...PlaceType.BOGUS"}라 <b>패키지 구조가
     * 클라이언트 응답에 그대로 실려 나간다.</b> 알려줄 것은 "무엇을 보낼 수 있는가"이지 우리 패키지
     * 이름이 아니다.
     *
     * <p>안내하는 목록은 {@link PlaceType#values()}가 아니라 {@link PlaceType#served()}다 — 음식점은
     * 서비스 대상이 아니라(ADR-025) 안내해 봐야 결과가 0건이다.
     */
    private static Set<PlaceType> parsePlaceTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<PlaceType> parsed = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String code = token.trim();
            if (code.isEmpty()) {
                continue;
            }
            parsed.add(PlaceType.ofSourceCode(code).orElseThrow(() -> new IllegalArgumentException(
                    "placeType 값이 올바르지 않습니다: " + code + " (가능한 값: "
                            + PlaceType.served().stream().map(Enum::name).collect(Collectors.joining(", "))
                            + ")")));
        }
        return Set.copyOf(parsed);
    }
}
