package com.hiddengyeongbuk.backend.ingest.admin;

import com.hiddengyeongbuk.backend.ingest.Collector;
import com.hiddengyeongbuk.backend.ingest.Merger;
import com.hiddengyeongbuk.backend.ingest.Resolver;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집 파이프라인 수동 트리거. 3단이 분리돼 있어 각각 따로 부를 수 있다 —
 * <b>Resolve·Merge는 외부 API를 쓰지 않으므로 마음껏 재실행해도 된다.</b> 매칭 로직을 고친 뒤
 * 재수집 없이 재판정만 하는 것이 staging 구조의 핵심 이점이다(오퍼레이션별 일 1,000건 한도).
 *
 * <p>인증이 없어 local 프로파일에서만 빈이 뜬다 — 운영(prod)에서는 엔드포인트 자체가 없다.
 */
@RestController
@Profile("local")
@RequestMapping("/internal/sync")
public class SyncAdminController {

    private final Collector collector;
    private final Resolver resolver;
    private final Merger merger;

    public SyncAdminController(Collector collector, Resolver resolver, Merger merger) {
        this.collector = collector;
        this.resolver = resolver;
        this.merger = merger;
    }

    /** 1단계. 언어 미지정 시 활성 언어 전체. TourAPI 호출량이 큰 경로다. */
    @PostMapping("/collect")
    public List<Collector.Result> collect(@RequestParam(required = false) String lang) {
        return lang == null ? collector.collectAll() : List.of(collector.collectLanguage(lang));
    }

    /** 1단계 — 지역 하나만. maxItems로 소규모 확인이 가능해 로직 검증에 쓴다. */
    @PostMapping("/collect/regions/{regionId}")
    public Collector.Result collectRegion(
            @org.springframework.web.bind.annotation.PathVariable Long regionId,
            @RequestParam String lang,
            @RequestParam(defaultValue = "100") int maxItems) {
        return collector.collectRegion(lang, regionId, maxItems);
    }

    /** 2단계. 외부 호출 없음 — 재실행 자유. */
    @PostMapping("/resolve")
    public Resolver.Result resolve(@RequestParam(defaultValue = "false") boolean resetAll) {
        if (resetAll) {
            resolver.resetAllToPending();
        }
        return resolver.resolve();
    }

    /** 3단계. 외부 호출 없음 — 재실행 자유. */
    @PostMapping("/merge")
    public Merger.Result merge() {
        return merger.merge();
    }
}
