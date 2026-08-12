package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.catalog.Interest;
import com.hiddengyeongbuk.backend.catalog.PlaceTexts;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
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
            Set<Interest> interests,
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

    /** 4.1 관심사 적합도: 코스가 커버한 사용자 관심사 종류 비율. */
    private double interestScore(List<Place> places, Set<Interest> interests) {
        if (interests.isEmpty()) {
            return 100.0;
        }
        Set<String> covered = places.stream()
                .flatMap(p -> p.getTags().stream().map(PlaceTag::getTag))
                .collect(Collectors.toSet());
        // place_tag가 enum 코드를 저장하므로 코드끼리 비교한다(구: 한글 라벨 문자열 일치).
        long matched = interests.stream().filter(i -> covered.contains(i.name())).count();
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
