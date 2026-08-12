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
