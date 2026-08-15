package com.hiddengyeongbuk.backend.catalog.course.dto;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 코스 추천 요청(API 명세서 §4.1).
 *
 * <p>{@code duration} enum은 없다 — 날짜 범위로 바뀌었다(ADR-020). 박 수·일 수·계절은 날짜에서
 * 파생하므로 따로 받지 않는다.
 *
 * @param regionIds 추천 범위(ADR-019). 비었거나 없으면 거점 주변 전체, 하나면 그 지역 집중,
 *     여럿이면 그 지역들로 한정. <b>거점의 소속 지역으로 제한하지 않는다</b> — 거점은 출발점일
 *     뿐이고 지역 선택은 독립적인 범위 지정이다.
 */
public record CourseRecommendRequest(
        @NotNull(message = "hubId는 필수입니다") Long hubId,
        List<Long> regionIds,
        @NotNull(message = "startDate는 필수입니다") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @NotNull(message = "endDate는 필수입니다") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        List<PlaceType> interests,
        @NotNull(message = "difficulty는 필수입니다") Difficulty difficulty) {}
