package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.PlaceTexts;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 추천 알고리즘 명세서 4장: 관심사·접근성·지역분산·데이터완성도 <b>4개 항목</b>을 가중 합산한다.
 *
 * <p>체류 적합도가 빠져 5개에서 4개가 됐다(ADR-021). 뺀 이유는 그 항목만 <b>우리가 만든 값을
 * 우리가 채점하는</b> 구조였기 때문이다 — 장소 수는 조립할 때 우리가 정하는 값이지, 관심사나
 * 좌표처럼 수집된 데이터에서 나오는 값이 아니다. 기간 대비 장소 수 조절 자체는 여전히 필요하므로
 * {@link CoursePlanningRules}의 제약으로 남아 있다.
 */
@Component
class CourseScorer {

    record Breakdown(double interest, double access, double dispersion, double completeness, double total) {}

    private final CourseProperties.Weights weights;
    private final CoursePlanningRules rules;

    CourseScorer(CourseProperties properties, CoursePlanningRules rules) {
        this.weights = properties.weights();
        this.rules = rules;
    }

    /**
     * @param availableRegions 추천 범위에 실제로 존재하는 지역 수. 지역분산의 목표치를 여기에
     *     맞춰 깎는다 — 자세한 이유는 {@link CoursePlanningRules#targetRegionCount}.
     */
    Breakdown score(
            CourseCandidate candidate,
            Set<PlaceType> interests,
            TravelDates dates,
            Difficulty difficulty,
            int availableRegions,
            Map<Long, PlaceTexts> texts) {
        List<Place> allPlaces = candidate.allPlaces();
        double interest = interestScore(allPlaces, interests);
        double access = accessScore(candidate.totalDistanceKm(), dates, difficulty);
        double dispersion = dispersionScore(allPlaces, dates, availableRegions);
        double completeness = completenessScore(allPlaces, texts);
        double total = (weights.interest() * interest
                        + weights.access() * access
                        + weights.dispersion() * dispersion
                        + weights.completeness() * completeness)
                / 100.0;
        return new Breakdown(interest, access, dispersion, completeness, total);
    }

    /**
     * 4.1 관심사 적합도: 코스가 커버한 사용자 관심사 종류 비율.
     *
     * <p>근거 데이터가 {@code place_tag} → {@code place.place_type}으로 바뀌었다(ADR-024).
     * 관심사와 장소 분류가 같은 어휘라 매핑 없이 그대로 대조한다.
     *
     * <p><b>장소당 분류가 정확히 하나</b>라, 코스가 커버하는 관심사 수는 장소 수를 넘지 못한다.
     * 관심사를 장소 수보다 많이 고르면 만점이 구조적으로 불가능하다 — 모든 후보 코스가 같은
     * 상한을 공유하므로 코스 간 상대 비교에는 영향이 없다. 정규화를 {@code min(|I|, |C|)}
     * 기준으로 바꿀지는 실데이터 확보 후 판단한다(추천 알고리즘 §7).
     */
    private double interestScore(List<Place> places, Set<PlaceType> interests) {
        if (interests.isEmpty()) {
            return 100.0;
        }
        Set<PlaceType> covered = places.stream().map(Place::getPlaceType).collect(Collectors.toSet());
        long matched = interests.stream().filter(covered::contains).count();
        return 100.0 * matched / interests.size();
    }

    /** 4.2 접근성 적합도: 총 이동거리를 난이도·기간 예산(Dmax) 대비 정규화. */
    private double accessScore(double totalDistanceKm, TravelDates dates, Difficulty difficulty) {
        double dmax = rules.maxDistanceKm(difficulty, dates.days());
        return Math.max(0, 1 - totalDistanceKm / dmax) * 100;
    }

    /** 4.4 지역분산 기여도: 방문 지역 다양성. 목표 지역 수가 1이면(당일·단일지역) 항상 만점. */
    private double dispersionScore(List<Place> places, TravelDates dates, int availableRegions) {
        int rmax = rules.targetRegionCount(dates.days(), availableRegions);
        if (rmax == 1) {
            return 100.0;
        }
        long distinctRegions =
                places.stream().map(p -> p.getRegion().getId()).distinct().count();
        long r = Math.min(distinctRegions, rmax);
        return 100.0 * (0.5 + 0.5 * (r - 1) / (rmax - 1));
    }

    /** 4.5 데이터 완성도: 장소별 필수 필드 충실도의 코스 평균. */
    private double completenessScore(List<Place> places, Map<Long, PlaceTexts> texts) {
        if (places.isEmpty()) {
            return 0;
        }
        return places.stream()
                .mapToDouble(p -> PlaceCompleteness.score(p, texts.getOrDefault(p.getId(), PlaceTexts.empty())))
                .average()
                .orElse(0);
    }
}
