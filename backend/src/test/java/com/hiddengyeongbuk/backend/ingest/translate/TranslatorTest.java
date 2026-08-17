package com.hiddengyeongbuk.backend.ingest.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.ingest.SyncLogger;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 백필 루프의 중단 조건.
 *
 * <p>2026-08-17에 일일 할당량이 소진된 뒤에도 남은 후보를 계속 두드려, 후보마다 22초(재시도 21초
 * + 페이싱 1초)를 태우며 {@code limit}까지 갔다. {@code limit=300}이면 1시간 45분이다. 할당량은
 * 어차피 거부된 요청에 소모되지 않으니 잃은 것은 시간뿐이지만, 그 시간이 매일 반복된다.
 *
 * <p>여기서 지키는 것은 <b>재시도를 다 소진한 실패 하나면 즉시 멈춘다</b>는 규칙과, 그 신호가
 * 아닌 실패는 <b>다음 후보로 넘어간다</b>는 구분이다.
 */
class TranslatorTest {

    private final TranslateClient client = Mockito.mock(TranslateClient.class);
    private final PlaceRepository places = Mockito.mock(PlaceRepository.class);
    private final PlaceTranslationRepository translations = Mockito.mock(PlaceTranslationRepository.class);
    private final LanguageRegistry languages = Mockito.mock(LanguageRegistry.class);
    private final SyncLogger syncLogger = Mockito.mock(SyncLogger.class);
    private final ApplicationEventPublisher events = Mockito.mock(ApplicationEventPublisher.class);

    private final Translator translator =
            new Translator(client, places, translations, languages, syncLogger, events);

    private static Place place(long id) {
        Place p = Place.builder().build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    /** 후보 n곳, 각 장소에 국문 원문 한 줄. */
    private void given(int n) {
        BDDMockito.given(languages.resolve("en")).willReturn("en");
        BDDMockito.given(languages.isSupported("en")).willReturn(true);
        BDDMockito.given(places.findAll())
                .willReturn(IntStream.rangeClosed(1, n).mapToObj(i -> place(i)).toList());
        BDDMockito.given(translations.findByLang("en")).willReturn(List.of());
        BDDMockito.given(translations.findByPlaceId(anyLongArg()))
                .willAnswer(inv -> List.of(PlaceTranslation.builder()
                        .placeId(inv.getArgument(0))
                        .lang(LanguageRegistry.BASE)
                        .title("안동")
                        .addr("경상북도 안동시")
                        .source(TranslationSource.OFFICIAL)
                        .build()));
    }

    private static Long anyLongArg() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    @Test
    void 재시도를_다_소진한_실패가_나오면_즉시_중단한다() {
        given(50);
        BDDMockito.given(client.translate(anyList(), anyString(), anyString()))
                .willReturn(List.of("Andong", "Andong-si")) // 1곳 성공
                .willThrow(new TranslateExhaustedException("재시도 4회를 모두 소진했다", null));

        Translator.BackfillResult r = translator.backfill("en", 50);

        assertThat(r.translated()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
        // 50곳 중 2곳만 손댔다 — 나머지는 건드리지 않았다.
        assertThat(r.remaining()).isEqualTo(48);
        assertThat(r.stoppedBy()).contains("할당량");
        // 두 번만 호출됐는지가 핵심이다. 안 멈추면 50번 호출되고 그만큼 시간을 태운다.
        BDDMockito.then(client).should(Mockito.times(2)).translate(anyList(), anyString(), anyString());
    }

    @Test
    void 그_장소만의_실패는_다음_후보로_넘어간다() {
        // 원문이 이상한 한 곳 때문에 배치 전체가 멈추면 안 된다.
        given(3);
        BDDMockito.given(client.translate(anyList(), anyString(), anyString()))
                .willThrow(new TranslateException("응답에서 번역 결과를 찾지 못함", null))
                .willReturn(List.of("Andong", "Andong-si"))
                .willReturn(List.of("Andong", "Andong-si"));

        Translator.BackfillResult r = translator.backfill("en", 10);

        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.translated()).isEqualTo(2);
        assertThat(r.remaining()).isZero();
        assertThat(r.stoppedBy()).isNull();
    }

    @Test
    void 정상_완주하면_중단_사유가_없다() {
        given(2);
        BDDMockito.given(client.translate(anyList(), anyString(), anyString()))
                .willReturn(List.of("Andong", "Andong-si"));

        Translator.BackfillResult r = translator.backfill("en", 10);

        assertThat(r.translated()).isEqualTo(2);
        assertThat(r.stoppedBy()).isNull();
        assertThat(r.remaining()).isZero();
    }

    @Test
    void limit에_닿으면_사유를_남기고_멈춘다() {
        given(10);
        BDDMockito.given(client.translate(anyList(), anyString(), anyString()))
                .willReturn(List.of("Andong", "Andong-si"));

        Translator.BackfillResult r = translator.backfill("en", 3);

        assertThat(r.translated()).isEqualTo(3);
        assertThat(r.remaining()).isEqualTo(7);
        assertThat(r.stoppedBy()).contains("limit");
    }

    @Test
    void 사용_글자_수를_원문_기준으로_센다() {
        // 무료 한도가 월 50만 자라 이 값이 예산 관리의 근거다. 구글도 원문 기준으로 센다.
        given(1);
        BDDMockito.given(client.translate(anyList(), anyString(), anyString()))
                .willReturn(List.of("Andong", "Andong-si"));

        Translator.BackfillResult r = translator.backfill("en", 1);

        // "안동"(2) + "경상북도 안동시"(8, 공백 포함)
        assertThat(r.charactersUsed()).isEqualTo(10);
    }
}
