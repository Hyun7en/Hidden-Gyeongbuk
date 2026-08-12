package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.place.PlaceTagRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merge 단계의 DB 쓰기 전담. <b>Merger와 별도 빈</b>이라 그룹 하나가 독립 트랜잭션으로 커밋된다 —
 * 같은 클래스 안에서 메서드를 나누면 self-invocation이라 {@code @Transactional}이 걸리지 않는다.
 */
@Component
public class MergeWriter {

    private final PlaceRepository placeRepository;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final PlaceTagRepository placeTagRepository;
    private final StagingPlaceRepository stagingPlaceRepository;
    private final RegionRepository regionRepository;
    private final ContentTypeMapping contentTypes;
    private final PlaceTagAssigner placeTagAssigner;

    public MergeWriter(
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository,
            PlaceTagRepository placeTagRepository,
            StagingPlaceRepository stagingPlaceRepository,
            RegionRepository regionRepository,
            ContentTypeMapping contentTypes,
            PlaceTagAssigner placeTagAssigner) {
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.placeTagRepository = placeTagRepository;
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.regionRepository = regionRepository;
        this.contentTypes = contentTypes;
        this.placeTagAssigner = placeTagAssigner;
    }

    public record Written(boolean placeCreated, int translations) {}

    /**
     * 같은 장소로 판정된 staging 그룹을 place 1건 + 언어별 place_translation 행으로 병합한다.
     *
     * @param group 첫 원소가 대표(앵커). 기준 언어가 있으면 그것이 앵커다
     */
    @Transactional
    public Written write(List<StagingPlace> group) {
        StagingPlace anchor = group.get(0);

        // 이미 병합된 그룹이면 그 place를 재사용한다 — 재실행이 중복 place를 만들지 않는
        // 근거다(ERD §3.2). 그룹 내 아무 행이든 연결돼 있으면 그걸 따른다.
        Long existingPlaceId = group.stream()
                .map(StagingPlace::getResolvedPlaceId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        Place place = existingPlaceId == null
                ? null
                : placeRepository.findById(existingPlaceId).orElse(null);
        boolean created = place == null;
        if (created) {
            // 기본 생성자가 protected라(JPA 전용) 빌더로 만든다.
            place = Place.builder().build();
        }

        applyNeutralFields(place, anchor, group);
        Place saved = placeRepository.save(place);

        int translations = 0;
        for (StagingPlace member : group) {
            upsertTranslation(saved, member);
            member.setResolvedPlaceId(saved.getId());
            translations++;
        }
        stagingPlaceRepository.saveAll(group);

        applyTags(saved, group);
        return new Written(created, translations);
    }

    /**
     * 언어 중립 필드는 그룹 전체에서 모은다. 좌표·이미지 같은 값은 어느 언어 서비스가 주든
     * 같은 장소의 속성이므로, 앵커에 없으면 다른 언어 행에서 가져온다(결측 보완).
     */
    private void applyNeutralFields(Place place, StagingPlace anchor, List<StagingPlace> group) {
        place.setRegion(resolveRegion(anchor, group));
        place.setPlaceType(resolvePlaceType(anchor, group));
        place.setLat(first(group, StagingPlace::getLat));
        place.setLng(first(group, StagingPlace::getLng));
        place.setTel(first(group, StagingPlace::getTel));
        place.setFirstImage(first(group, StagingPlace::getFirstImage));
        place.setLclsSystm1(first(group, StagingPlace::getLclsSystm1));
        place.setLclsSystm2(first(group, StagingPlace::getLclsSystm2));
        place.setLclsSystm3(first(group, StagingPlace::getLclsSystm3));
    }

    private Region resolveRegion(StagingPlace anchor, List<StagingPlace> group) {
        return group.stream()
                .filter(s -> s.getLdongRegnCd() != null && s.getLdongSignguCd() != null)
                .map(s -> regionRepository.findByLdongRegnCdAndLdongSignguCd(
                        s.getLdongRegnCd(), s.getLdongSignguCd()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "staging의 법정동코드에 해당하는 region이 없다: staging id=" + anchor.getId()));
    }

    /**
     * 소스 contentTypeId는 언어마다 값이 다르므로 각 행의 언어로 해석한다. 언어별로 타입이
     * 어긋나면 앵커(기준 언어) 쪽을 채택한다 — 국문 분류가 더 촘촘한 편이다.
     */
    private PlaceType resolvePlaceType(StagingPlace anchor, List<StagingPlace> group) {
        return group.stream()
                .map(s -> contentTypes.placeTypeOf(s.getLang(), s.getSourceTypeId()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "contentTypeId 매핑에 없는 타입이다: staging id=" + anchor.getId()));
    }

    /**
     * 언어별 콘텐츠를 (place_id, lang) 행으로 upsert한다.
     *
     * <p>수집으로 들어온 값은 그 언어 서비스의 원본이므로 항상 {@code OFFICIAL}이다. 기존 행이
     * 기계 번역이었다면 여기서 덮어써진다 — TRANSLATE → OFFICIAL은 단방향이며 역방향은 없다.
     */
    private void upsertTranslation(Place place, StagingPlace staging) {
        PlaceTranslation translation = placeTranslationRepository
                .findByPlaceIdAndLang(place.getId(), staging.getLang())
                .orElseGet(() -> PlaceTranslation.builder()
                        .placeId(place.getId())
                        .lang(staging.getLang())
                        .build());

        // title은 NOT NULL이다. 소스에 제목이 없으면 행을 만들 수 없으므로 건너뛴다.
        if (staging.getTitle() == null || staging.getTitle().isBlank()) {
            return;
        }

        translation.setTitle(staging.getTitle());
        translation.setOverview(staging.getOverview());
        translation.setAddr(staging.getAddr());
        translation.setSource(TranslationSource.OFFICIAL);
        translation.setSrcContentId(staging.getSourceContentId());
        translation.setSrcModifiedAt(staging.getSourceModifiedAt());
        translation.setGeneratedAt(LocalDateTime.now());
        placeTranslationRepository.save(translation);
    }

    /**
     * 태그는 무료 로컬 연산이라 매번 재부여한다 — 규칙을 바꾸면 다음 수집에 자동 반영된다.
     * 판정 기준이 한국어 키워드라 기준 언어 행의 텍스트를 쓴다.
     */
    private void applyTags(Place place, List<StagingPlace> group) {
        StagingPlace korean = group.stream()
                .filter(s -> LanguageRegistry.BASE.equalsIgnoreCase(s.getLang()))
                .findFirst()
                .orElse(null);
        if (korean == null) {
            return;
        }

        Set<String> tags = placeTagAssigner.assign(place.getPlaceType(), korean.getTitle(), korean.getOverview());
        placeTagRepository.deleteByPlaceId(place.getId());
        // flush 없이 바로 insert하면 Hibernate가 같은 트랜잭션 내 insert를 delete보다 먼저
        // 내보내 유니크 제약을 위반한다.
        placeTagRepository.flush();
        tags.forEach(tag -> placeTagRepository.save(
                PlaceTag.builder().place(place).tag(tag).build()));
    }

    private static <T> T first(List<StagingPlace> group, java.util.function.Function<StagingPlace, T> getter) {
        return group.stream().map(getter).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
