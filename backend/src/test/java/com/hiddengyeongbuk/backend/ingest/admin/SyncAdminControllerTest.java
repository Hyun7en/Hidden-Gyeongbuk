package com.hiddengyeongbuk.backend.ingest.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import com.hiddengyeongbuk.backend.ingest.PlaceCollectionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 컨트롤러가 @Profile("local") 빈이라 프로파일을 명시해야 컨텍스트에 뜬다.
@WebMvcTest(SyncAdminController.class)
@ActiveProfiles("local")
class SyncAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionRepository regionRepository;

    @MockitoBean
    private PlaceCollectionService placeCollectionService;

    @Test
    void 지역_목록을_반환한다() throws Exception {
        Region andong = Region.builder()
                .id(1L)
                .nameKo("안동")
                .ldongRegnCd("47")
                .ldongSignguCd("170")
                .build();
        BDDMockito.given(regionRepository.findAll()).willReturn(List.of(andong));

        mockMvc.perform(get("/internal/sync/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nameKo").value("안동"));
    }

    @Test
    void 존재하는_지역이면_수집_결과를_반환한다() throws Exception {
        Region andong = Region.builder()
                .id(1L)
                .nameKo("안동")
                .ldongRegnCd("47")
                .ldongSignguCd("170")
                .build();
        BDDMockito.given(regionRepository.findById(1L)).willReturn(Optional.of(andong));
        BDDMockito.given(placeCollectionService.collectRegionIncremental(andong))
                .willReturn(new PlaceCollectionService.CollectionResult(3, 1, 0, 0, SyncStatus.SUCCESS));

        mockMvc.perform(post("/internal/sync/regions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(3))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void 존재하지_않는_지역이면_404를_반환한다() throws Exception {
        BDDMockito.given(regionRepository.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(post("/internal/sync/regions/999"))
                .andExpect(status().isNotFound());
    }
}
