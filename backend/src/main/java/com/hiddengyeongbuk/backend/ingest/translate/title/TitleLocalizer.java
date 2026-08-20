package com.hiddengyeongbuk.backend.ingest.translate.title;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import com.hiddengyeongbuk.backend.support.llm.GeminiClient;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 영문 제목을 국문 원문에서 다시 만든다(ADR-026 localize 단계).
 *
 * <p><b>실패는 언제나 현상 유지로 떨어진다.</b> 호출 실패·빈 응답·길이 초과·응답 누락은 모두
 * 기존 제목을 그대로 둔다. 이 작업으로 데이터가 나빠지는 경로가 없어야 되돌릴 걱정 없이 돌린다.
 *
 * <p><b>재실행 판정은 {@code title_localized_at}이다.</b> 성공한 행에만 시각을 찍으므로,
 * 실패한 건은 표시가 없어 다음 실행이 자동으로 다시 시도한다 — 수집 파이프라인이 워터마크를
 * 되돌려 재시도하는 것과 같은 방식이다.
 *
 * <p><b>{@code @Transactional}을 걸지 않는다.</b> 루프 전체를 트랜잭션 하나로 묶으면 외부 HTTP를
 * 기다리는 동안 커넥션을 쥐고 있고, 중간 오류 하나가 rollback-only로 전체를 날린다
 * (수집 파이프라인과 같은 이유). 저장은 항목 단위로 일어난다.
 */
@Component
public class TitleLocalizer {

    private static final Logger log = LoggerFactory.getLogger(TitleLocalizer.class);

    /** 연속으로 이만큼 실패하면 멈춘다. 대개 쿼터 소진이라 계속 두드려도 시간만 버린다. */
    private static final int MAX_CONSECUTIVE_BATCH_FAILURES = 3;

    private final TitleCandidateLoader candidateLoader;
    private final TitleProperties properties;
    private final GeminiClient geminiClient;
    private final TitleBatchParser batchParser;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TitleLocalizer(
            TitleCandidateLoader candidateLoader,
            TitleProperties properties,
            GeminiClient geminiClient,
            TitleBatchParser batchParser,
            PlaceTranslationRepository placeTranslationRepository,
            ApplicationEventPublisher eventPublisher) {
        this.candidateLoader = candidateLoader;
        this.properties = properties;
        this.geminiClient = geminiClient;
        this.batchParser = batchParser;
        this.placeTranslationRepository = placeTranslationRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 모델이 돌려주는 한 건. 필드가 더 와도 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchTitle(String id, String title) {}

    /**
     * @param skipped 이미 localization을 마쳐 호출하지 않은 건
     * @param unchanged 새로 만들었는데 기존과 같아 제목은 그대로 둔 건(표시만 찍는다)
     * @param keptExisting 실패·누락으로 기존 제목을 남긴 건
     */
    public record LocalizeResult(
            String lang,
            int candidates,
            int skipped,
            int localized,
            int unchanged,
            int keptExisting,
            String stoppedBy) {}

    public LocalizeResult localize(String lang, int limit, boolean force) {
        List<TitleCandidate> all = candidateLoader.load(lang);
        List<TitleCandidate> pending = all.stream()
                .filter(c -> force || c.target().getTitleLocalizedAt() == null)
                .toList();
        int skipped = all.size() - pending.size();
        List<TitleCandidate> todo = pending.stream().limit(Math.max(limit, 0)).toList();

        int localized = 0;
        int unchanged = 0;
        int keptExisting = 0;
        int consecutiveFailures = 0;
        String stoppedBy = null;

        for (int from = 0; from < todo.size(); from += properties.batchSize()) {
            List<TitleCandidate> batch = todo.subList(from, Math.min(from + properties.batchSize(), todo.size()));
            Map<String, String> answers;
            try {
                answers = ask(batch, lang);
                consecutiveFailures = 0;
            } catch (RuntimeException e) {
                // 배치 전체가 날아가도 표시를 안 찍으므로 다음 실행이 그대로 다시 시도한다.
                log.warn("배치 {}건 실패, 기존 제목 유지: {}", batch.size(), e.getMessage());
                keptExisting += batch.size();
                if (++consecutiveFailures >= MAX_CONSECUTIVE_BATCH_FAILURES) {
                    stoppedBy = "연속 %d배치 실패 — 쿼터 소진 추정".formatted(consecutiveFailures);
                    break;
                }
                pace();
                continue;
            }

            for (TitleCandidate candidate : batch) {
                String generated = sanitize(answers.get(candidate.contentId()));
                String rejection = rejectionReason(generated);
                if (rejection != null) {
                    log.warn("생성 결과를 버리고 기존 제목 유지 (place {} · {}): {} — 받은 값: {}",
                            candidate.placeId(), candidate.koTitle(), rejection, abbreviate(generated));
                    keptExisting++;
                    continue;
                }
                PlaceTranslation target = candidate.target();
                boolean changed = !generated.equals(target.getTitle());
                if (changed) {
                    target.setTitle(generated);
                    localized++;
                } else {
                    unchanged++;
                }
                // 바뀌지 않았어도 표시는 찍는다 — 안 찍으면 다음 실행이 같은 호출을 또 한다.
                target.setTitleLocalizedAt(LocalDateTime.now());
                placeTranslationRepository.save(target);
            }
            pace();
        }

        if (stoppedBy == null && todo.size() < pending.size()) {
            stoppedBy = "limit " + limit + " 도달";
        }
        if (localized > 0) {
            // 제목이 바뀌었으니 조회·추천 캐시를 비워야 한다 — 안 비우면 stale로 남는다.
            eventPublisher.publishEvent(new PlaceDataChangedEvent(this));
        }
        LocalizeResult result =
                new LocalizeResult(lang, all.size(), skipped, localized, unchanged, keptExisting, stoppedBy);
        log.info("제목 localize: {}", result);
        return result;
    }

    /** contentId → 생성된 제목. 모델이 순서를 바꾸거나 일부를 빠뜨려도 되짚을 수 있게 한다. */
    private Map<String, String> ask(List<TitleCandidate> batch, String lang) {
        return batchParser
                .parse(geminiClient.generate(TitlePrompts.localize(batch, lang)), BatchTitle.class)
                .stream()
                .filter(a -> a.id() != null && a.title() != null)
                .collect(Collectors.toMap(BatchTitle::id, BatchTitle::title, (a, b) -> a, LinkedHashMap::new));
    }

    /** 모델이 붙이기 쉬운 장식(따옴표·여러 줄)을 걷어낸다. */
    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        int newline = text.indexOf('\n');
        if (newline >= 0) {
            text = text.substring(0, newline).strip();
        }
        return text.replaceAll("^[\"'`]+", "").replaceAll("[\"'`]+$", "").strip();
    }

    /** 받아들일 수 없는 이유. {@code null}이면 통과다. */
    private String rejectionReason(String title) {
        if (title == null || title.isBlank()) {
            return "응답에 없거나 빈 값";
        }
        if (title.length() > properties.maxLength()) {
            // place_translation.title이 VARCHAR(200)이라 넘으면 적재 자체가 실패한다.
            return "길이 초과(" + title.length() + " > " + properties.maxLength() + ")";
        }
        return null;
    }

    /** 무료 티어 분당 5회에 맞춘 간격. 이게 없으면 8건째에 429가 난다(2026-08-20 실측). */
    private void pace() {
        long millis = properties.pacingMillis();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() > 120 ? value.substring(0, 120) + "…" : value;
    }
}
