package com.hiddengyeongbuk.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hiddengyeongbuk.backend.domain.hub.Hub;
import com.hiddengyeongbuk.backend.domain.hub.HubRepository;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
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
 * @Cacheable(HubController) + PlaceDataChangedEvent 무효화가 실제로 동작하는지 최소 구성의
 * Spring 컨텍스트(DB 없이 CacheManager+mock repository만)로 검증한다. 다른 @Cacheable
 * 메서드(RegionController/MetaController/PlaceQueryService/CourseRecommendationService)도
 * 같은 AOP 메커니즘이라 대표로 하나만 검증한다.
 */
class CachingTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void 같은_요청은_캐시에서_처리돼_레포지토리를_한_번만_부른다() {
        context = new AnnotationConfigApplicationContext(Config.class);
        HubRepository hubRepository = context.getBean(HubRepository.class);
        given(hubRepository.findAll())
                .willReturn(List.of(Hub.builder().id(1L).nameKo("안동").build()));
        HubController controller = context.getBean(HubController.class);

        controller.list();
        controller.list();

        verify(hubRepository, times(1)).findAll();
    }

    @Test
    void PlaceDataChangedEvent가_발행되면_캐시가_비워져_다시_조회한다() {
        context = new AnnotationConfigApplicationContext(Config.class);
        HubRepository hubRepository = context.getBean(HubRepository.class);
        given(hubRepository.findAll())
                .willReturn(List.of(Hub.builder().id(1L).nameKo("안동").build()));
        HubController controller = context.getBean(HubController.class);

        controller.list();
        context.publishEvent(new PlaceDataChangedEvent(this));
        controller.list();

        verify(hubRepository, times(2)).findAll();
    }

    @Test
    void 캐시_무효화_리스너는_컨텍스트의_모든_캐시를_비운다() {
        context = new AnnotationConfigApplicationContext(Config.class);
        CacheManager cacheManager = context.getBean(CacheManager.class);
        HubRepository hubRepository = context.getBean(HubRepository.class);
        given(hubRepository.findAll())
                .willReturn(List.of(Hub.builder().id(1L).nameKo("안동").build()));
        context.getBean(HubController.class).list();
        assertThat(cacheManager.getCache("hubs").get(org.springframework.cache.interceptor.SimpleKey.EMPTY))
                .isNotNull();

        context.publishEvent(new PlaceDataChangedEvent(this));

        assertThat(cacheManager.getCache("hubs").get(org.springframework.cache.interceptor.SimpleKey.EMPTY))
                .isNull();
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
        HubController hubController(HubRepository hubRepository) {
            return new HubController(hubRepository);
        }

        @Bean
        CacheEvictionListener cacheEvictionListener(CacheManager cacheManager) {
            return new CacheEvictionListener(cacheManager);
        }
    }
}
