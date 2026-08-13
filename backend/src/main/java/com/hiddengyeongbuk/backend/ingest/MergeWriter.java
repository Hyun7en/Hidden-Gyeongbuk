package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final StagingPlaceRepository stagingPlaceRepository;
    private final RegionRepository regionRepository;
    private final ContentTypeMapping contentTypes;

    public MergeWriter(
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository,
            StagingPlaceRepository stagingPlaceRepository,
            RegionRepository regionRepository,
            ContentTypeMapping contentTypes) {
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.regionRepository = regionRepository;
        this.contentTypes = contentTypes;
    }

    public record Written(boolean placeCreated, int translations, boolean quarantined) {}

    /**
     * 같은 장소로 판정된 staging 그룹을 place 1건 + 언어별 place_translation 행으로 병합한다.
     *
     * @param group 첫 원소가 대표(앵커). 기준 언어가 있으면 그것이 앵커다
     */
    @Transactional
    public Written write(List<StagingPlace> group) {
        StagingPlace anchor = group.get(0);

        // 분류를 정할 수 없으면 place를 만들지 않는다. 오매칭보다 미매칭을 택하는 원칙과 같은
        // 결이며(ADR-016), 예외로 던져 매 Merge마다 재시도하게 두면 실패 카운트만 쌓이고
        // 원인이 보이지 않는다 — QUARANTINED로 남겨 건수를 관측한다(ADR-024 결과 ⑦).
        Optional<PlaceType> resolvedType = resolvePlaceType(group);
        if (resolvedType.isEmpty()) {
            quarantine(group);
            return new Written(false, 0, true);
        }

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

        applyNeutralFields(place, anchor, group, resolvedType.get());
        Place saved = placeRepository.save(place);

        int translations = 0;
        for (StagingPlace member : group) {
            upsertTranslation(saved, member);
            member.setResolvedPlaceId(saved.getId());
            translations++;
        }
        stagingPlaceRepository.saveAll(group);

        return new Written(created, translations, false);
    }

    /** 분류 불명 그룹을 보류로 되돌린다. place는 만들지 않고 사유를 남겨 수동 검토 근거로 둔다. */
    private void quarantine(List<StagingPlace> group) {
        group.forEach(s -> {
            s.setResolutionStatus(ResolutionStatus.QUARANTINED);
            s.setQuarantineReason("분류 없음 (lclsSystm1 결측, contentTypeId 폴백 불가)");
        });
        stagingPlaceRepository.saveAll(group);
    }

    /**
     * 언어 중립 필드는 그룹 전체에서 모은다. 좌표·이미지 같은 값은 어느 언어 서비스가 주든
     * 같은 장소의 속성이므로, 앵커에 없으면 다른 언어 행에서 가져온다(결측 보완).
     */
    private void applyNeutralFields(
            Place place, StagingPlace anchor, List<StagingPlace> group, PlaceType placeType) {
        place.setRegion(resolveRegion(anchor, group));
        place.setPlaceType(placeType);
        place.setLat(first(group, StagingPlace::getLat));
        place.setLng(first(group, StagingPlace::getLng));
        place.setTel(first(group, StagingPlace::getTel));
        place.setFirstImage(first(group, StagingPlace::getFirstImage));
        // lcls_systm2/3은 place에 두지 않는다 — 원본이 staging에 남아 있고, 세분 필터가
        // 필요해지면 컬럼을 추가하고 Merge를 재실행하면 된다(ADR-024).
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
     * 분류는 소스가 준 {@code lclsSystm1}을 그대로 쓴다(ADR-024). 언어 무관한 값이라 어느 언어
     * 행에서 가져오든 같아야 하며, 앞선 행부터 채택한다.
     *
     * <p>비어 있으면 contentTypeId로 폴백한다. 폴백은 1:1 대응이 명확한 타입에만 있고,
     * 관광지(12/76)는 대분류가 넷으로 갈라져 폴백이 없다 — 이 경우 빈 값을 돌려주고 호출부가
     * quarantine으로 보낸다.
     */
    private Optional<PlaceType> resolvePlaceType(List<StagingPlace> group) {
        Optional<PlaceType> fromSource = group.stream()
                .map(s -> PlaceType.ofSourceCode(s.getLclsSystm1()))
                .flatMap(Optional::stream)
                .findFirst();
        if (fromSource.isPresent()) {
            return fromSource;
        }
        return group.stream()
                .map(s -> contentTypes.fallbackPlaceType(s.getLang(), s.getSourceTypeId()))
                .flatMap(Optional::stream)
                .findFirst();
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

    private static <T> T first(List<StagingPlace> group, java.util.function.Function<StagingPlace, T> getter) {
        return group.stream().map(getter).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
