package com.hiddengyeongbuk.backend.ingest.translate.title;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 무엇을 다시 만들 대상으로 볼 것인가(ADR-026).
 *
 * <p>가장 중요한 것은 <b>{@code OFFICIAL}을 건드리지 않는 것</b>이다. 공식 영문 제목은 관광공사
 * 영문 서비스가 준 것이라 우리가 만든 어떤 것보다 낫다 — 덮으면 품질이 내려간다.
 */
class TitleCandidateLoaderTest {

    private final PlaceRepository places = Mockito.mock(PlaceRepository.class);
    private final PlaceTranslationRepository translations = Mockito.mock(PlaceTranslationRepository.class);
    private final StagingPlaceRepository staging = Mockito.mock(StagingPlaceRepository.class);

    private final TitleCandidateLoader loader = new TitleCandidateLoader(places, translations, staging);

    private static PlaceTranslation translation(long placeId, String lang, String title, TranslationSource source) {
        return PlaceTranslation.builder()
                .placeId(placeId)
                .lang(lang)
                .title(title)
                .source(source)
                .build();
    }

    private static Place place(long id) {
        Place p = Place.builder().placeType(PlaceType.AC).build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static StagingPlace koStaging(long placeId, String contentId) {
        StagingPlace s = StagingPlace.builder()
                .lang("ko")
                .sourceContentId(contentId)
                .title("숫골")
                .build();
        s.setResolvedPlaceId(placeId);
        return s;
    }

    private void given(List<PlaceTranslation> en, List<PlaceTranslation> ko, List<StagingPlace> stagingRows) {
        BDDMockito.given(translations.findByLang("en")).willReturn(en);
        BDDMockito.given(translations.findByLang("ko")).willReturn(ko);
        BDDMockito.given(places.findAll()).willReturn(List.of(place(7L), place(8L)));
        BDDMockito.given(staging.findByResolutionStatusAndLang(ResolutionStatus.MATCHED, "ko"))
                .willReturn(stagingRows);
    }

    @Test
    void 기계번역_행만_대상으로_삼는다() {
        given(
                List.of(
                        translation(7L, "en", "Male bone", TranslationSource.TRANSLATE),
                        translation(8L, "en", "Andong Hahoe Village", TranslationSource.OFFICIAL)),
                List.of(translation(7L, "ko", "숫골", TranslationSource.OFFICIAL),
                        translation(8L, "ko", "안동 하회마을", TranslationSource.OFFICIAL)),
                List.of(koStaging(7L, "126508"), koStaging(8L, "126509")));

        List<TitleCandidate> candidates = loader.load("en");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).placeId()).isEqualTo(7L);
        assertThat(candidates.get(0).contentId()).isEqualTo("126508");
        assertThat(candidates.get(0).koTitle()).isEqualTo("숫골");
    }

    @Test
    void 국문_원문이_없으면_제외한다() {
        // 다시 만들 근거가 없다. 기존 영문을 그대로 두는 것이 맞다.
        given(
                List.of(translation(7L, "en", "Male bone", TranslationSource.TRANSLATE)),
                List.of(),
                List.of(koStaging(7L, "126508")));

        assertThat(loader.load("en")).isEmpty();
    }

    @Test
    void 교정_키가_없으면_제외한다() {
        // MATCHED 국문 staging이 없으면 교정을 걸 자리가 없어 재실행 관리가 불가능하다.
        given(
                List.of(translation(7L, "en", "Male bone", TranslationSource.TRANSLATE)),
                List.of(translation(7L, "ko", "숫골", TranslationSource.OFFICIAL)),
                List.of());

        assertThat(loader.load("en")).isEmpty();
    }
}
