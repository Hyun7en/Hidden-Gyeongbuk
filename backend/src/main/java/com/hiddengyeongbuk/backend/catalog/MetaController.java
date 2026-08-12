package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final MetaQueryService metaQueryService;
    private final LanguageRegistry languages;

    public MetaController(MetaQueryService metaQueryService, LanguageRegistry languages) {
        this.metaQueryService = metaQueryService;
        this.languages = languages;
    }

    @GetMapping("/options")
    public MetaOptionsResponse options(@RequestParam(required = false) String lang) {
        return metaQueryService.options(languages.resolve(lang));
    }
}
