package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;

import com.hiddengyeongbuk.backend.domain.place.FestivalDetailRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceImageRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import java.util.List;
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
        BDDMockito.given(staging.findPlaceIdsReferencedByMatched(ResolutionStatus.MATCHED))
                .willReturn(referencedByMatched);
        BDDMockito.given(staging.findLinkedButNotMatched(ResolutionStatus.MATCHED)).willReturn(linkedNotMatched);
        BDDMockito.given(places.findAll()).willReturn(all);
        BDDMockito.given(translations.findByPlaceIdIn(anyCollection())).willReturn(List.of());
        BDDMockito.given(images.findByPlaceIdIn(anyCollection())).willReturn(List.of());
        BDDMockito.given(festivals.findByPlaceIdIn(anyCollection())).willReturn(List.of());
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
}
