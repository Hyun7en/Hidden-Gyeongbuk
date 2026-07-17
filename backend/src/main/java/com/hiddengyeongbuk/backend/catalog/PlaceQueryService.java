package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.PageResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceNearbyResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceSummaryResponse;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetailRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceImage;
import com.hiddengyeongbuk.backend.domain.place.PlaceImageRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummary;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummaryRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 검색·상세·주변 조회. API 명세서 3장 기준.
 * lang="en"이 기본값이며, 국문 필드로 폴백한다(외국인 관광객 우선 노출).
 */
@Service
@Transactional(readOnly = true)
public class PlaceQueryService {

    private final PlaceRepository placeRepository;
    private final PlaceSummaryRepository placeSummaryRepository;
    private final PlaceImageRepository placeImageRepository;
    private final FestivalDetailRepository festivalDetailRepository;
    private final TranslatedTitleLookup translatedTitleLookup;

    public PlaceQueryService(
            PlaceRepository placeRepository,
            PlaceSummaryRepository placeSummaryRepository,
            PlaceImageRepository placeImageRepository,
            FestivalDetailRepository festivalDetailRepository,
            TranslatedTitleLookup translatedTitleLookup) {
        this.placeRepository = placeRepository;
        this.placeSummaryRepository = placeSummaryRepository;
        this.placeImageRepository = placeImageRepository;
        this.festivalDetailRepository = festivalDetailRepository;
        this.translatedTitleLookup = translatedTitleLookup;
    }

    // keyword는 자유 입력이라 캐시 키 공간이 무한하다(nearby를 캐싱에서 뺀 것과 같은 이유) —
    // 검색어 없는 목록 조회(지역/카테고리/태그 필터, 키 공간 유한·재사용 빈도 높음)만 캐싱한다.
    @Cacheable(value = "placeSearch", condition = "#keyword == null || #keyword.isBlank()")
    public PageResponse<PlaceSummaryResponse> search(
            Long regionId, PlaceCategory category, String tagKo, String keyword, String lang, int page, int size) {
        Specification<Place> spec = buildSpecification(regionId, category, tagKo, keyword);
        Page<Place> result = placeRepository.findAll(spec, PageRequest.of(page, size));
        Map<Long, String> translatedTitles = translatedTitleLookup.forPlaces(result.getContent(), lang);
        List<PlaceSummaryResponse> content = result.getContent().stream()
                .map(p -> toSummary(p, lang, translatedTitles))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Cacheable("placeDetail")
    public PlaceDetailResponse getDetail(Long placeId, String lang) {
        Place place = placeRepository.findById(placeId).orElseThrow(() -> new PlaceNotFoundException(placeId));
        return toDetail(place, lang);
    }

    // nearby는 캐싱하지 않는다 — lat/lng가 사실상 연속값이라 키 공간이 무한대에 가까워
    // 캐시 적중률이 거의 0이고 엔트리만 계속 쌓인다(ConcurrentMapCache는 만료·크기 제한이 없음).
    public List<PlaceNearbyResponse> nearby(double lat, double lng, double radiusKm, PlaceCategory category) {
        Specification<Place> spec = category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category);
        List<Place> candidates = spec == null ? placeRepository.findAll() : placeRepository.findAll(spec);

        List<Candidate> withinRadius = candidates.stream()
                .filter(p -> p.getLat() != null && p.getLng() != null)
                .map(p -> new Candidate(
                        p, GeoUtils.haversineKm(lat, lng, p.getLat().doubleValue(), p.getLng().doubleValue())))
                .filter(c -> c.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(Candidate::distanceKm))
                .toList();
        Map<Long, String> translatedTitles = translatedTitleLookup.forPlaces(
                withinRadius.stream().map(Candidate::place).toList(), LangFallback.EN);
        return withinRadius.stream()
                .map(c -> new PlaceNearbyResponse(toSummary(c.place(), LangFallback.EN, translatedTitles), c.distanceKm()))
                .toList();
    }

    private Specification<Place> buildSpecification(Long regionId, PlaceCategory category, String tagKo, String keyword) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            if (regionId != null) {
                predicates.add(cb.equal(root.get("region").get("id"), regionId));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (tagKo != null && !tagKo.isBlank()) {
                predicates.add(cb.equal(root.join("tags").get("tag"), tagKo));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(cb.like(root.get("titleKo"), pattern), cb.like(root.get("titleEn"), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PlaceSummaryResponse toSummary(Place place, String lang, Map<Long, String> translatedTitles) {
        return new PlaceSummaryResponse(
                place.getId(),
                place.getCategory().name(),
                LangFallback.title(place, translatedTitles.get(place.getId()), lang),
                place.getAddr(),
                place.getLat(),
                place.getLng(),
                place.getFirstImage(),
                tagNames(place));
    }

    private PlaceDetailResponse toDetail(Place place, String lang) {
        List<String> images = placeImageRepository.findByPlaceIdOrderBySortOrder(place.getId()).stream()
                .map(PlaceImage::getImageUrl)
                .toList();
        // summary 블록 응답과 제목 3단계 폴백이 같은 행을 쓰므로 한 번만 조회한다.
        Optional<PlaceSummary> placeSummary = LangFallback.EN.equalsIgnoreCase(lang)
                ? placeSummaryRepository.findById(place.getId())
                : Optional.empty();
        PlaceDetailResponse.SummaryResponse summary = placeSummary
                .map(s -> new PlaceDetailResponse.SummaryResponse(
                        s.getTitleEn(), s.getAddrEn(), s.getSummaryEn(), s.getSource().name()))
                .orElse(null);
        PlaceDetailResponse.FestivalResponse festival = festivalDetailRepository
                .findById(place.getId())
                .map(f -> new PlaceDetailResponse.FestivalResponse(f.getStartDate(), f.getEndDate()))
                .orElse(null);

        return new PlaceDetailResponse(
                place.getId(),
                place.getContentId(),
                place.getCategory().name(),
                LangFallback.title(place, placeSummary.map(PlaceSummary::getTitleEn).orElse(null), lang),
                place.getAddr(),
                place.getTel(),
                place.getLat(),
                place.getLng(),
                resolveOverview(place, lang),
                place.getFirstImage(),
                images,
                tagNames(place),
                summary,
                festival);
    }

    private static String resolveOverview(Place place, String lang) {
        return LangFallback.overview(place, lang);
    }

    private static List<String> tagNames(Place place) {
        return place.getTags().stream().map(PlaceTag::getTag).toList();
    }

    private record Candidate(Place place, double distanceKm) {}
}
