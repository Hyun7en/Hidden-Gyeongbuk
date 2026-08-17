package com.hiddengyeongbuk.backend.ingest.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.domain.sync.SyncStatus;
import com.hiddengyeongbuk.backend.ingest.Collector;
import com.hiddengyeongbuk.backend.ingest.Merger;
import com.hiddengyeongbuk.backend.ingest.Resolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private Collector collector;

    @MockitoBean
    private Resolver resolver;

    @MockitoBean
    private Merger merger;

    @MockitoBean
    private SyncStatusService syncStatusService;

    @Test
    void 상태_조회는_단계별_연속_실패와_데이터_현황을_함께_준다() throws Exception {
        BDDMockito.given(syncStatusService.status())
                .willReturn(new SyncStatusResponse(
                        List.of(new SyncStatusResponse.PhaseStatus(
                                SyncPhase.COLLECT,
                                "en",
                                SyncStatus.FAIL,
                                LocalDateTime.of(2026, 8, 14, 3, 0),
                                LocalDateTime.of(2026, 8, 14, 3, 5),
                                0,
                                "timeout",
                                2)),
                        new SyncStatusResponse.DataCounts(
                                Map.of("QUARANTINED", 44L), Map.of("en", 297L), 525L, Map.of("en", 251L))));

        mockMvc.perform(get("/internal/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phases[0].lang").value("en"))
                .andExpect(jsonPath("$.phases[0].consecutiveFailures").value(2))
                .andExpect(jsonPath("$.data.stagingByStatus.QUARANTINED").value(44))
                .andExpect(jsonPath("$.data.places").value(525));
    }

    @Test
    void 언어_미지정_수집은_활성_언어_전체를_돈다() throws Exception {
        BDDMockito.given(collector.collectAll())
                .willReturn(List.of(new Collector.Result("ko", 10, 2, 1, 0, 0), new Collector.Result("en", 7, 0, 3, 1, 0)));

        mockMvc.perform(post("/internal/sync/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lang").value("ko"))
                .andExpect(jsonPath("$[1].lang").value("en"));

        BDDMockito.then(collector).should().collectAll();
    }

    @Test
    void 언어를_지정하면_그_언어만_수집한다() throws Exception {
        BDDMockito.given(collector.collectLanguage("en")).willReturn(new Collector.Result("en", 5, 0, 0, 0, 0));

        mockMvc.perform(post("/internal/sync/collect").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].upserted").value(5));

        BDDMockito.then(collector).should(BDDMockito.never()).collectAll();
    }

    @Test
    void 판정은_외부_호출_없이_재실행할_수_있다() throws Exception {
        BDDMockito.given(resolver.resolve()).willReturn(new Resolver.Result(12, 3, 9));

        mockMvc.perform(post("/internal/sync/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(12))
                .andExpect(jsonPath("$.quarantined").value(3));

        // resetAll을 안 줬으면 기존 판정을 유지한다.
        BDDMockito.then(resolver).should(BDDMockito.never()).resetAllToPending();
    }

    @Test
    void resetAll이면_전면_재판정한다() throws Exception {
        BDDMockito.given(resolver.resolve()).willReturn(new Resolver.Result(20, 0, 20));

        mockMvc.perform(post("/internal/sync/resolve").param("resetAll", "true"))
                .andExpect(status().isOk());

        BDDMockito.then(resolver).should().resetAllToPending();
    }

    @Test
    void 병합_결과를_반환한다() throws Exception {
        BDDMockito.given(merger.merge()).willReturn(new Merger.Result(9, 15, 0, 0, 0));

        mockMvc.perform(post("/internal/sync/merge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places").value(9))
                .andExpect(jsonPath("$.translations").value(15));
    }
}
