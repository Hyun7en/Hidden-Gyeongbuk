package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.catalog.Interest;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseListResponse;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseRecommendRequest;
import java.util.List;
import java.util.Set;
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

    public CourseController(CourseRecommendationService courseRecommendationService) {
        this.courseRecommendationService = courseRecommendationService;
    }

    @PostMapping("/recommend")
    public CourseListResponse recommend(
            @RequestBody CourseRecommendRequest request, @RequestParam(defaultValue = "en") String lang) {
        return courseRecommendationService.recommend(
                request.hubId(), request.duration(), toSet(request.interests()), request.difficulty(), lang);
    }

    @GetMapping
    public CourseListResponse recompute(
            @RequestParam Long hubId,
            @RequestParam Duration duration,
            @RequestParam(required = false) List<Interest> interests,
            @RequestParam Difficulty difficulty,
            @RequestParam(defaultValue = "en") String lang) {
        return courseRecommendationService.recommend(hubId, duration, toSet(interests), difficulty, lang);
    }

    private static Set<Interest> toSet(List<Interest> interests) {
        return interests == null ? Set.of() : Set.copyOf(interests);
    }
}
