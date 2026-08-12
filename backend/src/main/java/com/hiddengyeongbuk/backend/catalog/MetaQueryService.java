package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse;
import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse.Option;
import java.util.Arrays;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프론트 셀렉터 옵션(API 명세서 §2.3). {@code @Cacheable}이 프록시 기반이라 컨트롤러와 분리했다.
 *
 * <p>{@code durations}는 없다 — 기간 입력이 enum에서 날짜 범위로 바뀌었다(ADR-020).
 */
@Service
@Transactional(readOnly = true)
public class MetaQueryService {

    private final HubQueryService hubQueryService;

    public MetaQueryService(HubQueryService hubQueryService) {
        this.hubQueryService = hubQueryService;
    }

    @Cacheable("metaOptions")
    public MetaOptionsResponse options(String lang) {
        List<Option> difficulties = Arrays.stream(Difficulty.values())
                .map(d -> new Option(d.name(), d.label(lang)))
                .toList();
        List<Option> interests = Arrays.stream(Interest.values())
                .map(i -> new Option(i.name(), i.label(lang)))
                .toList();
        // 거점 옵션의 code는 PK가 아니라 hub.code다 — 공유 URL에 담기는 값이라 안정적이어야 하고,
        // PK는 재적재 시 바뀔 수 있다.
        List<Option> hubs = hubQueryService.list(lang).stream()
                .map(h -> new Option(h.code(), h.name()))
                .toList();

        return new MetaOptionsResponse(difficulties, interests, hubs, lang);
    }
}
