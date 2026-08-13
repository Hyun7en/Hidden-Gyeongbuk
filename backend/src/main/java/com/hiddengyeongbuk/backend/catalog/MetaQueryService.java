package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse;
import com.hiddengyeongbuk.backend.catalog.dto.MetaOptionsResponse.Option;
import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import java.util.Arrays;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프론트 셀렉터 옵션(API 명세서 §2.3). {@code @Cacheable}이 프록시 기반이라 컨트롤러와 분리했다.
 *
 * <p>{@code durations}는 없다 — 기간 입력이 enum에서 날짜 범위로 바뀌었다(ADR-020).
 *
 * <p>{@code interests}는 {@code placeTypes}와 같은 목록이다 — 분류축이 하나로 합쳐졌다(ADR-024).
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
        // 장소 타입은 필터 칩이자 카드 배지 라벨이다 — 프론트가 코드만 받고 자체 사전으로
        // 번역하면 백엔드와 어긋날 수 있어 여기서 해석해 내려준다.
        List<Option> placeTypes = Arrays.stream(PlaceType.values())
                .map(p -> new Option(p.name(), OptionLabel.of(lang, p.labelKo(), p.labelEn())))
                .toList();
        List<Option> difficulties = Arrays.stream(Difficulty.values())
                .map(d -> new Option(d.name(), d.label(lang)))
                .toList();
        // 관심사는 별도 어휘가 아니라 장소 타입과 같은 목록이다(ADR-024). 두 키를 모두 남긴
        // 것은 프론트가 쓰는 자리(필터 칩 vs 관심사 칩)가 다르고, 나중에 관심사만 부분집합으로
        // 줄이고 싶어질 수 있어서다 — 그때 이 줄만 바뀐다.
        List<Option> interests = placeTypes;
        // 거점 옵션의 code는 PK가 아니라 hub.code다 — 공유 URL에 담기는 값이라 안정적이어야 하고,
        // PK는 재적재 시 바뀔 수 있다.
        List<Option> hubs = hubQueryService.list(lang).stream()
                .map(h -> new Option(h.code(), h.name()))
                .toList();

        return new MetaOptionsResponse(placeTypes, difficulties, interests, hubs, lang);
    }
}
