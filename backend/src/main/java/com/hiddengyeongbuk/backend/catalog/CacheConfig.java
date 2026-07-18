package com.hiddengyeongbuk.backend.catalog;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ConcurrentMapCache(만료·크기 제한 없음) 대신 Caffeine을 쓴다. 캐시 키 공간을 유한하게
 * 설계했더라도(lang 정규화, keyword 제외, size 상한) page처럼 임의 정수가 키에 들어가는
 * 인자는 남는다 — 상한과 TTL을 캐시 자체에 걸어 어떤 키 조합이 와도 메모리가 유계이게 한다.
 * ADR-005("인메모리 → 필요 시 Redis")의 인메모리 단계 구현이며, Redis 전환 시에도
 * 이 빈만 교체하면 @Cacheable 코드는 그대로다.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofHours(12)));
        return manager;
    }
}
