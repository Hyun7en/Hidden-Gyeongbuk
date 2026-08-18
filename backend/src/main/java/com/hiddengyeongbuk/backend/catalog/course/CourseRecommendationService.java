package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.HubNotFoundException;
import com.hiddengyeongbuk.backend.catalog.PlaceTexts;
import com.hiddengyeongbuk.backend.catalog.RegionNotFoundException;
import com.hiddengyeongbuk.backend.catalog.TranslationLookup;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseListResponse;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseResponse;
import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetail;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetailRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 알고리즘 명세서 3~5장: 후보 생성 → 서로 다른 전략으로 코스 K개 조립 → 4장 점수로 정렬해 상위 반환.
 */
@Service
@Transactional(readOnly = true)
public class CourseRecommendationService {

    private static final int MAX_RESULTS = 3;

    private final HubRepository hubRepository;
    private final RegionRepository regionRepository;
    private final PlaceRepository placeRepository;
    private final FestivalDetailRepository festivalDetailRepository;
    private final CourseAssembler courseAssembler;
    private final CourseScorer courseScorer;
    private final CoursePlanningRules rules;
    private final TranslationLookup translationLookup;

    public CourseRecommendationService(
            HubRepository hubRepository,
            RegionRepository regionRepository,
            PlaceRepository placeRepository,
            FestivalDetailRepository festivalDetailRepository,
            CourseAssembler courseAssembler,
            CourseScorer courseScorer,
            CoursePlanningRules rules,
            TranslationLookup translationLookup) {
        this.hubRepository = hubRepository;
        this.regionRepository = regionRepository;
        this.placeRepository = placeRepository;
        this.festivalDetailRepository = festivalDetailRepository;
        this.courseAssembler = courseAssembler;
        this.courseScorer = courseScorer;
        this.rules = rules;
        this.translationLookup = translationLookup;
    }

    /**
     * null 검증은 요청 경계에서 끝난다 — POST는 {@code @Valid}, GET은 필수 {@code @RequestParam}.
     *
     * <p>캐시 키에 날짜와 지역 목록이 들어가 키 공간이 넓어졌다. 유한하지는 않지만 Caffeine의
     * maximumSize·TTL이 메모리를 유계로 묶고 있고(CacheConfig), 공유 URL 재계산처럼 같은 조건이
     * 반복되는 경로가 실제로 있어 캐시를 유지한다. <b>키 안정성을 위해 지역 목록은 호출 전에
     * 정렬돼 있어야 한다</b>(컨트롤러가 정렬한다) — 순서만 다른 같은 요청이 다른 키가 되면
     * 캐시가 조용히 무력화된다.
     */
    @Cacheable("courseRecommend")
    public CourseListResponse recommend(
            Long hubId,
            List<Long> regionIds,
            LocalDate startDate,
            LocalDate endDate,
            Set<PlaceType> interests,
            Difficulty difficulty,
            String lang) {
        Hub hub = hubRepository.findById(hubId).orElseThrow(() -> new HubNotFoundException(hubId));
        TravelDates dates = TravelDates.of(startDate, endDate, rules.maxNights());
        Set<PlaceType> effectiveInterests = interests == null ? Set.of() : interests;

        List<Long> scope = resolveScope(hubId, regionIds);
        List<Place> places = placeRepository.findByRegionIdIn(scope);

        List<Place> attractionPool = attractionPool(places, effectiveInterests);
        List<Place> foodPool = places.stream().filter(p -> p.getPlaceType() == PlaceType.FD).toList();
        List<Place> lodgingPool = places.stream().filter(p -> p.getPlaceType() == PlaceType.AC).toList();

        if (attractionPool.isEmpty()) {
            throw new NoCourseFoundException("선택한 조건으로 코스를 만들 장소가 부족합니다. 지역을 넓히거나 관심사를 줄여 보세요");
        }

        // 목표 지역 수의 상한 — 범위 안에 place가 실제로 있는 지역만 센다. 지역을 3곳 골랐어도
        // 그중 한 곳에만 데이터가 있으면 도달 가능한 분산은 1이다.
        int availableRegions = (int) places.stream()
                .map(p -> p.getRegion().getId())
                .distinct()
                .count();

        Map<Long, FestivalPeriod> festivals = festivalPeriods(places);

        Map<Set<Long>, CourseCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (CourseAssembler.SelectionStrategy strategy : CourseAssembler.SelectionStrategy.values()) {
            courseAssembler
                    .assemble(
                            hub,
                            attractionPool,
                            foodPool,
                            lodgingPool,
                            dates,
                            difficulty,
                            effectiveInterests,
                            festivals,
                            strategy)
                    .ifPresent(candidate -> uniqueCandidates.putIfAbsent(candidate.placeIdSet(), candidate));
        }
        if (uniqueCandidates.isEmpty()) {
            throw new NoCourseFoundException("선택한 조건으로 코스를 만들 장소가 부족합니다. 지역을 넓히거나 관심사를 줄여 보세요");
        }

        // 완성도 점수가 요청 언어의 번역 행 존재를 보므로(ADR-017), 채점 전에 후보 전체의
        // 텍스트를 한 번에 읽는다. 구 구현은 상위 코스가 정해진 뒤에 읽었는데, has_en 플래그가
        // place에 있어서 채점에 조회가 필요 없었기 때문이다.
        Map<Long, PlaceTexts> texts = translationLookup.forPlaces(allCandidatePlaces(uniqueCandidates.values()), lang);

        List<ScoredCandidate> ranked = uniqueCandidates.values().stream()
                .map(candidate -> new ScoredCandidate(
                        candidate,
                        courseScorer.score(candidate, effectiveInterests, dates, difficulty, availableRegions, texts)))
                .sorted(Comparator.comparingDouble((ScoredCandidate s) -> s.breakdown().total())
                        .thenComparingDouble(s -> s.breakdown().completeness())
                        .thenComparingDouble(s -> s.breakdown().access())
                        .reversed())
                .limit(MAX_RESULTS)
                .toList();

        List<CourseResponse> courses = new ArrayList<>();
        int rank = 1;
        for (ScoredCandidate scored : ranked) {
            courses.add(toResponse(rank++, scored, hub, texts));
        }
        return new CourseListResponse(courses, constraintsOf(dates, difficulty), lang);
    }

