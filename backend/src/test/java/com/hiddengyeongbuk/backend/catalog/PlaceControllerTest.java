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
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// LanguageRegistry는 목이 아니라 실제 빈을 쓴다 — 폴백/정규화가 컨트롤러 동작의 일부라
// 목으로 대체하면 정작 검증하려는 lang 처리가 사라진다.
@WebMvcTest(PlaceController.class)
@Import(com.hiddengyeongbuk.backend.support.LanguageRegistryTestConfig.class)
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceQueryService placeQueryService;

    @Test
    void 검색_결과를_반환한다() throws Exception {
        PlaceSummaryResponse summary = new PlaceSummaryResponse(
                1024L, "ATTRACTION", "Hahoe Folk Village", "Andong-si", null, null, null, List.of("TRADITION"));
        BDDMockito.given(placeQueryService.search(
                        isNull(), eq(PlaceType.ATTRACTION), isNull(), isNull(), anyString(), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(summary), 0, 20, 1, "en"));

        mockMvc.perform(get("/api/places").param("placeType", "ATTRACTION").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Hahoe Folk Village"))
                .andExpect(jsonPath("$.content[0].placeType").value("ATTRACTION"))
                .andExpect(jsonPath("$.content[0].tags[0]").value("TRADITION"))
                .andExpect(jsonPath("$.resolvedLang").value("en"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 존재하지_않는_장소는_404와_표준_에러_형식을_반환한다() throws Exception {
        BDDMockito.given(placeQueryService.getDetail(eq(999L), anyString()))
                .willThrow(new PlaceNotFoundException(999L));

        mockMvc.perform(get("/api/places/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 잘못된_placeType은_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/places").param("placeType", "존재하지않는타입"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAM"));
    }

    @Test
    void 장소_상세는_현지_표기_주소를_함께_준다() throws Exception {
        // addrLocal은 요청 언어와 무관하게 항상 국문이다 — 택시 기사에게 보여주는 용도라
        // 번역된 주소만으로는 실제 이동이 불가능하기 때문이다(User Persona §4-1).
        PlaceDetailResponse detail = new PlaceDetailResponse(
                1024L,
                "ATTRACTION",
                11L,
                "Hahoe Folk Village",
                "40 Hahoejongga-gil, Andong-si",
                "경상북도 안동시 풍천면 하회종가길 40",
                "054-000-0000",
                null,
                null,
                "A traditional village",
                null,
                List.of(),
                List.of("TRADITION"),
                "en",
                "OFFICIAL",
                null);
        BDDMockito.given(placeQueryService.getDetail(eq(1024L), anyString())).willReturn(detail);

        mockMvc.perform(get("/api/places/1024").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hahoe Folk Village"))
                .andExpect(jsonPath("$.addrLocal").value("경상북도 안동시 풍천면 하회종가길 40"))
                .andExpect(jsonPath("$.resolvedLang").value("en"))
                .andExpect(jsonPath("$.translationSource").value("OFFICIAL"));
    }

    @Test
    void 지원하지_않는_언어는_에러가_아니라_기준_언어로_폴백한다() throws Exception {
        // API 명세서 §1.1 — 지원하지 않는 언어도 에러가 아니다. resolvedLang으로 실제 언어를 알린다.
        BDDMockito.given(placeQueryService.search(
                        isNull(), isNull(), isNull(), isNull(), eq(LanguageRegistry.BASE), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, LanguageRegistry.BASE));

        mockMvc.perform(get("/api/places").param("lang", "xx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedLang").value(LanguageRegistry.BASE));
    }
}
