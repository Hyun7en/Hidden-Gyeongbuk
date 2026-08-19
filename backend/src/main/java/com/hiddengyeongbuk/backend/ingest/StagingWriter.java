package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    /** TourAPI eventstartdate/eventenddate 형식: 20260925 */
    private static final DateTimeFormatter EVENT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

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
            // 원본이 바뀌었으면 사진과 개최 기간도 바뀌었을 수 있다. "물어봤다" 표시를 지워
            // 다시 묻게 한다 — overview와 같은 규칙이다.
            staging.setImageFetchedAt(null);
            staging.setIntroFetchedAt(null);
        }

        StagingPlace saved = stagingPlaceRepository.save(staging);
        return new UpsertResult(
                saved.getId(),
                !unchanged,
                Integer.valueOf(0).equals(saved.getShowFlag()),
                saved.getImageFetchedAt() != null,
                saved.getIntroFetchedAt() != null,
                saved.getSourceTypeId());
    }

    /**
     * upsert 결과. <b>여기서 판정하지 않고 사실만 돌려준다</b> — 무엇을 더 부를지는 수집 예산을
     * 아는 Collector가 정한다(설정으로 축제 타입을 아는 쪽도 거기다).
     *
     * @param changed 원본 modifiedtime이 달라졌는가. 상세(overview) 재조회 여부를 가른다
     * @param hiddenAtSource {@code showflag=0} — 소스가 내린 장소. 추가 호출을 아낄 근거다
     * @param imageAlreadyFetched detailImage2를 이미 물어봤는가(받은 개수와 무관)
     * @param introAlreadyFetched detailIntro2를 이미 물어봤는가
     * @param sourceTypeId 그 언어 서비스의 contentTypeId 원본. detailIntro2가 필수로 요구한다
     */
    public record UpsertResult(
            Long stagingId,
            boolean changed,
            boolean hiddenAtSource,
            boolean imageAlreadyFetched,
            boolean introAlreadyFetched,
            Integer sourceTypeId) {}

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

    /**
     * detailImage2 결과를 담고 <b>물어봤다는 표시</b>를 남긴다.
     *
     * <p><b>0건이어도 표시를 남기는 것이 요점이다.</b> 개수로 판단하면 이미지가 없는 장소를 매
     * 수집마다 영원히 다시 묻게 된다 — 응답을 못 받은 것과 응답은 왔는데 비어 있는 것은 다르고,
     * 후자는 정상 결과다. 이 구분을 놓쳐서 overview에서 이미 한 번 데였다.
     *
     * <p>실패했을 때는 <b>아예 호출되지 않는다</b>(호출부가 catch한다). 그래서 표시가 없으면
     * "못 받았다"가 되어 다음 수집에서 자동 재시도된다.
     */
    @Transactional
    public void applyImages(Long stagingId, List<String> imageUrls) {
        stagingPlaceRepository.findById(stagingId).ifPresent(staging -> {
            staging.setDetailImages(imageUrls.isEmpty() ? null : String.join("\n", imageUrls));
            staging.setImageFetchedAt(LocalDateTime.now());
            stagingPlaceRepository.save(staging);
        });
    }

    /**
     * detailIntro2로 받은 축제 개최 기간을 담고 물어봤다는 표시를 남긴다.
     *
     * <p>날짜가 비어 있어도 표시는 남긴다 — 소스가 기간을 안 준 축제가 실재하고, 그건 정상
     * 결과다. 추천은 <b>날짜 없는 행사를 판단하지 않고 일반 장소처럼 다룬다</b>(ADR-021) —
     * 결측을 "안 열린다"로 읽으면 수집된 콘텐츠를 근거 없이 잃는다.
     */
    @Transactional
    public void applyEventPeriod(Long stagingId, String rawStart, String rawEnd) {
        stagingPlaceRepository.findById(stagingId).ifPresent(staging -> {
            staging.setEventStartDate(parseEventDate(rawStart));
            staging.setEventEndDate(parseEventDate(rawEnd));
            staging.setIntroFetchedAt(LocalDateTime.now());
            stagingPlaceRepository.save(staging);
        });
    }

    /**
     * {@code yyyyMMdd} → {@code LocalDate}. 못 읽으면 null이다.
     *
     * <p>소스가 {@code "0"}이나 빈 값을 주는 경우가 있어 예외로 올리지 않는다 — 그 한 건 때문에
     * 항목 전체를 실패로 만들면 사진·설명까지 같이 잃는다.
     */
    private static LocalDate parseEventDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), EVENT_DATE);
        } catch (Exception e) {
            return null;
        }
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
