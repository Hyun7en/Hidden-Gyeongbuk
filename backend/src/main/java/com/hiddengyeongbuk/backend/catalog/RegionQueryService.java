package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.RegionResponse;
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

    private final RegionRepository regionRepository;
    private final RegionTranslationRepository regionTranslationRepository;

    public RegionQueryService(
            RegionRepository regionRepository, RegionTranslationRepository regionTranslationRepository) {
        this.regionRepository = regionRepository;
        this.regionTranslationRepository = regionTranslationRepository;
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

        return regionRepository.findByActiveTrue().stream()
                .sorted(hubPriority(hubId))
                .map(region -> toResponse(region, requested.get(region.getId()), base.get(region.getId())))
                .toList();
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
            Region region, RegionTranslation requested, RegionTranslation base) {
        RegionTranslation chosen = requested != null ? requested : base;
        return new RegionResponse(
                region.getId(),
                chosen != null ? chosen.getName() : null,
                region.getHub().getId(),
                region.getLat(),
                region.getLng(),
                // 지역 대표 이미지는 아직 소스가 없다 — 정적 에셋 또는 대표 장소 이미지를
                // 붙이는 것이 후속 과제다(API 명세서 §2.2).
                null,
                chosen != null ? chosen.getLang() : null);
    }
}
