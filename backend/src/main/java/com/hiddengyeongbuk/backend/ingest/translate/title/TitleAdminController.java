package com.hiddengyeongbuk.backend.ingest.translate.title;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제목 localization 일회성 백필(ADR-026).
 *
 * <p><b>상시 파이프라인이 아니다.</b> 스케줄러도 큐도 없고, 사용자 요청 경로에서 호출되지도
 * 않는다 — 출품 데이터를 한 번 다듬는 운영 도구다. 기존 번역 백필
 * ({@code POST /internal/translations})과 같은 패턴이라 인증이 없고, 그래서 local 프로파일에서만
 * 빈이 뜬다. 운영(prod)에서는 엔드포인트 자체가 존재하지 않는다.
 *
 * <p>세 단계를 따로 두는 이유는 <b>각각 재실행 가능하게 하기 위해서</b>다. localize는 대장을
 * 보고 건너뛰고, review는 제목이 그대로면 다시 묻지 않고, overrides는 외부 호출이 없어 언제든
 * 다시 돌릴 수 있다.
 */
@RestController
@Profile("local")
@RequestMapping("/internal/titles")
public class TitleAdminController {

    private final TitleLocalizer localizer;
    private final TitleReviewer reviewer;
    private final TitleOverrides overrides;

    public TitleAdminController(TitleLocalizer localizer, TitleReviewer reviewer, TitleOverrides overrides) {
        this.localizer = localizer;
        this.reviewer = reviewer;
        this.overrides = overrides;
    }

    /**
     * 국문 원문에서 영문 제목을 다시 만든다.
     *
     * @param force 대장을 무시하고 다시 만든다. 프롬프트를 고쳐 전량을 재생성할 때만 쓴다
     */
    @PostMapping("/localize")
    public TitleLocalizer.LocalizeResult localize(
            @RequestParam String lang,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean force) {
        return localizer.localize(lang, limit, force);
    }

    /** 만들어진 제목을 전수 검수해 OK/SUSPECT 판정을 파일에 남긴다. DB는 바꾸지 않는다. */
    @PostMapping("/review")
    public TitleReviewer.ReviewResult review(
            @RequestParam String lang, @RequestParam(defaultValue = "200") int limit) {
        return reviewer.review(lang, limit);
    }

    /** 사람이 확정한 교정을 적용한다. 외부 호출이 없다. */
    @PostMapping("/overrides")
    public TitleOverrides.OverrideResult overrides(@RequestParam String lang) {
        return overrides.apply(lang);
    }
}
