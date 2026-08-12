package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.RegionResponse;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
}
