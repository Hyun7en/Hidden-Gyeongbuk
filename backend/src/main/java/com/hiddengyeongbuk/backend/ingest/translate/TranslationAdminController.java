package com.hiddengyeongbuk.backend.ingest.translate;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 번역 백필(API 명세서 §5.1). place_translation 행이 없는 (장소, 언어) 조합을 채운다.
 * 구 {@code POST /internal/summaries}를 대체한다 — 대상 판정이 has_en에서 행 존재로 바뀌면서
 * 언어 파라미터가 필수가 됐다.
 *
 * <p>인증이 없어 local 프로파일에서만 빈이 뜬다 — 운영(prod)에서는 엔드포인트 자체가 없다.
 */
@RestController
@Profile("local")
@RequestMapping("/internal/translations")
public class TranslationAdminController {

    private final Translator translator;

    public TranslationAdminController(Translator translator) {
        this.translator = translator;
    }

    @PostMapping
    public Translator.BackfillResult backfill(
            @RequestParam String lang, @RequestParam(defaultValue = "20") int limit) {
        return translator.backfill(lang, limit);
    }
}
