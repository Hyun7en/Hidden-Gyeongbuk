package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * place 데이터가 배치 수집·백필로 바뀌면(PlaceDataChangedEvent) 조회·추천 캐시를 전부 비운다.
 * 수집은 하루 1~2번 정도의 저빈도 이벤트라, 캐시별로 세밀하게 무효화하는 대신 전체 클리어로
 * 단순하게 정합성을 보장한다.
 */
@Component
class CacheEvictionListener {

    private final CacheManager cacheManager;

    CacheEvictionListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @EventListener
    void onPlaceDataChanged(PlaceDataChangedEvent event) {
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
