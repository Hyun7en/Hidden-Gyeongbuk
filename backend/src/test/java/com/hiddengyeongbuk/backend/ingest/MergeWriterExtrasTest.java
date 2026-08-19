package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

import com.hiddengyeongbuk.backend.domain.place.FestivalDetail;
import com.hiddengyeongbuk.backend.domain.place.FestivalDetailRepository;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceImage;
import com.hiddengyeongbuk.backend.domain.place.PlaceImageRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Merge가 {@code place_image}·{@code festival_detail}을 채우는 부분.
 *
 * <p>두 테이블은 스키마와 조회 코드만 있고 <b>쓰는 코드가 없어 계속 0행</b>이었다. 그 결과 장소
 * 상세의 사진이 목록이 준 한 장뿐이었고, 축제 날짜 겹침(추천 §4.7이 규정한 유일한 계절 반영
 * 근거)이 한 번도 동작한 적이 없다.
 *
 * <p>여기서 지키는 것은 CLAUDE.md가 함정으로 적어 둔 둘이다 — delete 후 같은 트랜잭션 재삽입 시
 * {@code flush()}, 그리고 {@code @MapsId} 연관에 ID를 직접 세팅하지 않기.
 */
class MergeWriterExtrasTest {

    private static final long PLACE_ID = 7L;

    private final PlaceRepository places = Mockito.mock(PlaceRepository.class);
    private final PlaceTranslationRepository translations = Mockito.mock(PlaceTranslationRepository.class);
    private final StagingPlaceRepository staging = Mockito.mock(StagingPlaceRepository.class);
    private final RegionRepository regions = Mockito.mock(RegionRepository.class);
    private final ContentTypeMapping contentTypes = Mockito.mock(ContentTypeMapping.class);
    private final PlaceImageRepository images = Mockito.mock(PlaceImageRepository.class);
    private final FestivalDetailRepository festivals = Mockito.mock(FestivalDetailRepository.class);

    private final MergeWriter writer = new MergeWriter(
            places, translations, staging, regions, contentTypes, images, festivals);

    /** lclsSystm1을 직접 주면 분류 폴백 경로를 타지 않는다 — 여기서 보려는 건 그게 아니다. */
    private static StagingPlace row(String lclsSystm1, String detailImages, LocalDate start, LocalDate end) {
        StagingPlace s = StagingPlace.builder()
                .lang("ko")
                .sourceContentId("c1")
                .sourceTypeId(15)
                .ldongRegnCd("47")
                .ldongSignguCd("170")
                .title("봉화은어축제")
                .lclsSystm1(lclsSystm1)
                .build();
        s.setDetailImages(detailImages);
        s.setEventStartDate(start);
        s.setEventEndDate(end);
        return s;
    }

    private void stubCommon() {
        Region region = Region.builder().ldongRegnCd("47").ldongSignguCd("170").build();
        BDDMockito.given(regions.findByLdongRegnCdAndLdongSignguCd("47", "170")).willReturn(Optional.of(region));
        BDDMockito.given(places.save(any(Place.class))).willAnswer(inv -> {
            Place p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", PLACE_ID);
            return p;
        });
        BDDMockito.given(translations.findByPlaceIdAndLang(anyLong(), any())).willReturn(Optional.empty());
        BDDMockito.given(images.findByPlaceIdOrderBySortOrder(PLACE_ID)).willReturn(List.of());
        BDDMockito.given(festivals.findById(PLACE_ID)).willReturn(Optional.empty());
    }

    @Test
    void 사진을_소스가_준_순서대로_담고_https로_올린다() {
        stubCommon();
        writer.write(List.of(row("EV", "http://a/1.jpg\nhttps://a/2.jpg", null, null)));

        ArgumentCaptor<PlaceImage> captor = ArgumentCaptor.forClass(PlaceImage.class);
        BDDMockito.then(images).should(Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PlaceImage::getImageUrl, PlaceImage::getSortOrder)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("https://a/1.jpg", 0),
                        org.assertj.core.api.Assertions.tuple("https://a/2.jpg", 1));
    }

    @Test
    void 기존_사진을_지운_뒤_반드시_flush하고_다시_넣는다() {
        // 같은 URL이 그대로 다시 들어오는 것이 정상 경로라, flush 없이는 Hibernate가 insert를
        // 먼저 내보내 UNIQUE(place_id, image_url) 위반이 난다(CLAUDE.md 함정).
        stubCommon();
        PlaceImage old = PlaceImage.builder().imageUrl("https://a/1.jpg").sortOrder(0).build();
        BDDMockito.given(images.findByPlaceIdOrderBySortOrder(PLACE_ID)).willReturn(List.of(old));

        writer.write(List.of(row("EV", "https://a/1.jpg", null, null)));

        InOrder order = Mockito.inOrder(images);
        order.verify(images).deleteAll(List.of(old));
        order.verify(images).flush();
        order.verify(images).save(any(PlaceImage.class));
    }

    @Test
    void 사진이_없으면_지우지도_넣지도_않는다() {
        stubCommon();
        writer.write(List.of(row("EV", null, null, null)));

        BDDMockito.then(images).should(Mockito.never()).deleteAll(any());
        BDDMockito.then(images).should(Mockito.never()).save(any());
    }

    @Test
    void 축제_기간을_담을_때_ID를_직접_세팅하지_않는다() {
        // @MapsId라 연관 엔티티만 넣으면 자동으로 채워진다. 직접 넣으면 merge가 update를
        // 시도하다 낙관적 락 예외가 난다(CLAUDE.md 함정).
        stubCommon();
        writer.write(List.of(row("EV", null, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27))));

        ArgumentCaptor<FestivalDetail> captor = ArgumentCaptor.forClass(FestivalDetail.class);
        BDDMockito.then(festivals).should().save(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(captor.getValue().getPlace()).isNotNull();
        assertThat(captor.getValue().getPlaceId()).isNull();
    }

    @Test
    void 날짜가_없는_축제는_행을_만들지_않는다() {
        // 결측을 "안 열린다"로 읽으면 콘텐츠를 근거 없이 잃고, "열린다"로 읽으면 닫힌 축제를
        // 일정에 넣는다. 행이 없으면 추천이 일반 장소처럼 다룬다(ADR-021).
        stubCommon();
        writer.write(List.of(row("EV", null, null, null)));

        BDDMockito.then(festivals).should(Mockito.never()).save(any());
    }

    @Test
    void 축제가_아니면_기존_기간_행을_지운다() {
        // 분류가 바뀌었는데 안 지우면 낡은 기간이 남아 추천이 그걸 근거로 판단한다.
        stubCommon();
        FestivalDetail stale = FestivalDetail.builder().startDate(LocalDate.now()).build();
        BDDMockito.given(festivals.findById(PLACE_ID)).willReturn(Optional.of(stale));

        writer.write(List.of(row("NA", null, LocalDate.now(), LocalDate.now())));

        BDDMockito.then(festivals).should().delete(stale);
        BDDMockito.then(festivals).should(Mockito.never()).save(any());
    }
}
