package com.hiddengyeongbuk.backend.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiddengyeongbuk.backend.catalog.dto.HubResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HubController.class)
@Import(com.hiddengyeongbuk.backend.support.LanguageRegistryTestConfig.class)
class HubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HubQueryService hubQueryService;

    @Test
    void 허브_목록은_언어별_필드가_아니라_해석된_단일_이름을_준다() throws Exception {
        // nameKo/nameEn을 나란히 내려주면 언어를 늘릴 때마다 응답 스키마가 바뀐다.
        HubResponse andong = new HubResponse(
                1L,
                "ANDONG",
                "Andong",
                "Tradition · Nature · Temples",
                new BigDecimal("36.5750"),
                new BigDecimal("128.6747"),
                "en");
        BDDMockito.given(hubQueryService.list(anyString())).willReturn(List.of(andong));

        mockMvc.perform(get("/api/hubs").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("ANDONG"))
                .andExpect(jsonPath("$[0].name").value("Andong"))
                .andExpect(jsonPath("$[0].resolvedLang").value("en"));
    }

    @Test
    void lang을_안_주면_기준_언어로_해석해_서비스에_넘긴다() throws Exception {
        BDDMockito.given(hubQueryService.list(anyString())).willReturn(List.of());

        mockMvc.perform(get("/api/hubs")).andExpect(status().isOk());

        BDDMockito.then(hubQueryService).should().list("ko");
    }
}
