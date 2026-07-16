package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.domain.place.Place;
import java.util.List;

/** 하루 일정: 방문 장소(음식점 포함) + 숙박(마지막 날 제외). */
record CourseDay(int day, List<Place> places, Place lodging) {}
