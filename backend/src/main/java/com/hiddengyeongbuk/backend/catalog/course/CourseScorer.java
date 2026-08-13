package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.catalog.PlaceTexts;
import com.hiddengyeongbuk.backend.domain.place.Place;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 추천 알고리즘 명세서 4장: 관심사·접근성·체류·지역분산·데이터완성도 5개 항목을 가중 합산한다. */
@Component
class CourseScorer {

    record Breakdown(double interest, double access, double stay, double dispersion, double completeness, double total) {}

    Breakdown score(
            CourseCandidate candidate,
            Set<PlaceType> interests,
            Duration duration,
            Difficulty difficulty,
            Map<Long, PlaceTexts> texts) {
        List<Place> allPlaces = candidate.allPlaces();
        double interest = interestScore(allPlaces, interests);
        double access = accessScore(candidate.totalDistanceKm(), duration, difficulty);
        double stay = stayScore(candidate, duration);
        double dispersion = dispersionScore(allPlaces, duration);
        double completeness = completenessScore(allPlaces, texts);
        double total = 0.30 * interest + 0.25 * access + 0.20 * stay + 0.15 * dispersion + 0.10 * completeness;
        return new Breakdown(interest, access, stay, dispersion, completeness, total);
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
    private double accessScore(double totalDistanceKm, Duration duration, Difficulty difficulty) {
        double dmax = CoursePlanningRules.dmax(difficulty, duration);
        return Math.max(0, 1 - totalDistanceKm / dmax) * 100;
    }

    /** 4.3 체류 적합도: 목표 장소 수 범위 이탈·필요 숙박 누락에 대한 감점. */
    private double stayScore(CourseCandidate candidate, Duration duration) {
        int n = candidate.visitPlaceCount();
        int min = CoursePlanningRules.minPlaces(duration);
        int max = CoursePlanningRules.maxPlaces(duration);
        int deviation = n < min ? min - n : Math.max(0, n - max);
        boolean lodgingMissing = candidate.lodgingCount() < CoursePlanningRules.requiredLodging(duration);
        double score = 100 - 15.0 * deviation - (lodgingMissing ? 40 : 0);
        return clamp(score);
    }

    /** 4.4 지역분산 기여도: 방문 지역 다양성. Rmax=1인 당일 코스는 항상 만점. */
    private double dispersionScore(List<Place> places, Duration duration) {
        int rmax = CoursePlanningRules.targetRegionCount(duration);
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

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
