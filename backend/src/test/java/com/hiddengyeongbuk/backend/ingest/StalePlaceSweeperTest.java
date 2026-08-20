package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;

import com.hiddengyeongbuk.backend.domain.place.FestivalDetailRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceImageRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 근거를 잃은 place 정리. 삭제하는 코드라 <b>지우면 안 되는 것을 지키는 쪽</b>에 테스트를 더 둔다.
 */
class StalePlaceSweeperTest {

    private final StagingPlaceRepository staging = Mockito.mock(StagingPlaceRepository.class);
    private final PlaceRepository places = Mockito.mock(PlaceRepository.class);
    private final PlaceTranslationRepository translations = Mockito.mock(PlaceTranslationRepository.class);
    private final PlaceImageRepository images = Mockito.mock(PlaceImageRepository.class);
    private final FestivalDetailRepository festivals = Mockito.mock(FestivalDetailRepository.class);

    private final StalePlaceSweeper sweeper =
            new StalePlaceSweeper(staging, places, translations, images, festivals);

    private static Place place(long id) {
        Place p = Place.builder().build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static StagingPlace quarantined(long placeId) {
        StagingPlace s = StagingPlace.builder()
                .lang("en")
                .sourceContentId("en-" + placeId)
                .resolutionStatus(ResolutionStatus.QUARANTINED)
                .build();
        s.setResolvedPlaceId(placeId);
        return s;
    }

    @SuppressWarnings("unchecked")
    private void stub(List<Long> referencedByMatched, List<StagingPlace> linkedNotMatched, List<Place> all) {
        // 안전장치는 MATCHED만 보고, 살려 둘 집합은 MATCHED+PENDING을 본다.
        BDDMockito.given(staging.findPlaceIdsReferencedBy(Set.of(ResolutionStatus.MATCHED)))
                .willReturn(referencedByMatched);
        BDDMockito.given(staging.findPlaceIdsReferencedBy(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(referencedByMatched);
        BDDMockito.given(staging.findLinkedButNotIn(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(linkedNotMatched);
        BDDMockito.given(places.findAll()).willReturn(all);
        BDDMockito.given(translations.findByPlaceIdIn(anyCollection())).willReturn(List.of());
        BDDMockito.given(images.findByPlaceIdIn(anyCollection())).willReturn(List.of());
        BDDMockito.given(festivals.findByPlaceIdIn(anyCollection())).willReturn(List.of());
        // 언어 단위 근거는 기본값 없음 — 필요한 테스트가 직접 채운다.
        BDDMockito.given(staging.findPlaceLangPairsReferencedBy(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(List.<Object[]>of());
    }

    private static PlaceTranslation translation(long placeId, String lang, TranslationSource source) {
        return PlaceTranslation.builder()
                .placeId(placeId)
                .lang(lang)
                .title("t")
                .source(source)
                .build();
    }

    @Test
    void MATCHED가_가리키지_않는_place를_지운다() {
        // 재판정으로 그룹이 합쳐지며 승자 아닌 place가 남은 상황.
        stub(List.of(1L, 2L), List.of(), List.of(place(1), place(2), place(99)));

        StalePlaceSweeper.Result r = sweeper.sweep();

        assertThat(r.deletedPlaces()).isEqualTo(1);
        ArgumentCaptor<Iterable<Place>> captor = ArgumentCaptor.forClass(Iterable.class);
        BDDMockito.then(places).should().deleteAll(captor.capture());
        assertThat(captor.getValue()).extracting("id").containsExactly(99L);
    }

    @Test
    void 보류_행의_place_링크를_끊는다() {
        // ADR-016은 보류 건에 place를 두지 않는다. FK가 RESTRICT라 링크를 끊지 않으면 삭제도 막힌다.
        StagingPlace q = quarantined(99L);
        stub(List.of(1L), List.of(q), List.of(place(1), place(99)));

        StalePlaceSweeper.Result r = sweeper.sweep();

        assertThat(q.getResolvedPlaceId()).isNull();
        assertThat(r.unlinked()).isEqualTo(1);
        assertThat(r.deletedPlaces()).isEqualTo(1);
    }

    @Test
    void MATCHED가_하나도_없으면_아무것도_지우지_않는다() {
        // 판정이 안 돌았거나 깨진 상태다. 여기서 쓸어내면 전량이 날아간다.
        stub(List.of(), List.of(quarantined(99L)), List.of(place(1), place(2), place(99)));

        StalePlaceSweeper.Result r = sweeper.sweep();

        assertThat(r.deletedPlaces()).isZero();
        assertThat(r.unlinked()).isZero();
        BDDMockito.then(places).should(Mockito.never()).deleteAll(any());
        BDDMockito.then(staging).should(Mockito.never()).saveAll(any());
    }

    @Test
    void 지울_것이_없으면_삭제를_호출하지_않는다() {
        stub(List.of(1L, 2L), List.of(), List.of(place(1), place(2)));

        StalePlaceSweeper.Result r = sweeper.sweep();

        assertThat(r.deletedPlaces()).isZero();
        BDDMockito.then(places).should(Mockito.never()).deleteAll(any());
    }

    // ── 언어 단위 정리 ────────────────────────────────────────────────────────
    // place는 살아 있는데 한 언어만 소스에서 내려간 경우(showflag=0). place 단위 정리로는
    // 안 잡히고, MergeWriter는 MATCHED 그룹만 다시 쓰므로 그 행은 영원히 갱신되지 않는다.
    // 2026-08-19 실측 81건(안동댐·안동호 등).

    @Test
    void 근거를_잃은_공식_번역_행을_지운다() {
        stub(List.of(1L), List.of(), List.of(place(1)));
        // 국문만 살아 있고 영문은 EXCLUDED가 됐다.
        BDDMockito.given(staging.findPlaceLangPairsReferencedBy(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(List.<Object[]>of(new Object[] {1L, "ko"}));
        BDDMockito.given(translations.findByPlaceIdIn(Set.of(1L)))
                .willReturn(List.of(
                        translation(1L, "ko", TranslationSource.OFFICIAL),
                        translation(1L, "en", TranslationSource.OFFICIAL)));

        StalePlaceSweeper.Result r = sweeper.sweep();

        assertThat(r.deletedPlaces()).isZero();
        assertThat(r.deletedTranslations()).isEqualTo(1);
        ArgumentCaptor<Iterable<PlaceTranslation>> captor = ArgumentCaptor.forClass(Iterable.class);
        BDDMockito.then(translations).should().deleteAll(captor.capture());
        assertThat(captor.getValue()).extracting("lang").containsExactly("en");
    }

    @Test
    void 기계_번역은_지우지_않는다() {
        // TRANSLATE 행은 우리가 국문에서 만든 것이라 소스 목록과 무관하다. 지우면 다음 백필이
        // 같은 것을 다시 번역해 무료 한도만 태운다.
        stub(List.of(1L), List.of(), List.of(place(1)));
        BDDMockito.given(staging.findPlaceLangPairsReferencedBy(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(List.<Object[]>of(new Object[] {1L, "ko"}));
        BDDMockito.given(translations.findByPlaceIdIn(Set.of(1L)))
                .willReturn(List.of(
                        translation(1L, "ko", TranslationSource.OFFICIAL),
                        translation(1L, "en", TranslationSource.TRANSLATE)));

        StalePlaceSweeper.Result r = sweeper.sweep();

        assertThat(r.deletedTranslations()).isZero();
        BDDMockito.then(translations).should(Mockito.never()).deleteAll(any());
    }

    @Test
    void 근거가_있는_행은_남긴다() {
        stub(List.of(1L), List.of(), List.of(place(1)));
        BDDMockito.given(staging.findPlaceLangPairsReferencedBy(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(List.<Object[]>of(new Object[] {1L, "ko"}, new Object[] {1L, "en"}));
        BDDMockito.given(translations.findByPlaceIdIn(Set.of(1L)))
                .willReturn(List.of(
                        translation(1L, "ko", TranslationSource.OFFICIAL),
                        translation(1L, "en", TranslationSource.OFFICIAL)));

        assertThat(sweeper.sweep().deletedTranslations()).isZero();
    }

    // ── PENDING 보호 ─────────────────────────────────────────────────────────
    // 2026-08-20에 실제로 데이터를 잃은 자리다. Collector가 변경된 행을 PENDING으로 되돌리므로
    // collect 직후 resolve 없이 merge를 돌리면 그 행들이 잠깐 MATCHED가 아니다. 그때 PENDING을
    // 고아로 보면 멀쩡한 place가 지워진다 — place 7곳(백석탄계곡·최치원문학관 등)을 잃었다.

    @Test
    void 재판정_대기중인_place는_지우지_않는다() {
        // MATCHED가 가리키는 것은 1번뿐이지만, 2번은 PENDING이 가리키고 있어 살아야 한다.
        BDDMockito.given(staging.findPlaceIdsReferencedBy(Set.of(ResolutionStatus.MATCHED)))
                .willReturn(List.of(1L));
        BDDMockito.given(staging.findPlaceIdsReferencedBy(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(List.of(1L, 2L));
        BDDMockito.given(staging.findLinkedButNotIn(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(List.of());
        BDDMockito.given(staging.findPlaceLangPairsReferencedBy(
                        Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING)))
                .willReturn(List.<Object[]>of());
        BDDMockito.given(places.findAll()).willReturn(List.of(place(1), place(2), place(99)));
        BDDMockito.given(translations.findByPlaceIdIn(anyCollection())).willReturn(List.of());
        BDDMockito.given(images.findByPlaceIdIn(anyCollection())).willReturn(List.of());
        BDDMockito.given(festivals.findByPlaceIdIn(anyCollection())).willReturn(List.of());

        StalePlaceSweeper.Result r = sweeper.sweep();

        // 99번만 지워야 한다. 2번은 재판정 대기일 뿐이다.
        assertThat(r.deletedPlaces()).isEqualTo(1);
        ArgumentCaptor<Iterable<Place>> captor = ArgumentCaptor.forClass(Iterable.class);
        BDDMockito.then(places).should().deleteAll(captor.capture());
        assertThat(captor.getValue()).extracting("id").containsExactly(99L);
    }

    @Test
    void PENDING_행의_place_링크는_끊지_않는다() {
        // 링크를 유지해야 재판정 후 Merge가 같은 place를 재사용해 place_id가 바뀌지 않는다.
        // 끊으면 place가 새로 만들어지면서 id가 churn한다.
        stub(List.of(1L), List.of(), List.of(place(1)));

        sweeper.sweep();

        // 링크 해제 대상 조회에 PENDING이 제외 집합으로 들어가는지 — 즉 PENDING은 안 끊긴다.
        BDDMockito.then(staging)
                .should()
                .findLinkedButNotIn(Set.of(ResolutionStatus.MATCHED, ResolutionStatus.PENDING));
    }
}
