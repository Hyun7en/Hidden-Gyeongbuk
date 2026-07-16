package com.hiddengyeongbuk.backend.ingest.summary;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * has_en=false로 이미 저장된 장소의 영문 요약을 TourAPI 재수집 없이 백필하는 내부 전용 엔드포인트.
 * SyncAdminController와 마찬가지로 인증이 없다 — 배포 전 반드시 제거하거나 접근 제한 필요(P4).
 */
@RestController
@RequestMapping("/internal/summaries")
public class SummaryAdminController {

    private final EnglishSummaryService englishSummaryService;

    public SummaryAdminController(EnglishSummaryService englishSummaryService) {
        this.englishSummaryService = englishSummaryService;
    }

    @PostMapping
    public EnglishSummaryService.BackfillResult backfill(@RequestParam(defaultValue = "20") int limit) {
        return englishSummaryService.backfill(limit);
    }
}
