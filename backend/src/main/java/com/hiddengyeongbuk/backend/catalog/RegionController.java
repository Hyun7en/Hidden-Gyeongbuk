package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.RegionResponse;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionRepository regionRepository;

    public RegionController(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @GetMapping
    @Cacheable("regions")
    public List<RegionResponse> list(@RequestParam(required = false) Long hubId) {
        List<Region> regions = hubId != null ? regionRepository.findByHubId(hubId) : regionRepository.findAll();
        return regions.stream()
                .map(r -> new RegionResponse(r.getId(), r.getNameKo(), r.getNameEn(), r.getHub().getId(), r.getLat(), r.getLng()))
                .toList();
    }
}
