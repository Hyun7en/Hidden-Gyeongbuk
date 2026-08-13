package com.hiddengyeongbuk.backend.catalog.course;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiddengyeongbuk.backend.catalog.Difficulty;
import com.hiddengyeongbuk.backend.catalog.Duration;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseListResponse;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseRecommendRequest;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseController.class)
@org.springframework.context.annotation.Import(com.hiddengyeongbuk.backend.support.LanguageRegistryTestConfig.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourseRecommendationService courseRecommendationService;

    @Test
    void 코스_추천_요청을_처리하고_상위_코스_목록을_반환한다() throws Exception {
        CourseResponse course = new CourseResponse(
                1,
                90.25,
                new CourseResponse.Breakdown(100, 80, 100, 75, 90),
                1L,
                24.0,
                List.of(new CourseResponse.DayResponse(1, List.of(), null)));
        BDDMockito.given(courseRecommendationService.recommend(
                        eq(1L), eq(Duration.ONE_NIGHT), any(), eq(Difficulty.EASY), anyString()))
                .willReturn(new CourseListResponse(List.of(course)));

        CourseRecommendRequest request =
                new CourseRecommendRequest(1L, Duration.ONE_NIGHT, List.of(PlaceType.HS, PlaceType.NA), Difficulty.EASY);

        mockMvc.perform(post("/api/courses/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].rank").value(1))
                .andExpect(jsonPath("$.courses[0].score").value(90.25))
                .andExpect(jsonPath("$.courses[0].breakdown.interest").value(100));
    }

    @Test
    void 공유_URL_재현_요청은_동일_조건으로_무상태_재계산한다() throws Exception {
        BDDMockito.given(courseRecommendationService.recommend(
                        eq(1L), eq(Duration.ONE_NIGHT), eq(Set.of(PlaceType.HS, PlaceType.NA)), eq(Difficulty.EASY), anyString()))
                .willReturn(new CourseListResponse(List.of()));

        mockMvc.perform(get("/api/courses")
                        .param("hubId", "1")
                        .param("duration", "ONE_NIGHT")
                        .param("interests", "HS,NA")
                        .param("difficulty", "EASY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses").isEmpty());
    }
}
