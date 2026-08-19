package com.hiddengyeongbuk.backend.ingest;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근거를 잃은 place를 정리한다. Merge 끝에 한 번 돈다.
 *
 * <p><b>왜 필요한가.</b> 재판정(resolve resetAll)으로 그룹 구성이 바뀌면, 어제 서로 다른 place에
 * 붙어 있던 두 행이 오늘 한 그룹이 된다. {@code MergeWriter}는 그룹 멤버 중 먼저 발견되는 place를
 * 재사용하고 나머지는 그냥 놔두므로, <b>승자 아닌 place가 테이블에 남는다</b>. 그 place는 어느
 * 그룹에도 대응하지 않지만 화면에는 그대로 뜬다 — 같은 장소가 목록에 둘로 보인다.
 *
 * <p>2026-08-17 실측: 좌표 가드를 5km로 넓혀 재판정한 뒤 place 2,148건 중 25건이 이 상태였다
 * (국문 앵커가 없는 place 25 = 아무도 안 가리키는 것 4 + 보류 행만 가리키는 것 21).
 *
 * <p><b>보류 행의 링크를 먼저 끊는다.</b> ADR-016은 보류 건에 place를 만들지 않는다고 정했는데,
 * 이전 판정에서 붙은 {@code resolved_place_id}가 남아 그 규칙을 우회하고 있었다. 게다가 이 FK가
 * RESTRICT라(V1 스키마 주석) 링크가 살아 있으면 place 삭제 자체가 막힌다.
 *
 * <p><b>안전장치.</b> MATCHED 행이 하나도 없으면 아무것도 지우지 않는다. 그 상태는 "정리할 게
 * 전부"라는 뜻이 아니라 판정이 아직 안 돌았거나 깨졌다는 뜻이고, 그때 쓸어내면 데이터를 잃는다.
 */
@Component
public class StalePlaceSweeper {

    private static final Logger log = LoggerFactory.getLogger(StalePlaceSweeper.class);

    private final StagingPlaceRepository stagingPlaceRepository;
    private final PlaceRepository placeRepository;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final PlaceImageRepository placeImageRepository;
    private final FestivalDetailRepository festivalDetailRepository;

    public StalePlaceSweeper(
            StagingPlaceRepository stagingPlaceRepository,
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository,
            PlaceImageRepository placeImageRepository,
            FestivalDetailRepository festivalDetailRepository) {
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.placeImageRepository = placeImageRepository;
        this.festivalDetailRepository = festivalDetailRepository;
    }

    /**
     * @param unlinked place 링크를 끊은 보류 행 수
     * @param deletedPlaces 지운 place 수
     * @param deletedTranslations 살아남은 place에서 지운 근거 없는 공식 번역 행 수
     */
    public record Result(int unlinked, int deletedPlaces, int deletedTranslations) {
        boolean isEmpty() {
            return unlinked == 0 && deletedPlaces == 0 && deletedTranslations == 0;
        }
    }

