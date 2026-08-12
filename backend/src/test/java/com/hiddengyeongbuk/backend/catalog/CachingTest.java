package com.hiddengyeongbuk.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import com.hiddengyeongbuk.backend.domain.hub.HubTranslation;
import com.hiddengyeongbuk.backend.domain.hub.HubTranslationRepository;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @Cacheable} + PlaceDataChangedEvent 무효화가 실제로 동작하는지 최소 구성의 Spring
 * 컨텍스트(DB 없이 CacheManager + mock repository)로 검증한다. 다른 {@code @Cacheable}
 * 메서드도 같은 AOP 메커니즘이라 대표로 하나만 본다.
 *
 * <p>대상이 컨트롤러에서 {@link HubQueryService}로 바뀌었다. {@code @Cacheable}은 프록시
 * 기반이라 같은 클래스 안에서 호출하면 캐시가 아예 안 걸리는데, 컨트롤러가 lang을 해석한 뒤
 * 자기 메서드를 부르는 구조로는 그 함정에 빠진다 — 그래서 조회를 별도 빈으로 분리했고
 * 캐시 검증도 그 빈을 대상으로 한다.
 */
class CachingTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private HubRepository stubbedRepository() {
        HubRepository hubRepository = context.getBean(HubRepository.class);
        given(hubRepository.findAll())
                .willReturn(List.of(Hub.builder()
                        .id(1L)
                        .code("ANDONG")
                        .lat(BigDecimal.valueOf(36.575))
                        .lng(BigDecimal.valueOf(128.6747))
                        .build()));
        HubTranslationRepository translations = context.getBean(HubTranslationRepository.class);
        given(translations.findByLang("ko"))
                .willReturn(List.of(HubTranslation.builder()
                        .id(1L)
                        .hubId(1L)
                        .lang("ko")
                        .name("안동")
                        .build()));
        return hubRepository;
    }

    @Test
    void 같은_요청은_캐시에서_처리돼_레포지토리를_한_번만_부른다() {
        context = new AnnotationConfigApplicationContext(Config.class);
        HubRepository hubRepository = stubbedRepository();
        HubQueryService service = context.getBean(HubQueryService.class);

        service.list("ko");
        service.list("ko");

        verify(hubRepository, times(1)).findAll();
    }

    @Test
    void 언어가_다르면_캐시_키가_달라_따로_조회한다() {
        // 캐시 키는 해석된 언어다 — 언어별로 응답이 다르므로 같은 엔트리를 쓰면 안 된다.
        context = new AnnotationConfigApplicationContext(Config.class);
        HubRepository hubRepository = stubbedRepository();
        HubQueryService service = context.getBean(HubQueryService.class);

        service.list("ko");
        service.list("en");

        verify(hubRepository, times(2)).findAll();
    }

    @Test
    void PlaceDataChangedEvent가_발행되면_캐시가_비워져_다시_조회한다() {
        context = new AnnotationConfigApplicationContext(Config.class);
        HubRepository hubRepository = stubbedRepository();
        HubQueryService service = context.getBean(HubQueryService.class);

        service.list("ko");
        context.publishEvent(new PlaceDataChangedEvent(this));
        service.list("ko");

        verify(hubRepository, times(2)).findAll();
    }

    @Test
    void 캐시_무효화_리스너는_컨텍스트의_모든_캐시를_비운다() {
        context = new AnnotationConfigApplicationContext(Config.class);
        stubbedRepository();
        CacheManager cacheManager = context.getBean(CacheManager.class);
        context.getBean(HubQueryService.class).list("ko");
        assertThat(cacheManager.getCache("hubs").get("ko")).isNotNull();

        context.publishEvent(new PlaceDataChangedEvent(this));

        assertThat(cacheManager.getCache("hubs").get("ko")).isNull();
    }

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        HubRepository hubRepository() {
            return mock(HubRepository.class);
        }

        @Bean
        HubTranslationRepository hubTranslationRepository() {
            return mock(HubTranslationRepository.class);
        }

        @Bean
        HubQueryService hubQueryService(HubRepository hubRepository, HubTranslationRepository translations) {
            return new HubQueryService(hubRepository, translations);
        }

        @Bean
        CacheEvictionListener cacheEvictionListener(CacheManager cacheManager) {
            return new CacheEvictionListener(cacheManager);
        }
    }
}
