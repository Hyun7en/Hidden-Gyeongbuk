package com.hiddengyeongbuk.backend.ingest.translate.title;

import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 사람이 확정한 제목 교정을 적용한다(ADR-026 overrides 단계).
 *
 * <p><b>외부 호출이 없다.</b> 몇 번을 돌려도 공짜이고 결과가 같다 — 같은 값을 다시 넣는 것이라
 * 두 번째 실행은 바꿀 것이 없다고 보고한다.
 *
 * <p><b>키는 국문 {@code source_content_id}다.</b> 내부 {@code place_id}는 재판정 때 바뀌므로
 * (2026-08-20에 실제로 겪었다) 교정이 엉뚱한 장소에 붙을 수 있다. 소스 식별자는 바뀌지 않는다.
 *
 * <p>파일 형식:
 *
 * <pre>
 * titles:
 *   "126508":
 *     ko: "숫골"
 *     en: "Sutgol"
 *     reason: "proper_name"
 * </pre>
 *
 * {@code ko}는 사람이 검토하기 위한 것이고 대조에 쓰지 않는다 — 국문 제목이 바뀌어도 교정이
 * 조용히 풀리면 안 되기 때문이다.
 */
@Component
public class TitleOverrides {

    private static final Logger log = LoggerFactory.getLogger(TitleOverrides.class);

    private final TitleCandidateLoader candidateLoader;
    private final TitleProperties properties;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TitleOverrides(
            TitleCandidateLoader candidateLoader,
            TitleProperties properties,
            PlaceTranslationRepository placeTranslationRepository,
            ApplicationEventPublisher eventPublisher) {
        this.candidateLoader = candidateLoader;
        this.properties = properties;
        this.placeTranslationRepository = placeTranslationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param unmatched 교정 파일에는 있는데 대상 장소를 찾지 못한 키. 오타이거나 재수집으로
     *     사라진 장소다 — 조용히 넘기면 교정이 안 먹은 것을 모른다
     */
    public record OverrideResult(String lang, int defined, int applied, int alreadyCorrect, List<String> unmatched) {}

    public OverrideResult apply(String lang) {
        Map<String, String> overrides = load(properties.overridesFile(lang));
        Map<String, TitleCandidate> byContentId = new LinkedHashMap<>();
        candidateLoader.load(lang).forEach(c -> byContentId.put(c.contentId(), c));

        int applied = 0;
        int alreadyCorrect = 0;
        List<String> unmatched = new java.util.ArrayList<>();

        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            TitleCandidate candidate = byContentId.get(entry.getKey());
            if (candidate == null) {
                unmatched.add(entry.getKey());
                continue;
            }
            if (entry.getValue().equals(candidate.currentTitle())) {
                alreadyCorrect++;
                continue;
            }
            PlaceTranslation target = candidate.target();
            target.setTitle(entry.getValue());
            placeTranslationRepository.save(target);
            applied++;
        }

        if (applied > 0) {
            eventPublisher.publishEvent(new PlaceDataChangedEvent(this));
        }
        if (!unmatched.isEmpty()) {
            log.warn("대상을 찾지 못한 교정 키 {}건: {}", unmatched.size(), unmatched);
        }
        OverrideResult result = new OverrideResult(lang, overrides.size(), applied, alreadyCorrect, unmatched);
        log.info("제목 교정 적용: {}", result);
        return result;
    }

    /** contentId → 확정 제목. 파일이 없으면 빈 map이다 — 교정이 아직 없는 것이 정상 상태다. */
    Map<String, String> load(Path file) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) {
            root = new Yaml().load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("교정 파일을 읽지 못했습니다: " + file, e);
        }
        if (root == null || !(root.get("titles") instanceof Map<?, ?> titles)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        titles.forEach((key, value) -> {
            if (!(value instanceof Map<?, ?> fields)) {
                return;
            }
            Object en = fields.get("en");
            if (en != null && !en.toString().isBlank()) {
                // YAML은 따옴표 없는 숫자 키를 Integer로 읽는다 — 문자열로 맞춰야 대조가 된다.
                result.put(String.valueOf(key), en.toString().strip());
            }
        });
        return result;
    }
}
