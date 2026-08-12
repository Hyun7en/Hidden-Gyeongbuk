package com.hiddengyeongbuk.backend.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집 파이프라인 일 1회 배치 — Collect → Resolve → Merge 순으로 돈다.
 *
 * <p>번역(4단계)은 여기서 돌리지 않는다. 무료 한도가 월 50만 자라 하루 페이스를 따로
 * 관리해야 하고, 공식 번역이 다음 주기에 올 수 있어 서둘러 기계 번역하면 낭비다 —
 * {@code POST /internal/translations}로 별도 진행한다(아키텍처 §5.1).
 *
 * <p>TourAPI 개발계정은 오퍼레이션별 일 1,000건 한도가 있어 초기 전체 적재가 끝나기 전까지는
 * {@code sync.enabled=false}(기본값)로 꺼둔다.
 */
@Component
@ConditionalOnProperty(prefix = "sync", name = "enabled", havingValue = "true")
public class IncrementalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncrementalSyncScheduler.class);

    private final Collector collector;
    private final Resolver resolver;
    private final Merger merger;

    public IncrementalSyncScheduler(Collector collector, Resolver resolver, Merger merger) {
        this.collector = collector;
        this.resolver = resolver;
        this.merger = merger;
    }

    @Scheduled(cron = "${sync.cron:0 0 3 * * *}")
    public void runDailySync() {
        log.info("수집 배치 시작");
        // 언어 하나가 실패해도 Collector 안에서 격리되고, 성공한 언어만으로 Resolve·Merge를
        // 진행한다 — 전체를 멈추면 정상 수집된 언어까지 방치된다(아키텍처 §5.1).
        collector.collectAll().forEach(result -> log.info("수집 완료: {}", result));
        log.info("판정 완료: {}", resolver.resolve());
        log.info("병합 완료: {}", merger.merge());
    }
}
