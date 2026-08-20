package com.hiddengyeongbuk.backend.ingest.translate.title;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 제목을 다시 만들 대상을 모은다.
 *
 * <p><b>{@code TRANSLATE} 행만 대상이다.</b> {@code OFFICIAL}은 해당 언어 서비스가 준 공식
 * 제목이라 우리가 만든 것보다 언제나 낫다 — 덮으면 품질이 내려간다.
 *
 * <p>조회를 장소마다 날리지 않고 <b>네 번에 나눠 통째로 읽고 메모리에서 잇는다</b>. 규모가
 * place 약 1,300건이라 전부 메모리에 올라가고(측정 전 비정규화하지 않는다는 원칙과 같은 이유),
 * 장소별 findById는 N+1이다.
 */
@Component
class TitleCandidateLoader {

    private final PlaceRepository placeRepository;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final StagingPlaceRepository stagingPlaceRepository;

    TitleCandidateLoader(
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository,
            StagingPlaceRepository stagingPlaceRepository) {
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.stagingPlaceRepository = stagingPlaceRepository;
    }

    List<TitleCandidate> load(String lang) {
        Map<Long, PlaceTranslation> base = placeTranslationRepository.findByLang(LanguageRegistry.BASE).stream()
                .collect(Collectors.toMap(PlaceTranslation::getPlaceId, Function.identity(), (a, b) -> a));
        Map<Long, Place> places = placeRepository.findAll().stream()
                .collect(Collectors.toMap(Place::getId, Function.identity(), (a, b) -> a));
        // 교정 키가 될 국문 contentId. MATCHED만 본다 — 다른 판정은 place를 뒷받침하지 않는다.
        Map<Long, String> contentIds =
                stagingPlaceRepository.findByResolutionStatusAndLang(ResolutionStatus.MATCHED, LanguageRegistry.BASE)
                        .stream()
                        .filter(s -> s.getResolvedPlaceId() != null)
                        .collect(Collectors.toMap(
                                StagingPlace::getResolvedPlaceId, StagingPlace::getSourceContentId, (a, b) -> a));

        return placeTranslationRepository.findByLang(lang).stream()
                .filter(t -> t.getSource() == TranslationSource.TRANSLATE)
                .map(t -> toCandidate(t, base, places, contentIds))
                .filter(java.util.Objects::nonNull)
                // 재실행마다 같은 순서여야 limit을 나눠 돌린 결과가 이어진다.
                .sorted(Comparator.comparingLong(TitleCandidate::placeId))
                .toList();
    }

    private TitleCandidate toCandidate(
            PlaceTranslation target,
            Map<Long, PlaceTranslation> base,
            Map<Long, Place> places,
            Map<Long, String> contentIds) {
        Long placeId = target.getPlaceId();
        PlaceTranslation ko = base.get(placeId);
        String contentId = contentIds.get(placeId);
        // 국문 원문이나 교정 키가 없으면 다시 만들 근거도, 교정을 걸 자리도 없다.
        if (ko == null || contentId == null || ko.getTitle() == null || ko.getTitle().isBlank()) {
            return null;
        }
        Place place = places.get(placeId);
        String placeType = place != null && place.getPlaceType() != null ? place.getPlaceType().name() : null;
        return new TitleCandidate(
                placeId, contentId, placeType, ko.getTitle(), ko.getOverview(), ko.getAddr(), target);
    }
}
