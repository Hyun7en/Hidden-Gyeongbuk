package com.hiddengyeongbuk.backend.ingest.translate;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.ingest.SyncLogger;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 수집 4단계 — {@code place_translation} 행이 <b>없는</b> (장소, 언어) 조합을 기계 번역으로
 * 채운다(ADR-013, 외부 API 연동 설계서 §4).
 *
 * <p>구 구현은 {@code has_en=false}인 장소를 대상으로 삼았는데, 그 플래그는 영어만 특별 취급해
 * 다국어와 맞지 않아 폐기됐다(ADR-017). 이제 대상 판정은 <b>행 존재 여부</b>이며 어느 언어든
 * 같은 코드가 처리한다.
 *
 * <p>번역 기준 언어는 {@code ko}이고, 국문이 없는 장소(다른 언어에만 존재)는 존재하는 아무
 * 언어를 기준으로 쓴다(아키텍처 §5.1). <b>중간 언어를 경유하지 않는다</b> — 국문 → 영어 →
 * 일본어처럼 이어 번역하면 오역이 누적된다.
 *
 * <p>공식 번역(OFFICIAL)이 있는 조합은 건드리지 않는다. 공식이 뒤늦게 확보되면 Merge가 같은
 * 행을 OFFICIAL로 덮어쓰므로, 여기서 만든 TRANSLATE 행은 자연히 대체된다.
 */
@Service
public class Translator {

    private static final Logger log = LoggerFactory.getLogger(Translator.class);
    private static final int MAX_OVERVIEW_CHARS = 3000;

    /**
     * 재시도를 다 소진했을 때 한 번 길게 쉬는 시간. 분당 창 기반 제한이면 이걸로 풀리고, 일일
     * 할당량 소진이면 안 풀린다 — 두 원인이 같은 403 {@code userRateLimitExceeded}로 와서
     * 응답만으로는 못 가리므로 <b>기다려 보고 판정한다</b>.
     *
     * <p>final이 아닌 이유는 테스트가 줄여 쓰기 때문이다 — 단위 테스트가 60초를 실제로 잘 수는 없다.
     */
    long quotaRecheckWaitMs = 60_000;

    private final TranslateClient translateClient;
    private final PlaceRepository placeRepository;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final LanguageRegistry languages;
    private final SyncLogger syncLogger;
    private final ApplicationEventPublisher eventPublisher;