    /**
     * 후보 풀에 있는 축제의 개최 기간(추천 알고리즘 §4.7).
     *
     * <p><b>날짜가 없는 행사는 지도에 올리지 않는다</b> — 여기 없으면 기간 판단을 아예 하지 않고
     * 일반 장소처럼 다룬다. 결측을 "안 열린다"로 읽어 빼버리면 수집된 콘텐츠를 근거 없이 잃고,
     * "열린다"로 읽으면 닫힌 축제를 넣게 된다. 모르는 것은 판단하지 않는 쪽이 맞다(ADR-021).
     */
    /**
     * 관광 후보 풀. 관심사는 <b>정렬 가중치가 아니라 필터</b>다 — 아키텍처 §5.4의 2단계가
     * "필터: 좌표 있음 + 관심사 해당(수천 건 → 수십 건)"이라고 규정한다.
     *
     * <p>2026-08-18까지는 FD·AC만 빼고 관심사는 {@link CourseAssembler}의 사전점수에만
     * 반영했다. 사전점수는 순서만 바꿀 뿐 <b>후보를 줄이지 않아서</b>, 일치하는 장소가 거리
     * 예산 안에서 떨어지면 그대로 다른 분류가 뽑혔다. {@code NEAREST_FIRST} 전략에서는 거리에
     * 1000을 곱하는데 사전점수는 최대 100이라, 1km 차이가 관심사 일치 여부를 10배로 압도했다.
     * 실측(2026-08-18): 안동 거점 · HS+NA 요청에 7곳 중 6곳이 요청 밖(상점 3, 음식 2, 체험 1).
     *
     * <p>음식(FD)·숙박(AC)은 일자별 끼니·숙박 자리에서 따로 뽑으므로 여기서 항상 뺀다. 코스에서
     * 빠지는 게 아니라 뽑는 자리가 다를 뿐이다(추천 알고리즘 §5). 그래서 <b>관심사로 FD·AC를
     * 골라도 관광 후보의 필터로는 쓰지 않는다</b> — 그걸로 거르면 관광 자리가 통째로 빈다.
     * 필터 기준은 "관심사 − {FD, AC}"이고, 그게 비면(관심사 미지정이거나 FD·AC뿐) 거르지 않는다.
     *
     * <p>필터 후 후보가 모자라면 코스가 짧아지거나(조립 루프가 그날치를 덜 채운다) 아예 안
     * 만들어진다. 그게 맞다 — 요청하지 않은 분류로 자리를 채우는 것은 취향의 문제가 아니라
     * 틀린 결과이고, 짧은 코스는 사용자가 보고 조건을 넓힐 수 있다.
     */
    // 서비스 전체를 띄우지 않고 이 규칙만 검증할 수 있도록 패키지 공개로 둔다.
    static List<Place> attractionPool(List<Place> places, Set<PlaceType> interests) {
        Set<PlaceType> wanted = interests.stream()
                .filter(type -> type != PlaceType.FD && type != PlaceType.AC)
                .collect(Collectors.toUnmodifiableSet());
        return places.stream()
                .filter(p -> p.getPlaceType() != PlaceType.FD && p.getPlaceType() != PlaceType.AC)
                .filter(p -> wanted.isEmpty() || wanted.contains(p.getPlaceType()))
                .toList();
    }

