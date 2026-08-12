package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.HubResponse;
import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import com.hiddengyeongbuk.backend.domain.hub.HubTranslation;
import com.hiddengyeongbuk.backend.domain.hub.HubTranslationRepository;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거점 조회(API 명세서 §2.1). 컨트롤러가 아니라 별도 빈인 이유는 {@code @Cacheable}이
 * 프록시 기반이라 같은 클래스 안에서 호출하면(self-invocation) 캐시가 아예 동작하지 않기
 * 때문이다 — 컨트롤러가 lang을 해석한 뒤 이 빈을 호출해야 AOP가 걸린다.
 *
 * <p>들어오는 {@code lang}은 이미 해석된 값이라 캐시 키 공간이 활성 언어 수로 유계다.
 */
@Service
@Transactional(readOnly = true)
public class HubQueryService {

    private final HubRepository hubRepository;
    private final HubTranslationRepository hubTranslationRepository;

    public HubQueryService(HubRepository hubRepository, HubTranslationRepository hubTranslationRepository) {
        this.hubRepository = hubRepository;
        this.hubTranslationRepository = hubTranslationRepository;
    }

    @Cacheable("hubs")
    public List<HubResponse> list(String lang) {
        // 거점은 4개뿐이라 요청 언어·기준 언어 행을 통째로 읽어 폴백에 쓴다.
        Map<Long, HubTranslation> requested = byHubId(lang);
        Map<Long, HubTranslation> base =
                LanguageRegistry.BASE.equals(lang) ? requested : byHubId(LanguageRegistry.BASE);

        return hubRepository.findAll().stream()
                .map(hub -> toResponse(hub, requested.get(hub.getId()), base.get(hub.getId())))
                .toList();
    }

    private Map<Long, HubTranslation> byHubId(String lang) {
        return hubTranslationRepository.findByLang(lang).stream()
                .collect(Collectors.toMap(HubTranslation::getHubId, Function.identity(), (a, b) -> a));
    }

    private static HubResponse toResponse(Hub hub, HubTranslation requested, HubTranslation base) {
        HubTranslation chosen = requested != null ? requested : base;
        return new HubResponse(
                hub.getId(),
                hub.getCode(),
                // 어떤 언어 행도 없으면 코드라도 보여준다 — seed가 깨지지 않는 한 일어나지 않는다.
                chosen != null ? chosen.getName() : hub.getCode(),
                chosen != null ? chosen.getDescription() : null,
                hub.getLat(),
                hub.getLng(),
                chosen != null ? chosen.getLang() : null);
    }
}
