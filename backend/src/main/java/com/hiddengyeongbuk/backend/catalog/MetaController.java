package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse;
import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse.Option;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final HubRepository hubRepository;

    public MetaController(HubRepository hubRepository) {
        this.hubRepository = hubRepository;
    }

    @GetMapping("/options")
    public MetaOptionsResponse options() {
        List<Option> durations =
                Arrays.stream(Duration.values()).map(d -> new Option(d.name(), d.labelKo(), d.labelEn())).toList();
        List<Option> difficulties = Arrays.stream(Difficulty.values())
                .map(d -> new Option(d.name(), d.labelKo(), d.labelEn()))
                .toList();
        List<Option> interests =
                Arrays.stream(Interest.values()).map(i -> new Option(i.name(), i.labelKo(), i.labelEn())).toList();
        List<Option> hubs = hubRepository.findAll().stream()
                .map(h -> new Option(h.getId().toString(), h.getNameKo(), h.getNameEn()))
                .toList();

        return new MetaOptionsResponse(durations, difficulties, interests, hubs);
    }
}