    private Map<Long, FestivalPeriod> festivalPeriods(List<Place> places) {
        List<Long> placeIds = places.stream().map(Place::getId).toList();
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return festivalDetailRepository.findByPlaceIdIn(placeIds).stream()
                .filter(f -> f.getStartDate() != null && f.getEndDate() != null)
                .collect(Collectors.toMap(
                        FestivalDetail::getPlaceId, f -> new FestivalPeriod(f.getStartDate(), f.getEndDate())));
    }

    /**
     * 추천 범위(ADR-019). 지역 미선택이면 거점의 소속 지역 전체, 선택했으면 그 지역들이다.
     *
     * <p><b>선택한 지역을 거점 소속으로 제한하지 않는다.</b> 거점은 출발점이고 지역 선택은 범위
     * 지정이라 서로 다른 축이다 — 안동에서 출발해 청송을 보러 가는 요청을 막을 이유가 없다.
     * 다만 없는 지역 ID는 조용히 무시하지 않고 404로 알린다.
     */
    private List<Long> resolveScope(Long hubId, List<Long> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            List<Long> hubRegions = regionRepository.findByHubId(hubId).stream()
                    .map(Region::getId)
                    .toList();
            if (hubRegions.isEmpty()) {
                throw new NoCourseFoundException("이 거점에 연결된 지역이 없습니다");
            }
            return hubRegions;
        }
        List<Long> found = regionRepository.findAllById(regionIds).stream()
                .map(Region::getId)
                .toList();
        List<Long> missing = regionIds.stream().filter(id -> !found.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new RegionNotFoundException(missing.get(0));
        }
        return found;
    }

    private CourseListResponse.Constraints constraintsOf(TravelDates dates, Difficulty difficulty) {
        return new CourseListResponse.Constraints(
                rules.targetPlaceCount(dates.days()),
                rules.maxDistanceKm(difficulty, dates.days()),
                rules.requiredLodging(dates.nights()));
    }

    /** 후보 코스 전체에 등장하는 장소(방문지+숙박)를 모아 번역을 일괄 조회한다. */
    private static List<Place> allCandidatePlaces(Collection<CourseCandidate> candidates) {
        return candidates.stream()
                .flatMap(c -> c.days().stream())
                .flatMap(day -> Stream.concat(day.places().stream(), Stream.ofNullable(day.lodging())))
                .toList();
    }

    private record ScoredCandidate(CourseCandidate candidate, CourseScorer.Breakdown breakdown) {}

    private CourseResponse toResponse(int rank, ScoredCandidate scored, Hub hub, Map<Long, PlaceTexts> texts) {
        CourseCandidate candidate = scored.candidate();
        CourseScorer.Breakdown b = scored.breakdown();
        List<CourseResponse.DayResponse> days =
                candidate.days().stream().map(day -> toDayResponse(day, texts)).toList();
        return new CourseResponse(
                rank,
                round2(b.total()),
                new CourseResponse.Breakdown(
                        round2(b.interest()), round2(b.access()), round2(b.dispersion()), round2(b.completeness())),
                hub.getId(),
                round2(candidate.totalDistanceKm()),
                candidate.regionCount(),
                days);
    }

    private CourseResponse.DayResponse toDayResponse(CourseDay day, Map<Long, PlaceTexts> texts) {
        List<CourseResponse.CoursePlaceResponse> places = new ArrayList<>();
        int order = 1;
        for (Place place : day.places()) {
            places.add(new CourseResponse.CoursePlaceResponse(
                    order++,
                    place.getId(),
                    place.getPlaceType().name(),
                    textOf(texts, place.getId()).title(),
                    place.getLat(),
                    place.getLng()));
        }
        CourseResponse.LodgingResponse lodging = day.lodging() == null
                ? null
                : new CourseResponse.LodgingResponse(
                        day.lodging().getId(), textOf(texts, day.lodging().getId()).title());
        return new CourseResponse.DayResponse(day.day(), places, lodging);
    }

    private static PlaceTexts textOf(Map<Long, PlaceTexts> texts, Long placeId) {
        return texts.getOrDefault(placeId, PlaceTexts.empty());
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
