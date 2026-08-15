package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseListResponse;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseRecommendRequest;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 명세서 4장: 코스 추천(POST) / 공유 URL 재현(GET, 동일 조건 무상태 재계산). */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseRecommendationService courseRecommendationService;
    private final LanguageRegistry languages;

    public CourseController(CourseRecommendationService courseRecommendationService, LanguageRegistry languages) {
        this.courseRecommendationService = courseRecommendationService;
        this.languages = languages;
    }

    @PostMapping("/recommend")
    public CourseListResponse recommend(
            @Valid @RequestBody CourseRecommendRequest request, @RequestParam(required = false) String lang) {
        return courseRecommendationService.recommend(
                request.hubId(),
                normalizeRegionIds(request.regionIds()),
                request.startDate(),
                request.endDate(),
                toSet(request.interests()),
                request.difficulty(),
                languages.resolve(lang));
    }

    @GetMapping
    public CourseListResponse recompute(
            @RequestParam Long hubId,
            @RequestParam(required = false) List<Long> regionIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<PlaceType> interests,
            @RequestParam Difficulty difficulty,
            @RequestParam(required = false) String lang) {
        return courseRecommendationService.recommend(
                hubId,
                normalizeRegionIds(regionIds),
                startDate,
                endDate,
                toSet(interests),
                difficulty,
                languages.resolve(lang));
    }

    /**
     * 캐시 키 안정화. 순서만 다르거나 중복이 섞인 같은 요청이 서로 다른 키가 되면 캐시가 조용히
     * 무력화되므로, 서비스에 넘기기 전에 정렬·중복 제거한다.
     */
    private static List<Long> normalizeRegionIds(List<Long> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }
        SortedSet<Long> sorted = new TreeSet<>(regionIds);
        return List.copyOf(sorted);
    }

    private static Set<PlaceType> toSet(List<PlaceType> interests) {
        return interests == null ? Set.of() : Set.copyOf(interests);
    }
}
