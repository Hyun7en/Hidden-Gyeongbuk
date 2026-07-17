package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.HubResponse;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hubs")
public class HubController {

    private final HubRepository hubRepository;

    public HubController(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    @GetMapping
    @Cacheable("hubs")
    public List<HubResponse> list() {
        return hubRepository.findAll().stream()
                .map(h -> new HubResponse(h.getId(), h.getNameKo(), h.getNameEn(), h.getLat(), h.getLng(), h.getDescription()))
                .toList();
    }
}
