package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.FestivalDetail;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetailRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceImage;
import com.hiddengyeongbuk.backend.domain.place.PlaceImageRepository;
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
import java.time.LocalDate;
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
    private final PlaceImageRepository placeImageRepository;
    private final FestivalDetailRepository festivalDetailRepository;

    public MergeWriter(
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository,
            StagingPlaceRepository stagingPlaceRepository,
            RegionRepository regionRepository,
            ContentTypeMapping contentTypes,
            PlaceImageRepository placeImageRepository,
            FestivalDetailRepository festivalDetailRepository) {
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.regionRepository = regionRepository;
        this.contentTypes = contentTypes;
        this.placeImageRepository = placeImageRepository;
        this.festivalDetailRepository = festivalDetailRepository;
    }

    public record Written(boolean placeCreated, int translations, boolean quarantined) {}

    /**
     * 같은 장소로 판정된 staging 그룹을 place 1건 + 언어별 place_translation 행으로 병합한다.
     *
     * @param group 첫 원소가 대표(앵커). 기준 언어가 있으면 그것이 앵커다
     */
    /**
     * 화면에 낼 수 있는 형태로 정리한 연락처. <b>소스가 이 칸에 전화번호만 넣지 않는다</b> —
     * 영문 축제는 "• 1330 Travel Hotline: +82-2-1330&lt;br&gt;(Korean, English, Japanese,
     * Chinese)" 같은 안내문을 넣는다(2026-08-19 실측 8건, 111~113자).
     *
     * <p>실측한 원문(봉화은어축제, 112자)은 <b>연락처 두 개</b>였다 — 1330 관광통역안내와
     * 축제사무국 번호를 {@code <br>}로 이어 붙였다. 같은 응답의 일반 항목은 15자
     * ({@code +82-54-683-9282})이니, 긴 값은 전화번호가 아니라 안내 블록이다.
     *
     * <p><b>{@code <br>}은 줄바꿈으로 남긴다.</b> 소스가 "여기서 끊어라"를 이미 알려준
     * 자리이므로 그 구분을 버리지 않는다 — 공백으로 이어 붙이면 103자 한 줄이 되어 읽을 수 없다.
     *
     * <p>원문은 {@code staging_place.tel}에 그대로 남는다(ADR-016 — 수집과 해석의 분리).
     * <b>전화번호를 골라내려 들지는 않는다</b> — 무엇이 번호이고 무엇이 설명인지 판정하는
     * 규칙을 우리가 소유하면 근거도 우리가 대야 한다. 잘라내지도 않는다(컬럼이 255자로 충분하다).
     */
    static String displayableTel(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("(?i)<br\\s*/?>", "\n")   // 소스가 끊으라고 한 자리
                .replaceAll("<[^>]*>", " ")                      // 나머지 태그는 지운다
                .replaceAll("[ \\t]+", " ")                       // 줄 안의 공백만 정리
                .replaceAll(" *\n *", "\n")                      // 줄 경계의 공백 제거
                .replaceAll("\n{2,}", "\n")                      // 빈 줄 접기
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 이미지 URL을 https로 맞춘다. <b>소스가 같은 호스트를 http와 https로 섞어 준다</b> —
     * 2026-08-19 실측으로 place 1,305건 중 685건(52.5%)이 {@code http://tong.visitkorea.or.kr}이고
     * 486건이 같은 호스트의 https였다.
     *
     * <p><b>이걸 그대로 두면 배포에서만 깨진다.</b> 로컬 개발은 http로 뜨니 이미지가 보이지만,
     * 배포는 https라 브라우저가 mixed content로 막아 카드 절반이 빈 이미지가 된다. 개발 중에는
     * 구조적으로 드러나지 않는 종류의 고장이라 수집 단계에서 미리 맞춰 둔다.
     *
     * <p>바꿔도 안전한 근거는 <b>같은 URL이 https에서도 동일하게 응답한다</b>는 것이다(실측:
     * 양쪽 200, {@code Content-Length} 481,259로 바이트까지 동일). 원문은 {@code staging_place}에
     * 그대로 남으므로 판단이 틀렸다면 이 메서드만 되돌리고 Merge를 다시 돌리면 된다.
     *
     * <p>호스트를 가리지 않고 http 전체를 올린다 — 지금 소스가 tong.visitkorea.or.kr 하나뿐이고,
     * 허용 목록을 두면 소스가 호스트를 바꿀 때 조용히 http로 되돌아간다.
     */
    static String httpsImageUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.regionMatches(true, 0, "http://", 0, 7) ? "https://" + trimmed.substring(7) : trimmed;
    }

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

        applyImages(saved, group);
        applyFestivalPeriod(saved, group);

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
        place.setTel(displayableTel(first(group, StagingPlace::getTel)));
        place.setFirstImage(httpsImageUrl(first(group, StagingPlace::getFirstImage)));
        // lcls_systm2/3은 place에 두지 않는다 — 원본이 staging에 남아 있고, 세분 필터가
        // 필요해지면 컬럼을 추가하고 Merge를 재실행하면 된다(ADR-024).
    }

    /**
     * 추가 사진을 {@code place_image}로 정규화한다. 소스가 준 순서를 그대로 {@code sort_order}에 넣는다 —
     * 무엇이 대표 사진인지 판정할 근거가 우리에게 없으므로 순서를 재정의하지 않는다.
     *
     * <p><b>매번 지우고 다시 넣는다.</b> 목록이 줄었을 때(소스가 사진을 내렸을 때) 남은 행을
     * 지울 다른 경로가 없기 때문이다. 무료 로컬 연산이라 아낄 이유도 없다.
     *
     * <p><b>delete 직후 flush()가 필수다.</b> 안 하면 Hibernate가 insert를 먼저 내보내
     * {@code UNIQUE(place_id, image_url)} 위반이 난다 — 같은 URL이 그대로 다시 들어오는 것이
     * 정상 경로라 반드시 걸린다. (구 태그 재부여에서 이미 겪은 함정이다.)
     *
     * <p>URL은 {@link #httpsImageUrl}로 https에 맞춘다 — first_image와 같은 이유다.
     *
     * <p><b>한 언어만 쓰지 않고 그룹 전체를 합친다.</b> 좌표·전화번호 같은 단일 값은 "앵커에
     * 없으면 다른 언어에서 가져온다"(결측 보완)로 충분하지만, 사진은 <b>목록</b>이라 같은 규칙을
     * 쓰면 한쪽을 통째로 버리게 된다. 국문·영문은 contentId가 다른 별개 레코드라 사진 목록도
     * 다르다 — 2026-08-20 실측: 두 언어 다 사진이 있는 82곳 중 <b>49곳(60%)이 개수부터 달랐다.</b>
     *
     * <p>중복은 URL 문자열로 거른다. 같은 사진이 다른 경로로 오면 못 거르지만, 그건 비슷한 장이
     * 둘 보이는 정도라 사진을 통째로 잃는 것보다 가볍다.
     */
    private void applyImages(Place place, List<StagingPlace> group) {
        // 언어별 목록을 합친다. 앵커(기준 언어)가 앞이라 국문 사진이 먼저 오고 다른 언어의
        // 추가분이 뒤에 붙는다.
        List<String> urls = group.stream()
                .map(StagingPlace::getDetailImages)
                .filter(raw -> raw != null && !raw.isBlank())
                .flatMap(raw -> java.util.Arrays.stream(raw.split("\n")))
                .map(MergeWriter::httpsImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();

        List<PlaceImage> existing = placeImageRepository.findByPlaceIdOrderBySortOrder(place.getId());
        if (existing.isEmpty() && urls.isEmpty()) {
            return;
        }
        placeImageRepository.deleteAll(existing);
        placeImageRepository.flush();

        int order = 0;
        for (String url : urls) {
            placeImageRepository.save(PlaceImage.builder()
                    .place(place)
                    .imageUrl(url)
                    .sortOrder(order++)
                    .build());
        }
    }

    /**
     * 축제 개최 기간을 {@code festival_detail}로 옮긴다. 추천의 <b>유일한 계절 반영 근거</b>다
     * (추천 알고리즘 §4.7) — 여행 기간에 안 열리는 축제를 후보에서 빼고, 겹치면 사전점수에 가산한다.
     *
     * <p><b>날짜가 없으면 행을 만들지 않는다.</b> 결측을 "안 열린다"로 읽으면 수집된 콘텐츠를
     * 근거 없이 잃고, "열린다"로 읽으면 닫힌 축제를 일정에 넣는다. 모르는 것은 판단하지 않는
     * 쪽이 맞고, 행이 없으면 추천이 일반 장소처럼 다룬다(ADR-021).
     *
     * <p>축제가 아니게 됐거나 날짜가 사라지면 기존 행을 지운다 — 안 지우면 낡은 기간이 남는다.
     *
     * <p><b>{@code @MapsId}라 ID를 직접 세팅하지 않는다.</b> 연관 엔티티만 넣으면 자동으로
     * 채워지며, 직접 넣으면 merge가 update를 시도하다 낙관적 락 예외가 난다(CLAUDE.md 함정).
     */
    private void applyFestivalPeriod(Place place, List<StagingPlace> group) {
        boolean festival = place.getPlaceType() == PlaceType.EV;
        LocalDate start = festival ? first(group, StagingPlace::getEventStartDate) : null;
        LocalDate end = festival ? first(group, StagingPlace::getEventEndDate) : null;

        Optional<FestivalDetail> existing = festivalDetailRepository.findById(place.getId());
        if (start == null || end == null) {
            existing.ifPresent(festivalDetailRepository::delete);
            return;
        }

        FestivalDetail detail = existing.orElseGet(
                () -> FestivalDetail.builder().place(place).build());
        detail.setStartDate(start);
        detail.setEndDate(end);
        festivalDetailRepository.save(detail);
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