    @Transactional
    public Result sweep() {
        List<Long> referenced =
                stagingPlaceRepository.findPlaceIdsReferencedByMatched(ResolutionStatus.MATCHED);
        if (referenced.isEmpty()) {
            log.warn("MATCHED staging이 없어 place 정리를 건너뜁니다 — 판정이 돌지 않은 상태로 보입니다");
            return new Result(0, 0, 0);
        }

        // 1) 보류 행이 붙들고 있는 링크를 끊는다. FK가 RESTRICT라 이걸 먼저 해야 place를 지울 수 있다.
        List<StagingPlace> linkedButNotMatched =
                stagingPlaceRepository.findLinkedButNotMatched(ResolutionStatus.MATCHED);
        linkedButNotMatched.forEach(s -> s.setResolvedPlaceId(null));
        if (!linkedButNotMatched.isEmpty()) {
            stagingPlaceRepository.saveAll(linkedButNotMatched);
        }

        // 2) MATCHED가 가리키지 않는 place를 자식부터 지운다.
        Set<Long> keep = new HashSet<>(referenced);
        List<Place> doomed = placeRepository.findAll().stream()
                .filter(p -> !keep.contains(p.getId()))
                .toList();

        if (!doomed.isEmpty()) {
            List<Long> ids = doomed.stream().map(Place::getId).toList();
            // 자식 → 부모 순. festival_detail은 @MapsId라 엔티티로 지운다(ID 직접 조작 금지).
            placeTranslationRepository.deleteAll(placeTranslationRepository.findByPlaceIdIn(ids));
            placeImageRepository.deleteAll(placeImageRepository.findByPlaceIdIn(ids));
            festivalDetailRepository.deleteAll(festivalDetailRepository.findByPlaceIdIn(ids));
            // 부모 삭제 전 자식 삭제가 먼저 나가야 한다 — 안 하면 FK 위반이다.
            placeTranslationRepository.flush();
            placeRepository.deleteAll(doomed);
        }

        // 3) 살아남은 place에서 근거를 잃은 언어 행을 지운다.
        int deletedTranslations = sweepStaleTranslations(keep);

        Result result = new Result(linkedButNotMatched.size(), doomed.size(), deletedTranslations);
        if (!result.isEmpty()) {
            log.info(
                    "근거 없는 place 정리: 링크 해제 {}건, place 삭제 {}건, 공식 번역 삭제 {}건",
                    result.unlinked(),
                    result.deletedPlaces(),
                    result.deletedTranslations());
        }
        return result;
    }

    /**
     * 살아남은 place에서 <b>근거를 잃은 공식 번역 행</b>을 지운다.
     *
     * <p><b>왜 place 정리만으로 부족한가.</b> 한 place의 국문은 소스에 살아 있고 영문만 내려간
     * 경우(showflag=0), place 자체는 근거가 있으므로 위 단계가 지우지 않는다. 그런데 그 영문
     * {@code place_translation} 행은 <b>소스가 내린 목록에서 온 텍스트</b>이고 아무도 갱신하지
     * 않는다 — {@code MergeWriter}는 MATCHED 그룹만 다시 쓰므로 EXCLUDED가 된 언어는 영원히
     * 손대지 않는다. 2026-08-19 실측으로 이 상태의 영문 행이 81건 있었다(안동댐·안동호 등).
     *
     * <p><b>OFFICIAL만 지운다.</b> TRANSLATE 행은 우리가 국문에서 만든 것이라 소스 목록과
     * 무관하며, 지우면 다음 백필이 같은 것을 다시 번역해 무료 한도만 태운다.
     *
     * <p>지운 자리는 비는 것이 아니라 <b>기계 번역으로 다시 채워진다</b>(Translator가 행 없는
     * 조합을 대상으로 삼는다). 내려간 목록의 옛 영문보다 현재 국문의 번역이 맞다 — 국문 쪽은
     * 갱신이 살아 있기 때문이다. 나중에 소스가 다시 올리면 Merge가 같은 행을 OFFICIAL로
     * 덮으므로(단방향) 되돌아오는 경로도 있다.
     *
     * <p>모든 언어가 근거를 잃은 place는 여기 오지 않는다 — 그런 place는 MATCHED가 하나도
     * 가리키지 않아 위 2)에서 통째로 지워진다. 즉 이 단계가 place를 무제목으로 만들 수 없다.
     */
    private int sweepStaleTranslations(Set<Long> keep) {
        Set<String> backed = new HashSet<>();
        for (Object[] pair : stagingPlaceRepository.findPlaceLangPairsReferencedByMatched(
                ResolutionStatus.MATCHED)) {
            backed.add(pair[0] + " " + pair[1]);
        }

        List<PlaceTranslation> stale = placeTranslationRepository.findByPlaceIdIn(keep).stream()
                .filter(t -> t.getSource() == TranslationSource.OFFICIAL)
                .filter(t -> !backed.contains(t.getPlaceId() + " " + t.getLang()))
                .toList();

        if (stale.isEmpty()) {
            return 0;
        }
        placeTranslationRepository.deleteAll(stale);
        return stale.size();
    }
}
