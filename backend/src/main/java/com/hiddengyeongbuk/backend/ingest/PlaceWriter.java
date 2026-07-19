package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTag;
import com.hiddengyeongbuk.backend.domain.place.PlaceTagRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailCommonItem;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * place 저장 + 태그 재부여를 "항목 하나 = 트랜잭션 하나"로 묶는다.
 *
 * PlaceCollectionService에서 굳이 별도 빈으로 분리한 이유:
 * 수집 루프는 항목마다 TourAPI(국문·영문 상세)와 번역 API를 부르는 네트워크 I/O 덩어리라,
 * 루프 전체를 @Transactional로 감싸면 DB 커넥션 하나를 수 분~수십 분씩 붙든 채 외부 HTTP
 * 응답을 기다리게 된다(커넥션 풀 고갈·롱 트랜잭션). 게다가 지역 전체가 트랜잭션 하나가 되면
 * 중간 한 건의 DB 오류가 participation failure로 트랜잭션을 rollback-only로 만들어,
 * 항목별 try/catch로 예외를 삼켜도 최종 커밋에서 UnexpectedRollbackException이 터지며
 * 그 지역 작업 전체(수집 로그까지)가 사라진다 — 잡았으니 안전하다는 건 착시였다.
 *
 * 그렇다고 같은 클래스 안에서 항목 단위 메서드에 @Transactional을 달아봐야 self-invocation이라
 * Spring AOP 프록시를 안 거쳐 무시된다(PlaceTranslationService.backfill의 같은 함정 참고).
 * 별도 빈으로 빼서 프록시를 태워야 항목마다 진짜로 독립 커밋된다.
 */
@Component
class PlaceWriter {

    private static final String LANG_EN = "en";

    private final PlaceRepository placeRepository;
    private final PlaceTagRepository placeTagRepository;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final PlaceTagAssigner placeTagAssigner;

    PlaceWriter(
            PlaceRepository placeRepository,
            PlaceTagRepository placeTagRepository,
            PlaceTranslationRepository placeTranslationRepository,
            PlaceTagAssigner placeTagAssigner) {
        this.placeRepository = placeRepository;
        this.placeTagRepository = placeTagRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.placeTagAssigner = placeTagAssigner;
    }

    /**
     * 상세를 새로 받아온 place를 저장하고 태그를 다시 부여한다.
     *
     * 저장된(영속) 인스턴스를 돌려주는 이유: 이미 저장돼 있던 place는 detached 상태로 들어와
     * save()가 merge()로 처리하는데, 이때 영속 인스턴스는 인자와 다른 복사본이다. 호출부가
     * 이후 단계(영문 번역)에서 인자를 계속 쓰면 헷갈리므로 항상 반환값을 쓰게 한다.
     */
    @Transactional
    Place savePlaceWithTags(Place place) {
        Place saved = placeRepository.save(place);
        assignTags(saved);
        return saved;
    }

    /**
     * 상세 재조회(TourAPI 호출)를 건너뛴 unchanged place의 태그를 다시 부여하고, 분류체계 코드도
     * 목록값으로 갱신한다. 태그는 매번 무료로 재계산하는 로컬 연산이고, lclsSystm 코드도 목록
     * 응답에 함께 오는 무료 데이터라 같은 원칙으로 최신화한다 — 단, 값이 바뀐 경우에만 place를
     * 저장해 불필요한 UPDATE를 피한다.
     */
    @Transactional
    void reassignTagsAndClassification(Place place, String lclsSystm1, String lclsSystm2, String lclsSystm3) {
        assignTags(place);
        if (!classificationEquals(place, lclsSystm1, lclsSystm2, lclsSystm3)) {
            place.setLclsSystm1(lclsSystm1);
            place.setLclsSystm2(lclsSystm2);
            place.setLclsSystm3(lclsSystm3);
            placeRepository.save(place);
        }
    }

    /**
     * 이름·좌표 매칭으로 찾은 공식 영문(EngService2 detailCommon2)을 place_translation의 en 행에
     * upsert한다. 기존에 번역(TRANSLATE) 행이 있었다면 같은 (place_id, en) 행을 공식(OFFICIAL)으로
     * 덮어쓴다 — 별도 삭제가 필요 없다. place.has_en은 "en 번역 존재" 캐시 플래그로 true로 둔다.
     */
    @Transactional
    Place applyEnglishOfficial(
            Place place, DetailCommonItem engDetail, String engContentId, LocalDateTime engModifiedAt) {
        place.setHasEn(true);
        Place saved = placeRepository.save(place);
        PlaceTranslation translation = placeTranslationRepository
                .findByPlaceIdAndLang(saved.getId(), LANG_EN)
                .orElseGet(() -> PlaceTranslation.builder()
                        .placeId(saved.getId())
                        .lang(LANG_EN)
                        .build());
        translation.setTitle(engDetail.title());
        translation.setOverview(engDetail.overview());
        translation.setAddr(engDetail.addr1());
        translation.setSource(TranslationSource.OFFICIAL);
        translation.setSrcContentId(engContentId);
        translation.setSrcModifiedAt(engModifiedAt);
        translation.setGeneratedAt(LocalDateTime.now());
        placeTranslationRepository.save(translation);
        return saved;
    }

    private static boolean classificationEquals(Place place, String lclsSystm1, String lclsSystm2, String lclsSystm3) {
        return Objects.equals(place.getLclsSystm1(), lclsSystm1)
                && Objects.equals(place.getLclsSystm2(), lclsSystm2)
                && Objects.equals(place.getLclsSystm3(), lclsSystm3);
    }

    // 태그는 delete → insert라 반드시 한 트랜잭션 안에서 원자적으로 처리해야 한다.
    private void assignTags(Place place) {
        Set<String> tags = placeTagAssigner.assign(place.getCategory(), place.getTitleKo(), place.getOverviewKo());
        placeTagRepository.deleteByPlaceId(place.getId());
        // flush 없이 바로 insert하면 Hibernate가 같은 트랜잭션 내 insert를 delete보다 먼저
        // 내보내 uk_place_tag(place_id, tag) 유니크 제약을 어길 수 있다(같은 태그 재부여 시).
        placeTagRepository.flush();
        for (String tag : tags) {
            placeTagRepository.save(PlaceTag.builder().place(place).tag(tag).build());
        }
    }
}
