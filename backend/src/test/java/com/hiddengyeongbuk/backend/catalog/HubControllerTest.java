package com.hiddengyeongbuk.backend.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HubController.class)
class HubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HubRepository hubRepository;

    @Test
    void 허브_목록을_반환한다() throws Exception {
        Hub andong = Hub.builder()
                .id(1L)
                .nameKo("안동")
                .nameEn("Andong")
                .lat(new BigDecimal("36.5750"))
                .lng(new BigDecimal("128.6747"))
                .description("전통·자연·사찰")
                .build();
        BDDMockito.given(hubRepository.findAll()).willReturn(List.of(andong));

        mockMvc.perform(get("/api/hubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nameKo").value("안동"))
                .andExpect(jsonPath("$[0].nameEn").value("Andong"));
    }
}
