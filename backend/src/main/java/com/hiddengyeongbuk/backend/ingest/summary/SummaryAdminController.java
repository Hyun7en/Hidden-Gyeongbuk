package com.hiddengyeongbuk.backend.ingest.summary;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * has_en=false로 이미 저장된 장소의 영문 번역을 TourAPI 재수집 없이 백필하는 내부 전용 엔드포인트.
 * SyncAdminController와 마찬가지로 인증이 없어 local 프로파일에서만 빈이 뜬다 —
 * 운영 배포(prod)에서는 엔드포인트 자체가 존재하지 않는다.
 */
@RestController
@Profile("local")
@RequestMapping("/internal/summaries")
public class SummaryAdminController {

    private final PlaceTranslationService placeTranslationService;

    public SummaryAdminController(PlaceTranslationService placeTranslationService) {
        this.placeTranslationService = placeTranslationService;
    }

    @PostMapping
    public PlaceTranslationService.BackfillResult backfill(@RequestParam(defaultValue = "20") int limit) {
        return placeTranslationService.backfill(limit);
    }
}
