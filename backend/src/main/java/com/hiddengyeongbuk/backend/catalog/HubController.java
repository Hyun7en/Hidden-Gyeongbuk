package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.HubResponse;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hubs")
public class HubController {

    private final HubQueryService hubQueryService;
    private final LanguageRegistry languages;

    public HubController(HubQueryService hubQueryService, LanguageRegistry languages) {
        this.hubQueryService = hubQueryService;
        this.languages = languages;
    }

    @GetMapping
    public List<HubResponse> list(@RequestParam(required = false) String lang) {
        return hubQueryService.list(languages.resolve(lang));
    }
}
