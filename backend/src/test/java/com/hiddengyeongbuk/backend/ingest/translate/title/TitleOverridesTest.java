package com.hiddengyeongbuk.backend.ingest.translate.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 사람이 확정한 교정 적용(ADR-026 overrides 단계).
 *
 * <p>키가 <b>국문 {@code source_content_id}</b>인 것이 핵심이다. 내부 {@code place_id}로 걸면
 * 재판정 때 번호가 바뀌면서 교정이 엉뚱한 장소에 붙는다 — 2026-08-20에 실제로 place_id churn을
 * 겪었다.
 */
class TitleOverridesTest {

    @TempDir
    Path workDir;

    private final TitleCandidateLoader loader = Mockito.mock(TitleCandidateLoader.class);
    private final PlaceTranslationRepository translations = Mockito.mock(PlaceTranslationRepository.class);
    private final ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);

    private PlaceTranslation target;

    private TitleOverrides overrides() {
        return new TitleOverrides(loader, new TitleProperties(workDir.toString(), 200, 20, 0), translations, events);
    }

    private void givenPlace(String currentTitle) {
        target = PlaceTranslation.builder()
                .placeId(7L)
                .lang("en")
                .title(currentTitle)
                .source(TranslationSource.TRANSLATE)
                .build();
        BDDMockito.given(loader.load("en"))
                .willReturn(List.of(new TitleCandidate(7L, "126508", "AC", "숫골", null, null, target)));
    }

    private void givenOverrideFile(String yaml) throws IOException {
        Files.writeString(workDir.resolve("title-overrides-en.yml"), yaml);
    }

    @Test
    void 교정을_contentId로_찾아_적용한다() throws IOException {
        givenPlace("Male bone");
        givenOverrideFile("""
                titles:
                  "126508":
                    ko: "숫골"
                    en: "Sutgol"
                    reason: "proper_name"
                """);

        TitleOverrides.OverrideResult result = overrides().apply("en");

        assertThat(target.getTitle()).isEqualTo("Sutgol");
        assertThat(result.applied()).isEqualTo(1);
        BDDMockito.then(translations).should().save(target);
        BDDMockito.then(events).should().publishEvent(any(PlaceDataChangedEvent.class));
    }

    @Test
    void 따옴표_없는_숫자키도_읽는다() throws IOException {
        // YAML은 따옴표 없는 숫자를 Integer로 읽는다. 문자열로 맞추지 않으면 조용히 안 먹는다.
        givenPlace("Male bone");
        givenOverrideFile("""
                titles:
                  126508:
                    en: "Sutgol"
                """);

        assertThat(overrides().apply("en").applied()).isEqualTo(1);
        assertThat(target.getTitle()).isEqualTo("Sutgol");
    }

    @Test
    void 이미_같은_값이면_저장하지_않는다() throws IOException {
        // 재실행이 공짜여야 한다 — 두 번째 실행은 바꿀 것이 없다고만 보고한다.
        givenPlace("Sutgol");
        givenOverrideFile("""
                titles:
                  "126508":
                    en: "Sutgol"
                """);

        TitleOverrides.OverrideResult result = overrides().apply("en");

        assertThat(result.alreadyCorrect()).isEqualTo(1);
        assertThat(result.applied()).isZero();
        BDDMockito.then(translations).should(Mockito.never()).save(any());
        BDDMockito.then(events).should(Mockito.never()).publishEvent(any(PlaceDataChangedEvent.class));
    }

    @Test
    void 대상을_찾지_못한_키는_조용히_넘기지_않고_보고한다() throws IOException {
        // 오타나 사라진 장소다. 조용히 넘기면 교정이 안 먹은 것을 아무도 모른다.
        givenPlace("Male bone");
        givenOverrideFile("""
                titles:
                  "999999":
                    en: "Nowhere"
                """);

        TitleOverrides.OverrideResult result = overrides().apply("en");

        assertThat(result.unmatched()).containsExactly("999999");
        assertThat(result.applied()).isZero();
        assertThat(target.getTitle()).isEqualTo("Male bone");
    }

    @Test
    void 교정_파일이_없으면_아무것도_하지_않는다() {
        // 교정이 아직 없는 것이 정상 상태다 — 없다고 실패하면 안 된다.
        givenPlace("Male bone");

        TitleOverrides.OverrideResult result = overrides().apply("en");

        assertThat(result.defined()).isZero();
        assertThat(result.applied()).isZero();
        BDDMockito.then(translations).should(Mockito.never()).save(any());
    }
}
