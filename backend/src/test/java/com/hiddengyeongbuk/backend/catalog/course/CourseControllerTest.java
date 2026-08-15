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
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseListResponse;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseRecommendRequest;
import com.hiddengyeongbuk.backend.catalog.course.dto.CourseResponse;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import java.time.LocalDate;
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

    private static final LocalDate START = LocalDate.of(2026, 5, 1);
    private static final LocalDate END = LocalDate.of(2026, 5, 2);

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
                new CourseResponse.Breakdown(100, 80, 100, 75),
                1L,
                24.0,
                2,
                List.of(new CourseResponse.DayResponse(1, List.of(), null)));
        BDDMockito.given(courseRecommendationService.recommend(
                        eq(1L), any(), eq(START), eq(END), any(), eq(Difficulty.EASY), anyString()))
                .willReturn(new CourseListResponse(
                        List.of(course), new CourseListResponse.Constraints(7, 120.0, 1), "en"));

        CourseRecommendRequest request = new CourseRecommendRequest(
                1L, List.of(), START, END, List.of(PlaceType.HS, PlaceType.NA), Difficulty.EASY);

        mockMvc.perform(post("/api/courses/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].rank").value(1))
                .andExpect(jsonPath("$.courses[0].score").value(90.25))
                .andExpect(jsonPath("$.courses[0].breakdown.interest").value(100))
                .andExpect(jsonPath("$.courses[0].regionCount").value(2))
                // 적용된 제약과 실제 사용 언어를 함께 내려 클라이언트가 안내를 띄울 수 있게 한다.
                .andExpect(jsonPath("$.constraints.placeCount").value(7))
                .andExpect(jsonPath("$.constraints.maxDistanceKm").value(120.0))
                .andExpect(jsonPath("$.constraints.lodgingCount").value(1))
                .andExpect(jsonPath("$.resolvedLang").value("en"));
    }

    @Test
    void 체류_적합도는_응답에서_사라졌다() throws Exception {
        CourseResponse course = new CourseResponse(
                1,
                90.25,
                new CourseResponse.Breakdown(100, 80, 100, 75),
                1L,
                24.0,
                1,
                List.of());
        BDDMockito.given(courseRecommendationService.recommend(
                        any(), any(), any(), any(), any(), any(), anyString()))
                .willReturn(new CourseListResponse(
                        List.of(course), new CourseListResponse.Constraints(4, 60.0, 0), "en"));

        CourseRecommendRequest request =
                new CourseRecommendRequest(1L, null, START, START, List.of(), Difficulty.EASY);

        mockMvc.perform(post("/api/courses/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].breakdown.stay").doesNotExist());
    }

    @Test
    void 지역_다중선택은_정렬_중복제거되어_서비스로_넘어간다() throws Exception {
        // 순서만 다른 같은 요청이 다른 캐시 키가 되면 캐시가 조용히 무력화된다.
        BDDMockito.given(courseRecommendationService.recommend(
                        eq(1L), eq(List.of(2L, 5L, 9L)), any(), any(), any(), any(), anyString()))
                .willReturn(new CourseListResponse(List.of(), new CourseListResponse.Constraints(4, 60.0, 0), "en"));

        CourseRecommendRequest request = new CourseRecommendRequest(
                1L, List.of(9L, 2L, 5L, 2L), START, START, List.of(), Difficulty.EASY);

        mockMvc.perform(post("/api/courses/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses").isEmpty());
    }

    @Test
    void 시작일이_없으면_400이다() throws Exception {
        String body = """
                {"hubId":1,"endDate":"2026-05-02","interests":[],"difficulty":"EASY"}
                """;

        mockMvc.perform(post("/api/courses/recommend").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAM"));
    }

    @Test
    void 공유_URL_재현_요청은_동일_조건으로_무상태_재계산한다() throws Exception {
        BDDMockito.given(courseRecommendationService.recommend(
                        eq(1L),
                        eq(List.of(3L)),
                        eq(START),
                        eq(END),
                        eq(Set.of(PlaceType.HS, PlaceType.NA)),
                        eq(Difficulty.EASY),
                        anyString()))
                .willReturn(new CourseListResponse(List.of(), new CourseListResponse.Constraints(7, 120.0, 1), "en"));

        mockMvc.perform(get("/api/courses")
                        .param("hubId", "1")
                        .param("regionIds", "3")
                        .param("startDate", "2026-05-01")
                        .param("endDate", "2026-05-02")
                        .param("interests", "HS,NA")
                        .param("difficulty", "EASY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses").isEmpty())
                .andExpect(jsonPath("$.constraints.lodgingCount").value(1));
    }
}
