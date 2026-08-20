package com.hiddengyeongbuk.backend.ingest.translate.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import com.hiddengyeongbuk.backend.support.llm.GeminiClient;
import com.hiddengyeongbuk.backend.support.llm.GeminiException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 제목 localization의 안전장치(ADR-026).
 *
 * <p>지키는 것은 둘이다.
 *
 * <ul>
 *   <li><b>어떤 실패도 기존 제목을 지우지 않는다.</b> 이미 화면에 나가는 제목을 덮어쓰는
 *       작업이라, 실패가 "빈 제목"으로 떨어지면 39만 자를 써서 만든 데이터를 잃는다.
 *   <li><b>실패한 건에는 {@code title_localized_at}을 찍지 않는다.</b> 찍어 버리면 다음 실행이
 *       건너뛰어 실패가 영구화된다.
 * </ul>
 *
 * <p>무료 티어가 분당 5회라 여러 건을 묶어 보낸다. 묶으면 <b>모델이 순서를 바꾸거나 일부를
 * 빠뜨릴 수 있어</b> 응답을 id로 되짚는지도 확인한다.
 */
class TitleLocalizerTest {

    private static final String SUTGOL = "126508";
    private static final String ANUK = "126509";

    private final TitleCandidateLoader loader = Mockito.mock(TitleCandidateLoader.class);
    private final GeminiClient gemini = Mockito.mock(GeminiClient.class);
    private final PlaceTranslationRepository translations = Mockito.mock(PlaceTranslationRepository.class);
    private final ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);

    // pacing 0 — 테스트가 13초씩 잘 이유가 없다.
    private final TitleProperties properties = new TitleProperties("build/tmp/title-test", 200, 20, 0);

    private final TitleLocalizer localizer = new TitleLocalizer(
            loader, properties, gemini, new TitleBatchParser(new ObjectMapper()), translations, events);

    private final List<PlaceTranslation> targets = new ArrayList<>();

    /** 국문 제목이 뜻으로 풀려 버린 상태 — 이 작업이 고치려는 바로 그 모습이다. */
    private void givenPlaces(String... contentIds) {
        List<TitleCandidate> candidates = new ArrayList<>();
        long placeId = 7L;
        for (String contentId : contentIds) {
            PlaceTranslation target = PlaceTranslation.builder()
                    .placeId(placeId)
                    .lang("en")
                    .title(SUTGOL.equals(contentId) ? "Male bone" : "Cozy")
                    .source(TranslationSource.TRANSLATE)
                    .build();
            targets.add(target);
            candidates.add(new TitleCandidate(
                    placeId++,
                    contentId,
                    "AC",
                    SUTGOL.equals(contentId) ? "숫골" : "아늑",
                    "설명글",
                    "경북 안동시",
                    target));
        }
        BDDMockito.given(loader.load("en")).willReturn(candidates);
    }

    private void answers(String json) {
        BDDMockito.given(gemini.generate(any())).willReturn(json);
    }

    @Test
    void 고유명사를_로마자로_바꾸고_처리_표시를_찍는다() {
        givenPlaces(SUTGOL);
        answers("""
                [{"id":"126508","title":"Sutgol"}]
                """);

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(targets.get(0).getTitle()).isEqualTo("Sutgol");
        assertThat(targets.get(0).getTitleLocalizedAt()).isNotNull();
        assertThat(result.localized()).isEqualTo(1);
        BDDMockito.then(translations).should().save(targets.get(0));
        BDDMockito.then(events).should().publishEvent(any(PlaceDataChangedEvent.class));
    }

    @Test
    void 한_번의_호출로_여러_건을_처리한다() {
        // 무료 티어 분당 5회라 이게 성립해야 1,105건이 12분에 끝난다.
        givenPlaces(SUTGOL, ANUK);
        answers("""
                [{"id":"126508","title":"Sutgol"},{"id":"126509","title":"Anuk"}]
                """);

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(result.localized()).isEqualTo(2);
        assertThat(targets).extracting(PlaceTranslation::getTitle).containsExactly("Sutgol", "Anuk");
        BDDMockito.then(gemini).should(Mockito.times(1)).generate(any());
    }

    @Test
    void 순서가_바뀌어_와도_id로_되짚는다() {
        // 순서에만 의존하면 한 건이 밀릴 때 전부 어긋나 엉뚱한 장소에 이름이 붙는다.
        givenPlaces(SUTGOL, ANUK);
        answers("""
                [{"id":"126509","title":"Anuk"},{"id":"126508","title":"Sutgol"}]
                """);

        localizer.localize("en", 10, false);

        assertThat(targets).extracting(PlaceTranslation::getTitle).containsExactly("Sutgol", "Anuk");
    }

    @Test
    void 응답에서_빠진_건은_기존_제목을_유지하고_표시도_안_찍는다() {
        givenPlaces(SUTGOL, ANUK);
        answers("""
                [{"id":"126508","title":"Sutgol"}]
                """);

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(result.localized()).isEqualTo(1);
        assertThat(result.keptExisting()).isEqualTo(1);
        assertThat(targets.get(1).getTitle()).isEqualTo("Cozy");
        assertThat(targets.get(1).getTitleLocalizedAt()).isNull();
    }

    @Test
    void 모델_장식을_걷어낸다() {
        givenPlaces(SUTGOL);
        answers("""
                ```json
                [{"id":"126508","title":"\\"Sutgol\\"\\n(a proper noun)"}]
                ```
                """);

        localizer.localize("en", 10, false);

        assertThat(targets.get(0).getTitle()).isEqualTo("Sutgol");
    }

    @Test
    void 호출이_실패하면_기존_제목을_유지하고_표시를_안_찍는다() {
        givenPlaces(SUTGOL);
        BDDMockito.given(gemini.generate(any())).willThrow(new GeminiException("429", null));

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(targets.get(0).getTitle()).isEqualTo("Male bone");
        assertThat(targets.get(0).getTitleLocalizedAt()).isNull();
        assertThat(result.keptExisting()).isEqualTo(1);
        BDDMockito.then(translations).should(Mockito.never()).save(any());
        BDDMockito.then(events).should(Mockito.never()).publishEvent(any(PlaceDataChangedEvent.class));
    }

    @Test
    void 배열을_못_읽으면_배치_전체가_기존_제목을_유지한다() {
        givenPlaces(SUTGOL, ANUK);
        answers("I could not process this request.");

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(result.keptExisting()).isEqualTo(2);
        assertThat(targets).extracting(PlaceTranslation::getTitle).containsExactly("Male bone", "Cozy");
        assertThat(targets).allMatch(t -> t.getTitleLocalizedAt() == null);
    }

    @Test
    void 빈_제목이면_기존_제목을_유지한다() {
        givenPlaces(SUTGOL);
        answers("""
                [{"id":"126508","title":"   "}]
                """);

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(targets.get(0).getTitle()).isEqualTo("Male bone");
        assertThat(targets.get(0).getTitleLocalizedAt()).isNull();
        assertThat(result.keptExisting()).isEqualTo(1);
    }

    @Test
    void 길이_상한을_넘으면_기존_제목을_유지한다() {
        // place_translation.title이 VARCHAR(200)이라 넘으면 적재 자체가 실패한다.
        givenPlaces(SUTGOL);
        answers("[{\"id\":\"126508\",\"title\":\"" + "A".repeat(201) + "\"}]");

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(targets.get(0).getTitle()).isEqualTo("Male bone");
        assertThat(result.keptExisting()).isEqualTo(1);
    }

    @Test
    void 이미_처리한_행은_다시_부르지_않는다() {
        givenPlaces(SUTGOL);
        targets.get(0).setTitleLocalizedAt(LocalDateTime.now());

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(result.skipped()).isEqualTo(1);
        BDDMockito.then(gemini).should(Mockito.never()).generate(any());
    }

    @Test
    void force면_표시를_무시하고_다시_만든다() {
        givenPlaces(SUTGOL);
        targets.get(0).setTitleLocalizedAt(LocalDateTime.now());
        answers("""
                [{"id":"126508","title":"Sutgol"}]
                """);

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, true);

        assertThat(result.localized()).isEqualTo(1);
        BDDMockito.then(gemini).should().generate(any());
    }

    @Test
    void 결과가_기존과_같아도_표시는_찍는다() {
        // 안 찍으면 다음 실행이 같은 호출을 또 한다 — 분당 5회에서 그 낭비가 크다.
        givenPlaces(SUTGOL);
        answers("""
                [{"id":"126508","title":"Male bone"}]
                """);

        TitleLocalizer.LocalizeResult result = localizer.localize("en", 10, false);

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(targets.get(0).getTitleLocalizedAt()).isNotNull();
        BDDMockito.then(translations).should().save(targets.get(0));
    }
}
