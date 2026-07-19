package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.catalog.HubNotFoundException;
import com.hiddengyeongbuk.backend.catalog.Interest;
import com.hiddengyeongbuk.backend.catalog.LangFallback;
import com.hiddengyeongbuk.backend.catalog.TranslationLookup;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseListResponse;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseResponse;
import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 알고리즘 명세서 3~5장: 후보 생성 → 서로 다른 전략으로 코스 K개 조립 → 4장 점수로 정렬해 상위 반환.
 * hub의 지역(Spoke)에 수집된 place가 아직 없으면(P1 데이터 수집이 진행 중이라 대부분의 hub가 이 상태) 빈 결과를 돌려준다.
 */
@Service
@Transactional(readOnly = true)
public class CourseRecommendationService {

    private static final int MAX_RESULTS = 3;

    private final HubRepository hubRepository;
    private final RegionRepository regionRepository;
    private final PlaceRepository placeRepository;
    private final CourseAssembler courseAssembler;
    private final CourseScorer courseScorer;
    private final TranslationLookup translationLookup;

    public CourseRecommendationService(
            HubRepository hubRepository,
            RegionRepository regionRepository,
            PlaceRepository placeRepository,
            CourseAssembler courseAssembler,
            CourseScorer courseScorer,
            TranslationLookup translationLookup) {
        this.hubRepository = hubRepository;
        this.regionRepository = regionRepository;
        this.placeRepository = placeRepository;
        this.courseAssembler = courseAssembler;
        this.courseScorer = courseScorer;
        this.translationLookup = translationLookup;
    }

    // null 검증은 요청 경계에서 끝난다 — POST는 @Valid(CourseRecommendRequest), GET은 필수 @RequestParam.
    @Cacheable("courseRecommend")
    public CourseListResponse recommend(Long hubId, Duration duration, Set<Interest> interests, Difficulty difficulty, String lang) {
        Hub hub = hubRepository.findById(hubId).orElseThrow(() -> new HubNotFoundException(hubId));
        Set<Interest> effectiveInterests = interests == null ? Set.of() : interests;

        List<Long> regionIds =
                regionRepository.findByHubId(hubId).stream().map(Region::getId).toList();
        if (regionIds.isEmpty()) {
            return new CourseListResponse(List.of());
        }

        List<Place> places = placeRepository.findByRegionIdInWithTags(regionIds);
        List<Place> attractionPool = places.stream()
                .filter(p -> p.getCategory() != PlaceCategory.음식점 && p.getCategory() != PlaceCategory.숙박)
                .toList();
        List<Place> foodPool = places.stream().filter(p -> p.getCategory() == PlaceCategory.음식점).toList();
        List<Place> lodgingPool = places.stream().filter(p -> p.getCategory() == PlaceCategory.숙박).toList();

        if (attractionPool.isEmpty()) {
            return new CourseListResponse(List.of());
        }

        Map<Set<Long>, CourseCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (CourseAssembler.SelectionStrategy strategy : CourseAssembler.SelectionStrategy.values()) {
            courseAssembler
                    .assemble(hub, attractionPool, foodPool, lodgingPool, duration, difficulty, effectiveInterests, strategy)
                    .ifPresent(candidate -> uniqueCandidates.putIfAbsent(candidate.placeIdSet(), candidate));
        }

        List<ScoredCandidate> ranked = uniqueCandidates.values().stream()
                .map(candidate -> new ScoredCandidate(
                        candidate, courseScorer.score(candidate, effectiveInterests, duration, difficulty)))
                .sorted(Comparator.comparingDouble((ScoredCandidate s) -> s.breakdown().total())
                        .thenComparingDouble(s -> s.breakdown().completeness())
                        .thenComparingDouble(s -> s.breakdown().access())
                        .reversed())
                .limit(MAX_RESULTS)
                .toList();

        Map<Long, PlaceTranslation> translations = translationLookup.forPlaces(placesOf(ranked), lang);
        List<CourseResponse> courses = new ArrayList<>();
        int rank = 1;
        for (ScoredCandidate scored : ranked) {
            courses.add(toResponse(rank++, scored, hub, lang, translations));
        }
        return new CourseListResponse(courses);
    }

    /** 상위 코스들에 등장하는 모든 장소(방문지+숙박)를 모아 번역 제목 일괄 조회에 쓴다. */
    private static List<Place> placesOf(List<ScoredCandidate> ranked) {
        return ranked.stream()
                .flatMap(s -> s.candidate().days().stream())
                .flatMap(day -> Stream.concat(day.places().stream(), Stream.ofNullable(day.lodging())))
                .toList();
    }

    private record ScoredCandidate(CourseCandidate candidate, CourseScorer.Breakdown breakdown) {}

    private CourseResponse toResponse(
            int rank, ScoredCandidate scored, Hub hub, String lang, Map<Long, PlaceTranslation> translations) {
        CourseCandidate candidate = scored.candidate();
        CourseScorer.Breakdown b = scored.breakdown();
        List<CourseResponse.DayResponse> days = candidate.days().stream()
                .map(day -> toDayResponse(day, lang, translations))
                .toList();
        return new CourseResponse(
                rank,
                round2(b.total()),
                new CourseResponse.Breakdown(
                        round2(b.interest()), round2(b.access()), round2(b.stay()), round2(b.dispersion()), round2(b.completeness())),
                hub.getId(),
                round2(candidate.totalDistanceKm()),
                days);
    }

    private CourseResponse.DayResponse toDayResponse(CourseDay day, String lang, Map<Long, PlaceTranslation> translations) {
        List<CourseResponse.CoursePlaceResponse> places = new ArrayList<>();
        int order = 1;
        for (Place place : day.places()) {
            places.add(new CourseResponse.CoursePlaceResponse(
                    order++,
                    place.getId(),
                    place.getCategory().name(),
                    LangFallback.title(place, translations.get(place.getId()), lang),
                    place.getLat(),
                    place.getLng()));
        }
        CourseResponse.LodgingResponse lodging = day.lodging() == null
                ? null
                : new CourseResponse.LodgingResponse(
                        day.lodging().getId(),
                        LangFallback.title(day.lodging(), translations.get(day.lodging().getId()), lang));
        return new CourseResponse.DayResponse(day.day(), places, lodging);
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
