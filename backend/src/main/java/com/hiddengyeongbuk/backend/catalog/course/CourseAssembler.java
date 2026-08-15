package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 추천 알고리즘 명세서 5장 코스 조립 규칙: Hub 기점 → 거리 예산(Dmax) 내에서 사전점수(관심사 매치 +
 * 데이터 완성도) 높은 place를 근접 순으로 선택(nearest-neighbor)한다. {@link SelectionStrategy}로
 * 우선순위를 달리해 서로 다른 조합의 후보 코스 K개를 만든다("5장 6단계").
 */
@Component
class CourseAssembler {

    private final CoursePlanningRules rules;

    CourseAssembler(CoursePlanningRules rules) {
        this.rules = rules;
    }

    enum SelectionStrategy {
        /** 사전점수 우선, 거리로 동점 처리 — 명세 문구를 그대로 따르는 기본 전략. */
        PRESCORE_FIRST,
        /** 최근접 우선, 사전점수로 동점 처리 — 이동거리를 더 아끼는 변형. */
        NEAREST_FIRST,
        /** 사전점수와 거리를 함께 반영하는 절충 전략. */
        BALANCED
    }

    Optional<CourseCandidate> assemble(
            Hub hub,
            List<Place> attractionPool,
            List<Place> foodPool,
            List<Place> lodgingPool,
            TravelDates dates,
            Difficulty difficulty,
            Set<PlaceType> interests,
            SelectionStrategy strategy) {
        int totalDays = dates.days();
        int[] perDayTargets = splitEvenly(rules.targetPlaceCount(totalDays), totalDays);
        double dmax = rules.maxDistanceKm(difficulty, totalDays);

        Set<Long> used = new HashSet<>();
        List<CourseDay> courseDays = new ArrayList<>();
        double lat = hub.getLat().doubleValue();
        double lng = hub.getLng().doubleValue();
        double totalDistance = 0;

        for (int day = 1; day <= totalDays; day++) {
            List<Place> dayPlaces = new ArrayList<>();

            Place food = pickNext(foodPool, used, lat, lng, interests, strategy, dmax - totalDistance);
            if (food != null) {
                totalDistance += GeoUtils.haversineKm(lat, lng, food.getLat().doubleValue(), food.getLng().doubleValue());
                lat = food.getLat().doubleValue();
                lng = food.getLng().doubleValue();
                used.add(food.getId());
                dayPlaces.add(food);
            }

            int target = perDayTargets[day - 1];
            while (dayPlaces.size() < target) {
                Place next = pickNext(attractionPool, used, lat, lng, interests, strategy, dmax - totalDistance);
                if (next == null) {
                    break;
                }
                totalDistance += GeoUtils.haversineKm(lat, lng, next.getLat().doubleValue(), next.getLng().doubleValue());
                lat = next.getLat().doubleValue();
                lng = next.getLng().doubleValue();
                used.add(next.getId());
                dayPlaces.add(next);
            }

            if (dayPlaces.isEmpty()) {
                break;
            }

            Place lodging = null;
            if (day < totalDays) {
                lodging = pickNext(lodgingPool, used, lat, lng, interests, strategy, dmax - totalDistance);
                if (lodging != null) {
                    totalDistance +=
                            GeoUtils.haversineKm(lat, lng, lodging.getLat().doubleValue(), lodging.getLng().doubleValue());
                    lat = lodging.getLat().doubleValue();
                    lng = lodging.getLng().doubleValue();
                    used.add(lodging.getId());
                }
            }

            courseDays.add(new CourseDay(day, List.copyOf(dayPlaces), lodging));
        }

        if (courseDays.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CourseCandidate(courseDays, totalDistance));
    }

    private Place pickNext(
            List<Place> pool,
            Set<Long> used,
            double lat,
            double lng,
            Set<PlaceType> interests,
            SelectionStrategy strategy,
            double remainingBudget) {
        Place best = null;
        double bestKey = Double.NEGATIVE_INFINITY;
        for (Place candidate : pool) {
            if (used.contains(candidate.getId()) || candidate.getLat() == null || candidate.getLng() == null) {
                continue;
            }
            double distance =
                    GeoUtils.haversineKm(lat, lng, candidate.getLat().doubleValue(), candidate.getLng().doubleValue());
            if (distance > remainingBudget) {
                continue;
            }
            double preScore = preScore(candidate, interests);
            double key =
                    switch (strategy) {
                        case PRESCORE_FIRST -> preScore * 1000 - distance;
                        case NEAREST_FIRST -> -distance * 1000 + preScore;
                        case BALANCED -> preScore - 2 * distance;
                    };
            if (key > bestKey) {
                bestKey = key;
                best = candidate;
            }
        }
        return best;
    }

    /** 5장 1단계: 관심사 매치(70%) + 데이터 완성도(30%)로 산정하는 place 레벨 사전점수. */
    private double preScore(Place place, Set<PlaceType> interests) {
        double interestScore;
        if (interests.isEmpty()) {
            interestScore = 50;
        } else {
            // 장소 하나의 분류는 정확히 하나라, 사전점수는 "고른 관심사 안에 드는가"의
            // 0/1 판정이 된다(구 태그 방식은 여러 개가 걸릴 수 있었다).
            interestScore = interests.contains(place.getPlaceType()) ? 100.0 : 0.0;
        }
        return 0.7 * interestScore + 0.3 * cheapCompleteness(place);
    }

    /**
     * 후보 선별용 완성도 <b>근사치</b>. 진짜 완성도({@link PlaceCompleteness})는 요청 언어의
     * 번역 행 존재까지 보지만(ADR-017), 그러려면 후보가 아니라 <b>풀 전체</b>의 번역을 읽어야
     * 한다 — 조립 전이라 아직 후보가 정해지지 않았기 때문이다. 수천 건을 매 요청 읽는 비용이
     * 선별 정확도 이득보다 커서, 여기서는 place만으로 판단할 수 있는 두 필드로 근사한다.
     * 최종 점수는 조립 후 실제 번역을 읽어 제대로 계산한다.
     */
    private static double cheapCompleteness(Place place) {
        int present = 0;
        if (place.getFirstImage() != null && !place.getFirstImage().isBlank()) {
            present++;
        }
        if (place.getLat() != null && place.getLng() != null) {
            present++;
        }
        return present * 100.0 / 2;
    }

    private static int[] splitEvenly(int total, int buckets) {
        int[] result = new int[buckets];
        int base = total / buckets;
        int remainder = total % buckets;
        for (int i = 0; i < buckets; i++) {
            result[i] = base + (i < remainder ? 1 : 0);
        }
        return result;
    }
}
