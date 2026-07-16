package com.hiddengyeongbuk.backend.ingest.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummary;
import com.hiddengyeongbuk.backend.domain.place.PlaceSummaryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class EnglishSummaryServiceTest {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceSummaryRepository placeSummaryRepository;

    private EnglishSummaryService service;

    @BeforeEach
    void setUp() {
        service = new EnglishSummaryService(geminiClient, placeRepository, placeSummaryRepository);
    }

    @Test
    void has_en이_true면_제미니를_부르지_않는다() {
        Place place = place(1L, true, "설명");

        EnglishSummaryService.SummaryResult result = service.ensureSummary(place);

        assertThat(result).isEqualTo(EnglishSummaryService.SummaryResult.SKIPPED_HAS_EN);
        verify(geminiClient, never()).generateSummary(anyString());
    }

    @Test
    void 이미_요약이_있으면_다시_생성하지_않는다() {
        Place place = place(1L, false, "설명");
        given(placeSummaryRepository.existsById(1L)).willReturn(true);

        EnglishSummaryService.SummaryResult result = service.ensureSummary(place);

        assertThat(result).isEqualTo(EnglishSummaryService.SummaryResult.ALREADY_EXISTS);
        verify(geminiClient, never()).generateSummary(anyString());
    }

    @Test
    void 국문_설명이_없으면_건너뛴다() {
        Place place = place(1L, false, null);
        given(placeSummaryRepository.existsById(1L)).willReturn(false);

        EnglishSummaryService.SummaryResult result = service.ensureSummary(place);

        assertThat(result).isEqualTo(EnglishSummaryService.SummaryResult.SKIPPED_NO_OVERVIEW);
        verify(geminiClient, never()).generateSummary(anyString());
    }

    @Test
    void 정상_응답이면_영문_요약을_저장한다() {
        Place place = place(1L, false, "안동 하회마을은 전통 한옥 마을이다");
        given(placeSummaryRepository.existsById(1L)).willReturn(false);
        given(geminiClient.generateSummary(anyString())).willReturn("Hahoe Village is a traditional hanok village.");

        EnglishSummaryService.SummaryResult result = service.ensureSummary(place);

        assertThat(result).isEqualTo(EnglishSummaryService.SummaryResult.GENERATED);
        verify(placeSummaryRepository, times(1)).save(any(PlaceSummary.class));
    }

    @Test
    void 제미니_호출이_실패하면_예외를_던지지_않고_실패로_처리한다() {
        Place place = place(1L, false, "설명");
        given(placeSummaryRepository.existsById(1L)).willReturn(false);
        given(geminiClient.generateSummary(anyString())).willThrow(new GeminiException("boom", null));

        EnglishSummaryService.SummaryResult result = service.ensureSummary(place);

        assertThat(result).isEqualTo(EnglishSummaryService.SummaryResult.FAILED);
        verify(placeSummaryRepository, never()).save(any());
    }

    @Test
    void 백필은_limit만큼만_생성하고_멈춘다() {
        Place p1 = place(1L, false, "설명1");
        Place p2 = place(2L, false, "설명2");
        Place p3 = place(3L, false, "설명3");
        given(placeRepository.findByHasEnFalse()).willReturn(List.of(p1, p2, p3));
        given(placeSummaryRepository.existsById(any())).willReturn(false);
        given(geminiClient.generateSummary(anyString())).willReturn("summary");

        EnglishSummaryService.BackfillResult result = service.backfill(2);

        assertThat(result.generated()).isEqualTo(2);
        verify(geminiClient, times(2)).generateSummary(anyString());
    }

    @Test
    void 전부_실패해도_limit_시도_횟수에서_멈춘다() {
        // 설정 오류 등으로 Gemini 호출이 전부 실패하는 상황 — limit이 "성공 개수"에만 걸리면
        // 남은 후보 전체를 계속 두드리게 된다. 시도 횟수 자체에 limit을 걸어 이를 막는다.
        Place p1 = place(1L, false, "설명1");
        Place p2 = place(2L, false, "설명2");
        Place p3 = place(3L, false, "설명3");
        given(placeRepository.findByHasEnFalse()).willReturn(List.of(p1, p2, p3));
        given(placeSummaryRepository.existsById(any())).willReturn(false);
        given(geminiClient.generateSummary(anyString())).willThrow(new GeminiException("boom", null));

        EnglishSummaryService.BackfillResult result = service.backfill(2);

        assertThat(result.generated()).isZero();
        assertThat(result.failed()).isEqualTo(2);
        verify(geminiClient, times(2)).generateSummary(anyString());
    }

    private static Place place(long id, boolean hasEn, String overviewKo) {
        return Place.builder()
                .id(id)
                .contentId("c" + id)
                .contentTypeId(12)
                .hasEn(hasEn)
                .overviewKo(overviewKo)
                .build();
    }
}
