package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * staging_place 쓰기 전담. <b>Collector와 별도 빈</b>인 이유는 트랜잭션을 항목 단위로 끊기
 * 위해서다 — 같은 클래스 안에서 메서드를 나누면 self-invocation이라 {@code @Transactional}
 * AOP가 걸리지 않는다.
 *
 * <p>수집 루프 전체를 트랜잭션 하나로 묶으면 외부 HTTP를 기다리는 동안 DB 커넥션을 점유하고,
 * 중간에 DB 오류 하나가 rollback-only를 걸어 지역 전체를 날린다. 항목마다 독립 커밋해야
 * per-item try/catch가 실제로 격리 역할을 한다.
 */
@Component
public class StagingWriter {

    /** TourAPI modifiedtime 형식: 20260812153000 */
    private static final DateTimeFormatter MODIFIED_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StagingPlaceRepository stagingPlaceRepository;

    public StagingWriter(StagingPlaceRepository stagingPlaceRepository) {
        this.stagingPlaceRepository = stagingPlaceRepository;
    }

    /**
     * 자연키 (lang, sourceContentId) 기준 upsert. 배치를 몇 번 돌려도 결과가 같아야
     * Resolve·Merge 재실행이 의미를 갖는다(아키텍처 §5.1 멱등성).
     *
     * @return 저장된 행의 id와 "원본이 실제로 바뀌었는지" — 후자가 상세 조회 필요 여부를 가른다
     */
    @Transactional
    public UpsertResult upsert(String lang, String regnCd, String signguCd, AreaBasedSyncItem item) {
        LocalDateTime modifiedAt = parseModifiedTime(item.modifiedtime());

        StagingPlace staging = stagingPlaceRepository
                .findByLangAndSourceContentId(lang, item.contentid())
                .orElseGet(() -> StagingPlace.builder()
                        .lang(lang)
                        .sourceContentId(item.contentid())
                        .build());

        boolean unchanged = staging.getId() != null
                && modifiedAt != null
                && modifiedAt.equals(staging.getSourceModifiedAt());

        staging.setSourceTypeId(parseInt(item.contenttypeid()));
        staging.setLdongRegnCd(regnCd);
        staging.setLdongSignguCd(signguCd);
        staging.setTitle(item.title());
        staging.setAddr(item.addr1());
        staging.setLat(GeoUtils.parseCoordinate(item.mapy()));
        staging.setLng(GeoUtils.parseCoordinate(item.mapx()));
        staging.setTel(item.tel());
        staging.setFirstImage(item.firstimage());
        // unchanged 여부와 무관하게 매번 쓴다 — 소스가 장소를 내리는 것은 modifiedtime을 바꾸지
        // 않을 수 있고, 이 값은 무료 로컬 갱신이라 아낄 이유도 없다.
        staging.setShowFlag(parseInt(item.showflag()));
        staging.setLclsSystm1(item.lclsSystm1());
        staging.setLclsSystm2(item.lclsSystm2());
        staging.setLclsSystm3(item.lclsSystm3());
        staging.setSourceModifiedAt(modifiedAt);
        staging.setCollectedAt(LocalDateTime.now());

        // 원본이 바뀌었으면 이전 판정을 신뢰할 수 없다 — 좌표·이름이 달라졌을 수 있으므로
        // PENDING으로 되돌려 다음 Resolve가 다시 판정하게 한다. 안 바뀌었으면 판정을 보존해
        // 이미 병합된 장소를 불필요하게 다시 처리하지 않는다.
        if (!unchanged) {
            staging.setResolutionStatus(ResolutionStatus.PENDING);
            staging.setQuarantineReason(null);
        }

        StagingPlace saved = stagingPlaceRepository.save(staging);
        return new UpsertResult(saved.getId(), !unchanged);
    }

    public record UpsertResult(Long stagingId, boolean changed) {}

    /**
     * 워터마크를 지워 다음 수집이 이 행을 "바뀐 항목"으로 다시 보게 한다. 상세(detailCommon2)를
     * <b>못 받았을 때</b> 호출한다.
     *
     * <p>이게 없으면 상세 실패가 영구 손실이 된다. {@link #upsert}는 별도 트랜잭션이라 상세를
     * 부르기 전에 이미 커밋되고, 그때 워터마크도 함께 저장된다. 그래서 상세가 실패해도 다음
     * 수집에서는 unchanged로 판정돼 재시도 대상에서 빠진다 — overview가 영영 빈 채로 남는다.
     *
     * <p>그 결과가 특히 나쁜 이유는 <b>원본에 설명이 없는 장소와 구분되지 않기</b> 때문이다.
     * 관광공사가 음식점에 overview를 거의 주지 않아(실측 FD 8%) 화면은 "설명 없음"을 정상
     * 상태로 다루는데, 여기 섞이면 우리 실패를 원본 탓으로 표시하게 된다. 추천의
     * PlaceCompleteness 점수도 같이 깎인다.
     */
    @Transactional
    public void clearWatermark(Long stagingId) {
        stagingPlaceRepository.findById(stagingId).ifPresent(staging -> {
            staging.setSourceModifiedAt(null);
            stagingPlaceRepository.save(staging);
        });
    }

    /** 상세(detailCommon2)로 받은 overview를 채운다. 목록 응답에는 overview가 없다. */
    @Transactional
    public void applyOverview(Long stagingId, String overview) {
        stagingPlaceRepository.findById(stagingId).ifPresent(staging -> {
            staging.setOverview(overview);
            stagingPlaceRepository.save(staging);
        });
    }

    private static LocalDateTime parseModifiedTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), MODIFIED_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
