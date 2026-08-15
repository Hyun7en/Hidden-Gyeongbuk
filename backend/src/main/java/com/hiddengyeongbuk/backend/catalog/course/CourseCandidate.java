package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.domain.place.Place;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 조립된 코스 후보 하나. 4장 점수 산정의 입력이 되는 P = {p1..pn}은 {@link #allPlaces()}가 제공한다. */
record CourseCandidate(List<CourseDay> days, double totalDistanceKm) {

    List<Place> allPlaces() {
        List<Place> all = new ArrayList<>();
        for (CourseDay day : days) {
            all.addAll(day.places());
            if (day.lodging() != null) {
                all.add(day.lodging());
            }
        }
        return all;
    }

    int visitPlaceCount() {
        return days.stream().mapToInt(day -> day.places().size()).sum();
    }

    int lodgingCount() {
        return (int) days.stream().filter(day -> day.lodging() != null).count();
    }

    /** 코스가 실제로 걸치는 지역 수. 응답에 그대로 나가고 지역분산 점수의 근거이기도 하다. */
    int regionCount() {
        return (int) allPlaces().stream()
                .map(p -> p.getRegion().getId())
                .distinct()
                .count();
    }

    Set<Long> placeIdSet() {
        return allPlaces().stream().map(Place::getId).collect(Collectors.toSet());
    }
}
