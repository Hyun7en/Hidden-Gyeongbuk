package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.RegionDetailResponse;
import com.hiddengyeongbuk.backend.catalog.dto.RegionResponse;
import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import com.hiddengyeongbuk.backend.domain.region.RegionTranslation;
import com.hiddengyeongbuk.backend.domain.region.RegionTranslationRepository;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역 조회(API 명세서 §2.2). {@code @Cacheable}이 프록시 기반이라 컨트롤러와 분리했다.
 */
@Service
@Transactional(readOnly = true)
public class RegionQueryService {

    /** 지역 대표 이미지로 빌려올 장소 이미지 개수. */
    private static final int REGION_IMAGE_LIMIT = 3;

    private final RegionRepository regionRepository;
    private final RegionTranslationRepository regionTranslationRepository;
    private final PlaceRepository placeRepository;

    public RegionQueryService(
            RegionRepository regionRepository,
            RegionTranslationRepository regionTranslationRepository,
            PlaceRepository placeRepository) {
        this.regionRepository = regionRepository;
        this.regionTranslationRepository = regionTranslationRepository;
        this.placeRepository = placeRepository;
    }

    /**
     * 인구감소지역 목록. {@code active=true}인 지역만 반환한다 — 지정이 해제된 지역은
     * 데이터를 지우지 않고 플래그만 내리므로, 여기서 걸러야 한다(NFR-06).
     *
     * <p>{@code hubId}를 받으면 그 거점 소속 지역을 상단에 우선 정렬한다(ADR-019). 제외가
     * 아니라 정렬인 이유는, 거점을 골라도 다른 지역을 못 보게 막을 이유가 없어서다.
     */
    @Cacheable("regions")
    public List<RegionResponse> list(Long hubId, String lang) {
        Map<Long, RegionTranslation> requested = byRegionId(lang);
        Map<Long, RegionTranslation> base =
                LanguageRegistry.BASE.equals(lang) ? requested : byRegionId(LanguageRegistry.BASE);

        List<Region> regions = regionRepository.findByActiveTrue();
        Map<Long, String> images = representativeImages(regions);

        return regions.stream()
                .sorted(hubPriority(hubId))
                .map(region -> toResponse(
                        region,
                        requested.get(region.getId()),
                        base.get(region.getId()),
                        images.get(region.getId())))
                .toList();
    }

    /**
     * 지역별 대표 이미지 — 그 지역 장소의 이미지를 하나 빌려온다(API 명세서 §2.2가 "정적 에셋
     * 또는 대표 장소 이미지"를 모두 허용한다). 상세 조회(§2.5)가 이미 같은 방식을 쓰고 있었는데
     * 목록만 {@code null}로 남아 있어, <b>홈의 지역 카드 15장이 전부 빈 이미지</b>였다.
     *
     * <p><b>지역마다 따로 조회하지 않는다</b> — 15번 쿼리가 나가는 N+1이다. 한 번에 읽어
     * 지역별로 접는다.
     *
     * <p>고르는 기준은 <b>PK가 가장 작은 장소</b>다. 임의로 보이지만 요점은 결정적이라는 것이다 —
     * 이 응답이 캐시되므로 호출마다 다른 사진이 나오면 화면이 이유 없이 깜빡인다. "대표성"을
     * 판정할 근거 데이터가 없는 상태에서 안정성을 택했다.
     */
    private Map<Long, String> representativeImages(List<Region> regions) {
        List<Long> regionIds = regions.stream().map(Region::getId).toList();
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        return placeRepository.findByRegionIdIn(regionIds).stream()
                .filter(p -> p.getFirstImage() != null && !p.getFirstImage().isBlank())
                .sorted(Comparator.comparing(Place::getId))
                .collect(Collectors.toMap(
                        p -> p.getRegion().getId(), Place::getFirstImage, (first, later) -> first));
    }

    /**
     * 지역 상세(API 명세서 §2.5). 소개 글은 요청 언어 → 기준 언어 순으로 폴백한다.
     *
     * <p>{@code images}는 아직 지역 전용 에셋이 없어 <b>그 지역 장소의 대표 이미지를 빌려온다</b>
     * — 명세가 "정적 에셋 또는 대표 장소 이미지"를 모두 허용한다. 전용 에셋이 생기면 이 부분만
     * 바뀐다.
     */
    @Cacheable("regionDetail")
    public RegionDetailResponse getDetail(Long regionId, String lang) {
        Region region = regionRepository
                .findById(regionId)
                .filter(Region::isActive)
                .orElseThrow(() -> new RegionNotFoundException(regionId));

        RegionTranslation requested =
                regionTranslationRepository.findByRegionIdAndLang(regionId, lang).orElse(null);
        RegionTranslation base = requested != null
                ? requested
                : regionTranslationRepository
                        .findByRegionIdAndLang(regionId, LanguageRegistry.BASE)
                        .orElse(null);
        RegionTranslation chosen = requested != null ? requested : base;

        List<String> images = placeRepository.findByRegionIdIn(List.of(regionId)).stream()
                .map(Place::getFirstImage)
                .filter(url -> url != null && !url.isBlank())
                .limit(REGION_IMAGE_LIMIT)
                .toList();

        return new RegionDetailResponse(
                region.getId(),
                chosen != null ? chosen.getName() : null,
                region.getHub().getId(),
                region.getLat(),
                region.getLng(),
                chosen != null ? chosen.getDescription() : null,
                images,
                chosen != null ? chosen.getLang() : null);
    }

    /** 거점 소속을 앞으로, 그 안에서는 PK 순으로 — 정렬이 결정적이어야 캐시 결과가 안정적이다. */
    private static Comparator<Region> hubPriority(Long hubId) {
        if (hubId == null) {
            return Comparator.comparing(Region::getId);
        }
        return Comparator.comparing((Region r) -> !hubId.equals(r.getHub().getId()))
                .thenComparing(Region::getId);
    }

    private Map<Long, RegionTranslation> byRegionId(String lang) {
        return regionTranslationRepository.findByLang(lang).stream()
                .collect(Collectors.toMap(RegionTranslation::getRegionId, Function.identity(), (a, b) -> a));
    }

    private static RegionResponse toResponse(
            Region region, RegionTranslation requested, RegionTranslation base, String firstImage) {
        RegionTranslation chosen = requested != null ? requested : base;
        return new RegionResponse(
                region.getId(),
                chosen != null ? chosen.getName() : null,
                region.getHub().getId(),
                region.getLat(),
                region.getLng(),
                firstImage,
                chosen != null ? chosen.getLang() : null);
    }
}
