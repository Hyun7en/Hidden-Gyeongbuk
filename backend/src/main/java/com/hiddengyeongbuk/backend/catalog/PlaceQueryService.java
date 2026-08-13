package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.PageResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceNearbyResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceSummaryResponse;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetailRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceImage;
import com.hiddengyeongbuk.backend.domain.place.PlaceImageRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 검색·상세·주변 조회(API 명세서 §3).
 *
 * <p>여기 들어오는 {@code lang}은 이미 {@code LanguageRegistry.resolve()}를 거친 값이다 —
 * 컨트롤러가 해석해서 넘기므로 캐시 키 공간이 활성 언어 수로 유계다.
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
    // 검색어 없는 목록 조회(지역/타입/태그 필터, 키 공간 유한·재사용 빈도 높음)만 캐싱한다.
    @Cacheable(value = "placeSearch", condition = "#keyword == null || #keyword.isBlank()")
    public PageResponse<PlaceSummaryResponse> search(
            Long regionId, PlaceType placeType, String keyword, String lang, int page, int size) {
        Specification<Place> spec = buildSpecification(regionId, placeType, keyword, lang);
        // 정렬 없는 페이지네이션은 페이지 간 순서가 비결정적이라(같은 장소 중복/누락) 항상 PK로 고정한다.
        Page<Place> result =
                placeRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
        Map<Long, PlaceTexts> texts = translationLookup.forPlaces(result.getContent(), lang);
        List<PlaceSummaryResponse> content = result.getContent().stream()
                .map(p -> toSummary(p, texts))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), lang);
    }

    @Cacheable("placeDetail")
    public PlaceDetailResponse getDetail(Long placeId, String lang) {
        Place place = placeRepository.findById(placeId).orElseThrow(() -> new PlaceNotFoundException(placeId));
        return toDetail(place, lang);
    }

    // nearby는 캐싱하지 않는다 — lat/lng가 사실상 연속값이라 키 공간이 무한대에 가까워
    // 캐시 적중률이 거의 0이고 엔트리만 계속 쌓인다. 대신 캐시가 없는 만큼 호출 비용 자체에
    // 상한을 건다: 반경 바운딩 박스로 DB에서 선필터하고, 결과 개수도 잘라서 돌려준다.
    public List<PlaceNearbyResponse> nearby(
            double lat, double lng, double radiusKm, PlaceType placeType, String lang) {
        // 위도 1도 ≈ 111km 근사 사각형으로 DB에서 선필터 → Haversine으로 정밀 필터.
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        Specification<Place> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(
                    root.get("lat"), BigDecimal.valueOf(lat - latDelta), BigDecimal.valueOf(lat + latDelta)));
            predicates.add(cb.between(
                    root.get("lng"), BigDecimal.valueOf(lng - lngDelta), BigDecimal.valueOf(lng + lngDelta)));
            if (placeType != null) {
                predicates.add(cb.equal(root.get("placeType"), placeType));
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
        Map<Long, PlaceTexts> texts = translationLookup.forPlaces(
                withinRadius.stream().map(Candidate::place).toList(), lang);
        return withinRadius.stream()
                .map(c -> new PlaceNearbyResponse(toSummary(c.place(), texts), c.distanceKm()))
                .toList();
    }

    private Specification<Place> buildSpecification(
            Long regionId, PlaceType placeType, String keyword, String lang) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            if (regionId != null) {
                predicates.add(cb.equal(root.get("region").get("id"), regionId));
            }
            if (placeType != null) {
                predicates.add(cb.equal(root.get("placeType"), placeType));
            }
            if (keyword != null && !keyword.isBlank()) {
                // 명칭 검색은 요청 언어의 title을 대상으로 한다(API 명세서 §3.1). 국문 제목이
                // place 컬럼에서 사라졌으므로 place_translation만 보면 되고, 언어 조건이 붙어
                // "영문 사용자에게 국문 제목이 매치되는" 혼선도 사라진다. 대신 그 언어 행이
                // 아직 없는 장소는 검색에 안 걸린다 — 번역 백필이 끝나면 해소된다.
                Subquery<Long> titleMatch = query.subquery(Long.class);
                Root<PlaceTranslation> tr = titleMatch.from(PlaceTranslation.class);
                titleMatch
                        .select(tr.get("placeId"))
                        .where(
                                cb.equal(tr.get("placeId"), root.get("id")),
                                cb.equal(tr.get("lang"), lang),
                                cb.like(tr.get("title"), "%" + keyword + "%"));
                predicates.add(cb.exists(titleMatch));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PlaceSummaryResponse toSummary(Place place, Map<Long, PlaceTexts> texts) {
        PlaceTexts t = texts.getOrDefault(place.getId(), PlaceTexts.empty());
        return new PlaceSummaryResponse(
                place.getId(),
                place.getPlaceType().name(),
                t.title(),
                t.addr(),
                place.getLat(),
                place.getLng(),
                place.getFirstImage());
    }

    private PlaceDetailResponse toDetail(Place place, String lang) {
        List<String> images = placeImageRepository.findByPlaceIdOrderBySortOrder(place.getId()).stream()
                .map(PlaceImage::getImageUrl)
                .toList();
        // 제목·설명·주소·현지주소가 모두 같은 장소의 번역 행들에서 나오므로 한 번만 조회한다.
        PlaceTexts t = translationLookup.forPlace(place.getId(), lang);
        PlaceDetailResponse.FestivalResponse festival = festivalDetailRepository
                .findById(place.getId())
                .map(f -> new PlaceDetailResponse.FestivalResponse(f.getStartDate(), f.getEndDate()))
                .orElse(null);

        return new PlaceDetailResponse(
                place.getId(),
                place.getPlaceType().name(),
                place.getRegion().getId(),
                t.title(),
                t.addr(),
                t.addrLocal(),
                place.getTel(),
                place.getLat(),
                place.getLng(),
                t.overview(),
                place.getFirstImage(),
                images,
                t.resolvedLang(),
                t.translationSource(),
                festival);
    }

    private record Candidate(Place place, double distanceKm) {}
}
