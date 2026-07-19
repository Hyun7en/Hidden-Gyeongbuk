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
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    // nearby는 캐시가 없는 공개 엔드포인트라 응답 크기에도 상한을 둔다.
    private static final int MAX_NEARBY_RESULTS = 50;

    private final PlaceRepository placeRepository;
    private final PlaceImageRepository placeImageRepository;
    private final FestivalDetailRepository festivalDetailRepository;
    private final TranslationLookup translationLookup;

    public PlaceQueryService(
            PlaceRepository placeRepository,
            PlaceImageRepository placeImageRepository,
            FestivalDetailRepository festivalDetailRepository,
            TranslationLookup translationLookup) {
        this.placeRepository = placeRepository;
        this.placeImageRepository = placeImageRepository;
        this.festivalDetailRepository = festivalDetailRepository;
        this.translationLookup = translationLookup;
    }

    // keyword는 자유 입력이라 캐시 키 공간이 무한하다(nearby를 캐싱에서 뺀 것과 같은 이유) —
    // 검색어 없는 목록 조회(지역/카테고리/태그 필터, 키 공간 유한·재사용 빈도 높음)만 캐싱한다.
    @Cacheable(value = "placeSearch", condition = "#keyword == null || #keyword.isBlank()")
    public PageResponse<PlaceSummaryResponse> search(
            Long regionId, PlaceCategory category, String tagKo, String keyword, String lang, int page, int size) {
        Specification<Place> spec = buildSpecification(regionId, category, tagKo, keyword);
        // 정렬 없는 페이지네이션은 페이지 간 순서가 비결정적이라(같은 장소 중복/누락) 항상 PK로 고정한다.
        Page<Place> result = placeRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
        Map<Long, PlaceTranslation> translations = translationLookup.forPlaces(result.getContent(), lang);
        List<PlaceSummaryResponse> content = result.getContent().stream()
                .map(p -> toSummary(p, lang, translations))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Cacheable("placeDetail")
    public PlaceDetailResponse getDetail(Long placeId, String lang) {
        Place place = placeRepository.findById(placeId).orElseThrow(() -> new PlaceNotFoundException(placeId));
        return toDetail(place, lang);
    }

    // nearby는 캐싱하지 않는다 — lat/lng가 사실상 연속값이라 키 공간이 무한대에 가까워
    // 캐시 적중률이 거의 0이고 엔트리만 계속 쌓인다. 대신 캐시가 없는 만큼 호출 비용 자체에
    // 상한을 건다: 반경 바운딩 박스로 DB에서 선필터하고, 결과 개수도 잘라서 돌려준다.
    public List<PlaceNearbyResponse> nearby(double lat, double lng, double radiusKm, PlaceCategory category, String lang) {
        // 위도 1도 ≈ 111km 근사 사각형으로 DB에서 선필터 → Haversine으로 정밀 필터.
        // 전 장소 풀스캔 + 전건 거리 계산을 반경 주변 장소로 줄인다.
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        Specification<Place> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(
                    root.get("lat"), BigDecimal.valueOf(lat - latDelta), BigDecimal.valueOf(lat + latDelta)));
            predicates.add(cb.between(
                    root.get("lng"), BigDecimal.valueOf(lng - lngDelta), BigDecimal.valueOf(lng + lngDelta)));
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Candidate> withinRadius = placeRepository.findAll(spec).stream()
                .filter(p -> p.getLat() != null && p.getLng() != null)
                .map(p -> new Candidate(
                        p, GeoUtils.haversineKm(lat, lng, p.getLat().doubleValue(), p.getLng().doubleValue())))
                .filter(c -> c.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(Candidate::distanceKm))
                .limit(MAX_NEARBY_RESULTS)
                .toList();
        Map<Long, PlaceTranslation> translations = translationLookup.forPlaces(
                withinRadius.stream().map(Candidate::place).toList(), lang);
        return withinRadius.stream()
                .map(c -> new PlaceNearbyResponse(toSummary(c.place(), lang, translations), c.distanceKm()))
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
                // 국문 제목 + 번역/공식 영문 제목(place_translation.title) 양쪽을 매치해야 영문
                // 키워드 검색이 실질적으로 동작한다. 영문 제목은 이제 place_translation에만 있다.
                Subquery<Long> translationTitleMatch = query.subquery(Long.class);
                Root<PlaceTranslation> translationRoot = translationTitleMatch.from(PlaceTranslation.class);
                translationTitleMatch
                        .select(translationRoot.get("placeId"))
                        .where(
                                cb.equal(translationRoot.get("placeId"), root.get("id")),
                                cb.like(translationRoot.get("title"), pattern));
                predicates.add(cb.or(
                        cb.like(root.get("titleKo"), pattern),
                        cb.exists(translationTitleMatch)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PlaceSummaryResponse toSummary(Place place, String lang, Map<Long, PlaceTranslation> translations) {
        PlaceTranslation tr = translations.get(place.getId());
        return new PlaceSummaryResponse(
                place.getId(),
                place.getCategory().name(),
                LangFallback.title(place, tr, lang),
                LangFallback.addr(place, tr, lang),
                place.getLat(),
                place.getLng(),
                place.getFirstImage(),
                tagNames(place));
    }

    private PlaceDetailResponse toDetail(Place place, String lang) {
        List<String> images = placeImageRepository.findByPlaceIdOrderBySortOrder(place.getId()).stream()
                .map(PlaceImage::getImageUrl)
                .toList();
        // 제목·설명·주소가 모두 같은 번역행을 쓰므로 한 번만 조회한다.
        PlaceTranslation tr = translationLookup.forPlaces(List.of(place), lang).get(place.getId());
        String translationSource = tr != null ? tr.getSource().name() : null;
        PlaceDetailResponse.FestivalResponse festival = festivalDetailRepository
                .findById(place.getId())
                .map(f -> new PlaceDetailResponse.FestivalResponse(f.getStartDate(), f.getEndDate()))
                .orElse(null);

        return new PlaceDetailResponse(
                place.getId(),
                place.getContentId(),
                place.getCategory().name(),
                LangFallback.title(place, tr, lang),
                place.getTitleKo(),
                LangFallback.addr(place, tr, lang),
                place.getTel(),
                place.getLat(),
                place.getLng(),
                LangFallback.overview(place, tr, lang),
                place.getFirstImage(),
                images,
                tagNames(place),
                translationSource,
                festival);
    }

    private static List<String> tagNames(Place place) {
        return place.getTags().stream().map(PlaceTag::getTag).toList();
    }

    private record Candidate(Place place, double distanceKm) {}
}
