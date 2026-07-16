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

    Set<Long> placeIdSet() {
        return allPlaces().stream().map(Place::getId).collect(Collectors.toSet());
    }
}
