package com.hiddengyeongbuk.backend.catalog.course.dto;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.catalog.Interest;
import java.util.List;

public record CourseRecommendRequest(Long hubId, Duration duration, List<Interest> interests, Difficulty difficulty) {}
