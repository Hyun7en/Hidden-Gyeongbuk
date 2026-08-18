package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final int festivalBonus;

    CourseAssembler(CoursePlanningRules rules, CourseProperties properties) {
        this.rules = rules;
        this.festivalBonus = properties.festival().preScoreBonus();
    }

    enum SelectionStrategy {
        /** 사전점수 우선, 거리로 동점 처리 — 명세 문구를 그대로 따르는 기본 전략. */
        PRESCORE_FIRST,
        /** 최근접 우선, 사전점수로 동점 처리 — 이동거리를 더 아끼는 변형. */
        NEAREST_FIRST,
        /** 사전점수와 거리를 함께 반영하는 절충 전략. */
        BALANCED,
        /**
         * 목표 지역 수에 닿을 때까지 <b>아직 안 간 지역</b>을 우선하는 전략.
         *
         * <p>나머지 세 전략은 거리를 전부 <b>비용</b>으로만 다뤄서 거점 반경을 벗어날 이유가
         * 구조적으로 없다. 그래서 지역분산에 20점을 배정해 놓고도 분산된 후보 자체가 만들어지지
         * 않았다 — 실측(2026-08-18): 안동 거점 1박2일에 후보 전원이 지역 1곳, 총 13.1km.
         * 거리 예산을 120km에서 300km로 늘려도 13.1km 그대로였으니 예산 문제가 아니었다.
         *
         * <p>여기서 하는 일은 <b>후보를 하나 더 만드는 것뿐</b>이다. 이 전략의 코스가 실제로
         * 좋은지는 {@link CourseScorer}가 접근성(30)과 지역분산(20)을 함께 재서 정한다.
         */
        REGION_SPREAD,
        /**
         * 아직 <b>안 담은 관심사</b>를 우선하는 전략.
         *
         * <p>{@link #REGION_SPREAD}와 정확히 같은 종류의 구멍을 메운다. 관심사 적합도에 35점(네
         * 항목 중 최대)을 배정해 놓고도 여러 관심사를 고루 담으려는 장치가 없어, 필터를 통과한
         * 풀에서 가까운 순으로 집으면 한 분류가 몰려 있는 곳으로 쏠렸다 — 실측(2026-08-18):
         * HS+NA 요청에 관광 자리 5곳이 전부 HS라 후보 3개가 모두 관심 50점.
         *
         * <p>모든 관심사를 담고 나면 우대할 후보가 없어져 {@code PRESCORE_FIRST}와 같아진다.
         * 그래서 {@code REGION_SPREAD}와 달리 별도 상한이 필요 없다.
         *
         * <p>장소당 분류가 정확히 하나라 커버 가능한 관심사 수는 장소 수를 넘지 못한다(추천
         * 알고리즘 §4.1). 그 상한은 여기서 고칠 수 있는 게 아니다.
         */
        INTEREST_SPREAD
    }

    Optional<CourseCandidate> assemble(
            Hub hub,
            List<Place> attractionPool,
            List<Place> foodPool,
            List<Place> lodgingPool,
            TravelDates dates,
            Difficulty difficulty,
            Set<PlaceType> interests,
            Map<Long, FestivalPeriod> festivals,
            SelectionStrategy strategy) {
        int totalDays = dates.days();
        int[] perDayTargets = splitEvenly(rules.targetPlaceCount(totalDays), totalDays);
        double dmax = rules.maxDistanceKm(difficulty, totalDays);

        Set<Long> used = new HashSet<>();
        // 지역분산 점수의 목표치와 같은 기준을 쓴다 — 채점기가 재는 것과 다른 목표를 좇으면
        // 후보를 만들어도 점수에 반영되지 않는다.
        Set<Long> usedRegions = new HashSet<>();
        // 채점기가 재는 것과 같은 기준 — 끼니·숙박도 allPlaces()에 들어가므로 여기서도 함께 센다.
        Set<PlaceType> coveredInterests = new HashSet<>();
        int targetRegions = rules.targetRegionCount(totalDays, distinctRegions(attractionPool));
        List<CourseDay> courseDays = new ArrayList<>();
        double lat = hub.getLat().doubleValue();
        double lng = hub.getLng().doubleValue();
        double totalDistance = 0;

        for (int day = 1; day <= totalDays; day++) {
            List<Place> dayPlaces = new ArrayList<>();

            Place food = pickNext(foodPool, used, usedRegions, targetRegions, coveredInterests, lat, lng, interests, festivals, dates, strategy, dmax - totalDistance);
            if (food != null) {
                totalDistance += GeoUtils.haversineKm(lat, lng, food.getLat().doubleValue(), food.getLng().doubleValue());
                lat = food.getLat().doubleValue();
                lng = food.getLng().doubleValue();
                used.add(food.getId());
                usedRegions.add(food.getRegion().getId());
                coveredInterests.add(food.getPlaceType());
                dayPlaces.add(food);
            }

            int target = perDayTargets[day - 1];
            while (dayPlaces.size() < target) {
                Place next =
                        pickNext(attractionPool, used, usedRegions, targetRegions, coveredInterests, lat, lng, interests, festivals, dates, strategy, dmax - totalDistance);
                if (next == null) {
                    break;
                }
                totalDistance += GeoUtils.haversineKm(lat, lng, next.getLat().doubleValue(), next.getLng().doubleValue());
                lat = next.getLat().doubleValue();
                lng = next.getLng().doubleValue();
                used.add(next.getId());
                usedRegions.add(next.getRegion().getId());
                coveredInterests.add(next.getPlaceType());
                dayPlaces.add(next);
            }

            if (dayPlaces.isEmpty()) {
                break;
            }

            Place lodging = null;
            if (day < totalDays) {
                lodging = pickNext(lodgingPool, used, usedRegions, targetRegions, coveredInterests, lat, lng, interests, festivals, dates, strategy, dmax - totalDistance);
                if (lodging != null) {
                    totalDistance +=
                            GeoUtils.haversineKm(lat, lng, lodging.getLat().doubleValue(), lodging.getLng().doubleValue());
                    lat = lodging.getLat().doubleValue();
                    lng = lodging.getLng().doubleValue();
                    used.add(lodging.getId());
                    usedRegions.add(lodging.getRegion().getId());
                    coveredInterests.add(lodging.getPlaceType());
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
            Set<Long> usedRegions,
            int targetRegions,
            Set<PlaceType> coveredInterests,
            double lat,
            double lng,
            Set<PlaceType> interests,
            Map<Long, FestivalPeriod> festivals,
            TravelDates dates,
            SelectionStrategy strategy,
            double remainingBudget) {
        Place best = null;
        double bestKey = Double.NEGATIVE_INFINITY;
        for (Place candidate : pool) {
            if (used.contains(candidate.getId()) || candidate.getLat() == null || candidate.getLng() == null) {
                continue;
            }
            // 여행 기간에 안 열리는 축제는 후보에서 뺀다 — 점수를 깎는 게 아니라 제외다.
            // 닫힌 축제를 일정에 넣는 것은 선호의 문제가 아니라 틀린 정보다(추천 알고리즘 §4.7).
            FestivalPeriod festival = festivals.get(candidate.getId());
            if (festival != null && !festival.overlaps(dates)) {
                continue;
            }
            double distance =
                    GeoUtils.haversineKm(lat, lng, candidate.getLat().doubleValue(), candidate.getLng().doubleValue());
            if (distance > remainingBudget) {
                continue;
            }
            double preScore = preScore(candidate, interests) + (festival != null ? festivalBonus : 0);
            // 목표 지역 수를 채우기 전까지만 새 지역을 우대한다. 목표를 넘겨서까지 밀면 점수는
            // 안 오르는데 이동거리만 늘어 접근성(30점)을 깎는다.
            boolean opensNewRegion = usedRegions.size() < targetRegions
                    && !usedRegions.contains(candidate.getRegion().getId());
            // 고른 관심사 중 아직 코스에 없는 분류인가. 다 담고 나면 아무도 참이 아니게 돼
            // 이 전략은 자연히 PRESCORE_FIRST로 수렴한다.
            boolean opensNewInterest = interests.contains(candidate.getPlaceType())
                    && !coveredInterests.contains(candidate.getPlaceType());
            double key =
                    switch (strategy) {
                        case PRESCORE_FIRST -> preScore * 1000 - distance;
                        case NEAREST_FIRST -> -distance * 1000 + preScore;
                        case BALANCED -> preScore - 2 * distance;
                        case REGION_SPREAD -> (opensNewRegion ? 1_000_000 : 0) + preScore * 1000 - distance;
                        case INTEREST_SPREAD -> (opensNewInterest ? 1_000_000 : 0) + preScore * 1000 - distance;
                    };
            if (key > bestKey) {
                bestKey = key;
                best = candidate;
            }
        }
        return best;
    }

    /** 후보 풀이 실제로 걸쳐 있는 지역 수. 도달 불가능한 목표를 세우지 않기 위한 상한이다. */
    private static int distinctRegions(List<Place> pool) {
        return (int) pool.stream().map(p -> p.getRegion().getId()).distinct().count();
    }

    /**
     * 5장 1단계: 관심사 매치(70%) + 데이터 완성도(30%)로 산정하는 place 레벨 사전점수.
     *
     * <p>호출부에서 축제 가산이 더해지므로 결과가 100을 넘을 수 있다. 사용자에게 보여주는
     * 점수가 아니라 후보 정렬 키라 상한을 두지 않는다 — 응답의 {@code breakdown}과는 무관하다.
     */
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
