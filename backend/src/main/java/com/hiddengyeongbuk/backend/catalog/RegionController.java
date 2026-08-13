package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.RegionResponse;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import com.hiddengyeongbuk.backend.catalog.dto.RegionDetailResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionQueryService regionQueryService;
    private final LanguageRegistry languages;

    public RegionController(RegionQueryService regionQueryService, LanguageRegistry languages) {
        this.regionQueryService = regionQueryService;
        this.languages = languages;
    }

    @GetMapping
    public List<RegionResponse> list(
            @RequestParam(required = false) Long hubId, @RequestParam(required = false) String lang) {
        return regionQueryService.list(hubId, languages.resolve(lang));
    }

    /** 지역 상세 — 지역 소개 화면(S2)용(API 명세서 §2.5, ADR-019). */
    @GetMapping("/{regionId}")
    public RegionDetailResponse detail(
            @PathVariable Long regionId, @RequestParam(required = false) String lang) {
        return regionQueryService.getDetail(regionId, languages.resolve(lang));
    }
}