    public Translator(
            TranslateClient translateClient,
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository,
            LanguageRegistry languages,
            SyncLogger syncLogger,
            ApplicationEventPublisher eventPublisher) {
        this.translateClient = translateClient;
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.languages = languages;
        this.syncLogger = syncLogger;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param remaining 이번 실행에서 손대지 않은 후보 수. 중단됐을 때 "얼마나 남았나"를 바로 보려고
     *     담는다 — 다음 실행이 여기서 이어진다
     * @param stoppedBy 중단 사유. 정상 완주면 null이다
     */
    public record BackfillResult(
            String lang,
            int requested,
            int translated,
            int skipped,
            int failed,
            int charactersUsed,
            int remaining,
            String stoppedBy) {

        /** 사용 글자 수까지 남긴다 — 무료 한도가 월 50만 자라 누적 소모를 사후에 봐야 한다. */
        String breakdown() {
            String base = "translated=%d, skipped=%d, failed=%d, characters=%d, remaining=%d"
                    .formatted(translated, skipped, failed, charactersUsed, remaining);
            return stoppedBy == null ? base : base + ", 중단=" + stoppedBy;
        }
    }

    /**
     * 대상 언어의 빠진 조합을 최대 {@code limit}건 채운다.
     *
     * <p>{@code limit}은 "성공 개수"가 아니라 <b>"시도 횟수"</b>에 건다 — 설정 오류로 전부
     * 실패하는 상황에서 limit이 안 걸려 후보 전체를 연달아 두드리는 걸 막기 위함이다.
     *
     * <p>{@code @Transactional}을 걸지 않는다. 걸면 루프 전체가 트랜잭션 하나가 되어 중간
     * 한 건의 실패가 그전 성공분까지 롤백시키고, 번역 HTTP 호출이 DB 커넥션을 붙든 채 돈다.
     */
    public BackfillResult backfill(String lang, int limit) {
        String targetLang = languages.resolve(lang);
        if (!languages.isSupported(lang)) {
            log.warn("레지스트리에 없는 언어 요청: {} → {}로 처리", lang, targetLang);
        }

        // 언어별 행 존재 여부를 한 번에 읽어 장소별 조회를 피한다.
        Set<Long> alreadyHave = placeTranslationRepository.findByLang(targetLang).stream()
                .map(PlaceTranslation::getPlaceId)
                .collect(Collectors.toSet());

        List<Place> candidates = placeRepository.findAll().stream()
                .filter(p -> !alreadyHave.contains(p.getId()))
                .toList();

        int translated = 0;
        int skipped = 0;
        int failed = 0;
        int characters = 0;
        int attempts = 0;

        String stoppedBy = null;
        int handled = 0;
        boolean quotaRechecked = false;

        for (Place place : candidates) {
            if (attempts >= limit) {
                stoppedBy = "limit " + limit + " 도달";
                break;
            }
            handled++;
            PlaceTranslation source = pickSource(place.getId(), targetLang);
            if (source == null) {
                // 번역할 원문 자체가 없다 — 아직 Merge되지 않았거나 제목이 없는 장소.
                skipped++;
                continue;
            }
            attempts++;
            try {
                characters += translateInto(place, source, targetLang);
                translated++;
                pace();
            } catch (TranslateExhaustedException e) {
                // 재시도(3·6·12초)를 다 쓰고도 실패했다. 원인이 둘인데 응답으로는 못 가린다 —
                // 분당 창 기반 제한이면 더 기다리면 풀리고, 일일 할당량 소진이면 안 풀린다.
                // 그래서 한 번 길게 쉬고 같은 장소를 재확인한다. 60초면 분당 창은 넘긴다.
                if (quotaRechecked) {
                    // 이미 한 번 확인했다. 반복하면 배치가 무한정 늘어난다.
                    failed++;
                    stoppedBy = "재시도 소진 반복 — 대기 후 재확인은 이미 사용함";
                    log.warn("장소 {} {} 번역 실패, 배치를 중단한다: {}", place.getId(), targetLang, e.getMessage());
                    break;
                }
                quotaRechecked = true;
                log.warn(
                        "재시도를 모두 소진했다. 분당 제한일 수 있어 {}초 쉬고 한 번 재확인한다: {}",
                        quotaRecheckWaitMs / 1000,
                        e.getMessage());
                if (!sleepQuietly(quotaRecheckWaitMs)) {
                    failed++;
                    stoppedBy = "대기 중 인터럽트";
                    break;
                }
                try {
                    characters += translateInto(place, source, targetLang);
                    translated++;
                    pace();
                    log.info("대기 후 성공했다 — 분당 제한이었던 것으로 보인다. 배치를 계속한다");
                } catch (RuntimeException stillFailing) {
                    // 60초를 쉬고도 안 풀렸다. 일일 할당량 소진으로 보고 남은 후보는 두드리지 않는다.
                    // 안 멈추면 후보마다 22초(재시도 21초 + 페이싱 1초)를 태우며 limit까지 간다 —
                    // 2026-08-17에 limit=300으로 1시간 45분이 걸릴 상황이 실제로 있었다.
                    failed++;
                    stoppedBy = "일일 할당량 소진 추정 — " + (quotaRecheckWaitMs / 1000) + "초 대기 후에도 실패";
                    log.warn("대기 후에도 실패했다. 배치를 중단한다: {}", stillFailing.getMessage());
                    break;
                }
            } catch (RuntimeException e) {
                // 그 장소만의 문제(원문 이상 등)로 본다. 다음 후보로 넘어간다.
                failed++;
                log.warn("장소 {} {} 번역 실패: {}", place.getId(), targetLang, e.getMessage());
                pace();
            }
        }
        int remaining = candidates.size() - handled;

        if (translated > 0) {
            eventPublisher.publishEvent(new PlaceDataChangedEvent(this));
        }
        BackfillResult result = new BackfillResult(
                targetLang, limit, translated, skipped, failed, characters, remaining, stoppedBy);
        syncLogger.record(
                SyncPhase.TRANSLATE,
                targetLang,
                "백필",
                translated,
                result.breakdown(),
                failed > 0 ? failed + "건 실패" : null);
        return result;
    }

    /**
     * 번역 원문을 고른다. 기준 언어(ko)를 우선하고, 없으면 존재하는 아무 행을 쓴다 —
     * 어느 한 언어에만 존재하는 장소도 나머지 언어를 채울 수 있어야 한다(ADR-016).
     */
    private PlaceTranslation pickSource(Long placeId, String targetLang) {
        Map<String, PlaceTranslation> byLang = placeTranslationRepository.findByPlaceId(placeId).stream()
                .collect(Collectors.toMap(PlaceTranslation::getLang, t -> t, (a, b) -> a));
        PlaceTranslation base = byLang.get(LanguageRegistry.BASE);
        if (base != null && !LanguageRegistry.BASE.equals(targetLang)) {
            return base;
        }
        return byLang.values().stream()
                .filter(t -> !t.getLang().equalsIgnoreCase(targetLang))
                .findFirst()
                .orElse(null);
    }

    /** 제목·설명·주소를 한 번의 호출로 함께 번역한다 — 호출 수를 줄이는 것이 무료 한도 관리의 핵심. */
    private int translateInto(Place place, PlaceTranslation source, String targetLang) {
        List<String> texts = new ArrayList<>();
        texts.add(source.getTitle());
        boolean hasOverview = isPresent(source.getOverview());
        boolean hasAddr = isPresent(source.getAddr());
        if (hasOverview) {
            texts.add(truncate(source.getOverview()));
        }
        if (hasAddr) {
            texts.add(source.getAddr());
        }

        // 원문 언어를 추측하지 않고 실제로 고른 행의 언어를 넘긴다. pickSource가 국문이 없으면
        // 다른 언어 행을 고르므로(ADR-016), "항상 국문"으로 단정하면 영어를 한국어라고 보내게 된다.
        List<String> out = translateClient.translate(texts, source.getLang(), targetLang);
        int cursor = 1;
        String overview = hasOverview ? out.get(cursor++) : null;
        String addr = hasAddr ? out.get(cursor) : null;

        placeTranslationRepository.save(PlaceTranslation.builder()
                .placeId(place.getId())
                .lang(targetLang)
                .title(out.get(0))
                .overview(overview)
                .addr(addr)
                .source(TranslationSource.TRANSLATE)
                .generatedAt(LocalDateTime.now())
                .build());

        return texts.stream().mapToInt(String::length).sum();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String truncate(String overview) {
        return overview.length() > MAX_OVERVIEW_CHARS ? overview.substring(0, MAX_OVERVIEW_CHARS) : overview;
    }

    // 연속 호출 시 403 userRateLimitExceeded가 난다(429가 아님 — 레거시 v2 특성). 하루 글자수
    // 한도와는 별개인 "초당 요청 수" 제한이라 호출 직후 무조건 쉰다(2026-07-17 실측, 0.3초로는 부족).
    private static void pace() {
        sleepQuietly(1_000);
    }

    /**
     * @return 끝까지 쉬었으면 true, 인터럽트되면 false — 호출부가 배치를 접을 수 있게 한다
     */
    private static boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
