package com.hiddengyeongbuk.backend.ingest.translate.title;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiddengyeongbuk.backend.support.llm.GeminiClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 만들어진 제목을 전수 검수한다(ADR-026 review 단계).
 *
 * <p><b>왜 휴리스틱으로 끝내지 않는가.</b> 2026-08-20에 "드문 영어 일반명사" 규칙으로 1,105건
 * 중 92건을 걸러 16건이 확실한 오역이었는데, <b>놓친 건수(false negative)는 측정하지 못했다.</b>
 * 규칙이 못 보는 오역이 얼마인지 모르는 채로 품질을 주장할 수 없어 전수로 본다. 제목 전체가
 * 8,594자라 전수가 비용 문제가 되지 않는다.
 *
 * <p><b>LLM은 판정만 한다.</b> 제안({@code suggested_title})은 파일에 남을 뿐 DB로 가지 않는다 —
 * 실제 수정은 사람이 확정한 교정 파일을 통해서만 들어간다.
 *
 * <p>재실행하면 <b>제목이 그대로인 건은 다시 묻지 않는다.</b> localize가 바꾼 것만 다시 본다.
 */
@Component
public class TitleReviewer {

    private static final Logger log = LoggerFactory.getLogger(TitleReviewer.class);

    private static final int MAX_CONSECUTIVE_BATCH_FAILURES = 3;

    private final TitleCandidateLoader candidateLoader;
    private final TitleProperties properties;
    private final GeminiClient geminiClient;
    private final TitleBatchParser batchParser;
    private final ObjectMapper objectMapper;

    public TitleReviewer(
            TitleCandidateLoader candidateLoader,
            TitleProperties properties,
            GeminiClient geminiClient,
            TitleBatchParser batchParser,
            ObjectMapper objectMapper) {
        this.candidateLoader = candidateLoader;
        this.properties = properties;
        this.geminiClient = geminiClient;
        this.batchParser = batchParser;
        this.objectMapper = objectMapper;
    }

    public record ReviewEntry(
            String contentId,
            long placeId,
            String koTitle,
            String enTitle,
            String decision,
            String reason,
            String suggestedTitle,
            String at) {}

    public record ReviewResult(
            String lang, int candidates, int reviewed, int skipped, int ok, int suspect, int failed, String stoppedBy) {}

    /** 모델이 돌려주는 판정 한 건. 필드가 더 와도 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verdict(
            String id, String decision, String reason, @JsonProperty("suggested_title") String suggestedTitle) {}

    public ReviewResult review(String lang, int limit) {
        Path reviewFile = properties.reviewFile(lang);
        Map<String, ReviewEntry> existing = readExisting(reviewFile);
        List<TitleCandidate> all = candidateLoader.load(lang);

        // 제목이 그대로면 판정도 그대로다 — localize가 바꾼 것만 다시 묻는다.
        List<TitleCandidate> pending = all.stream()
                .filter(c -> {
                    ReviewEntry previous = existing.get(c.contentId());
                    return previous == null || !previous.enTitle().equals(c.currentTitle());
                })
                .toList();
        int skipped = all.size() - pending.size();
        List<TitleCandidate> todo = pending.stream().limit(Math.max(limit, 0)).toList();

        int reviewed = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        String stoppedBy = null;

        for (int from = 0; from < todo.size(); from += properties.batchSize()) {
            List<TitleCandidate> batch = todo.subList(from, Math.min(from + properties.batchSize(), todo.size()));
            Map<String, Verdict> verdicts;
            try {
                verdicts = ask(batch, lang);
                consecutiveFailures = 0;
            } catch (RuntimeException e) {
                // 기록하지 않으므로 다음 실행에서 다시 시도된다.
                log.warn("검수 배치 {}건 실패: {}", batch.size(), e.getMessage());
                failed += batch.size();
                if (++consecutiveFailures >= MAX_CONSECUTIVE_BATCH_FAILURES) {
                    stoppedBy = "연속 %d배치 실패 — 쿼터 소진 추정".formatted(consecutiveFailures);
                    break;
                }
                pace();
                continue;
            }

            for (TitleCandidate candidate : batch) {
                Verdict verdict = verdicts.get(candidate.contentId());
                if (verdict == null) {
                    failed++;
                    continue;
                }
                existing.put(
                        candidate.contentId(),
                        new ReviewEntry(
                                candidate.contentId(),
                                candidate.placeId(),
                                candidate.koTitle(),
                                candidate.currentTitle(),
                                normalizeDecision(verdict.decision()),
                                verdict.reason(),
                                verdict.suggestedTitle(),
                                LocalDateTime.now().toString()));
                reviewed++;
            }
            // 배치마다 저장한다 — 중간에 멈춰도 여기까지가 남는다.
            write(reviewFile, existing.values());
            pace();
        }

        write(reviewFile, existing.values());
        if (stoppedBy == null && todo.size() < pending.size()) {
            stoppedBy = "limit " + limit + " 도달";
        }
        int ok = (int) existing.values().stream().filter(e -> "OK".equals(e.decision())).count();
        int suspect = (int) existing.values().stream().filter(e -> "SUSPECT".equals(e.decision())).count();
        ReviewResult result =
                new ReviewResult(lang, all.size(), reviewed, skipped, ok, suspect, failed, stoppedBy);
        log.info("제목 검수: {}", result);
        return result;
    }

    /** contentId → 판정. 순서가 바뀌거나 일부가 빠져도 되짚을 수 있게 id로 맞춘다. */
    private Map<String, Verdict> ask(List<TitleCandidate> batch, String lang) {
        return batchParser.parse(geminiClient.generate(TitlePrompts.review(batch, lang)), Verdict.class).stream()
                .filter(v -> v.id() != null)
                .collect(java.util.stream.Collectors.toMap(
                        Verdict::id, v -> v, (a, b) -> a, LinkedHashMap::new));
    }

    /** {@code OK}/{@code SUSPECT} 둘만 허용한다 — 모르는 값이 오면 사람이 보도록 SUSPECT로 떨어뜨린다. */
    private static String normalizeDecision(String raw) {
        if (raw == null) {
            return "SUSPECT";
        }
        String upper = raw.strip().toUpperCase(java.util.Locale.ROOT);
        return "OK".equals(upper) ? "OK" : "SUSPECT";
    }

    private Map<String, ReviewEntry> readExisting(Path file) {
        Map<String, ReviewEntry> byContentId = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return byContentId;
        }
        try {
            List<ReviewEntry> entries = objectMapper.readValue(
                    Files.readString(file, StandardCharsets.UTF_8),
                    objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, ReviewEntry.class));
            entries.forEach(e -> byContentId.put(e.contentId(), e));
        } catch (IOException e) {
            throw new UncheckedIOException("검수 결과를 읽지 못했습니다: " + file, e);
        }
        return byContentId;
    }

    private void write(Path file, java.util.Collection<ReviewEntry> entries) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(
                    file,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("검수 결과를 쓰지 못했습니다: " + file, e);
        }
    }

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
}
