package com.hiddengyeongbuk.backend.catalog;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiddengyeongbuk.backend.catalog.dto.PageResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.PlaceSummaryResponse;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceController.class)
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceQueryService placeQueryService;

    @Test
    void 검색_결과를_반환한다() throws Exception {
        PlaceSummaryResponse summary =
                new PlaceSummaryResponse(1024L, "관광지", "Hahoe Folk Village", "Andong-si", null, null, null, List.of("전통"));
        BDDMockito.given(placeQueryService.search(
                        isNull(), eq(PlaceCategory.관광지), isNull(), isNull(), anyString(), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(summary), 0, 20, 1));

        mockMvc.perform(get("/api/places").param("category", "관광지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Hahoe Folk Village"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 존재하지_않는_장소는_404와_표준_에러_형식을_반환한다() throws Exception {
        BDDMockito.given(placeQueryService.getDetail(999L, "en")).willThrow(new PlaceNotFoundException(999L));

        mockMvc.perform(get("/api/places/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 잘못된_카테고리는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/places").param("category", "존재하지않는카테고리"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAM"));
    }

    @Test
    void 장소_상세를_반환한다() throws Exception {
        PlaceDetailResponse detail = new PlaceDetailResponse(
                1024L, "126508", "관광지", "Hahoe Folk Village", "하회마을", "Andong-si", "054-000-0000",
                null, null, "A traditional village", null, List.of(), List.of("전통"), null, null);
        BDDMockito.given(placeQueryService.getDetail(1024L, "en")).willReturn(detail);

        mockMvc.perform(get("/api/places/1024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hahoe Folk Village"));
    }
}
