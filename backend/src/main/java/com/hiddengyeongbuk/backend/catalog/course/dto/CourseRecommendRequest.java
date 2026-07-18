package com.hiddengyeongbuk.backend.catalog.course.dto;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.catalog.Interest;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CourseRecommendRequest(
        @NotNull(message = "hubId는 필수입니다") Long hubId,
        @NotNull(message = "duration은 필수입니다") Duration duration,
        List<Interest> interests,
        @NotNull(message = "difficulty는 필수입니다") Difficulty difficulty) {}
