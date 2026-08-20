package com.hiddengyeongbuk.backend.ingest.translate.title;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Gemini에 보내는 지시문(ADR-026).
 *
 * <p><b>제목만 던지지 않는 것이 이 작업의 전부다.</b> 국문 제목은 평균 7.8자라 홀로 보면
 * 고유명사인지 보통명사인지 알 수 없다 — {@code 아늑}을 던지면 번역기가 형용사로 읽어
 * {@code Cozy}를 내놓는 것이 정상 동작이다. 설명글·분류·주소를 같이 줘서 "이건 숙소 이름"임을
 * 알 수 있게 한다.
 *
 * <p><b>여러 건을 한 번에 묶어 보낸다.</b> 무료 티어가 분당 5회라 1건씩은 현실적이지 않다.
 * 응답을 {@code id}로 되짚을 수 있게 해서, 모델이 순서를 바꾸거나 일부를 빠뜨려도 나머지가
 * 제자리를 찾게 한다 — 순서에만 의존하면 한 건이 밀릴 때 전부 어긋난다.
 */
final class TitlePrompts {

    /**
     * 설명글은 문맥이면 충분하다 — 전문을 보낼 이유가 없고, 길면 지시문이 묻힌다.
     *
     * <p>하루 20회 한도라 한 번에 100건을 묶는데, 그러면 이 값이 프롬프트 크기를 지배한다.
     * 고유명사인지 판별하는 데는 첫 문장이면 대개 충분하다.
     */
    private static final int CONTEXT_LIMIT = 300;

    private TitlePrompts() {}

    static String localize(List<TitleCandidate> batch, String targetLang) {
        return """
        You are localizing Korean tourism place names into %s.

        Rules:
        1. Treat each Korean title as a tourism place/business/proper name, not as ordinary prose.
        2. Use the Korean overview as context to decide what is a proper name.
        3. Do not translate a proper name merely because it has a dictionary meaning.
        4. Preserve established romanization when appropriate.
        5. Translate generic facility/place-type terms when natural (마을 -> Village, 계곡 -> Valley).
        6. Prefer established official English names when they exist.
        7. Do not invent an English name.
        8. If uncertain whether a word is a proper name, prefer romanization over semantic translation.
        9. The result must stay natural for foreign tourists while preserving the identity
           of the original Korean name.

        Examples of the intended direction (not a lookup table):
        안동 하회마을 -> Andong Hahoe Village
        숫골 -> Sutgol
        아늑 -> Anuk
        별무리 -> Byeolmuri
        물길스테이 -> Mulgil Stay

        Return ONLY a JSON array with one object per item, using the given id:
        [{"id":"<id>","title":"<localized title>"}]
        No markdown fences, no commentary, no extra fields.

        Items:
        %s
        """
                .formatted(targetLang, items(batch, TitlePrompts::localizeItem));
    }

    /**
     * 전수 검수. <b>고치라고 시키지 않고 판정만 시킨다</b> — 수정은 사람이 확정한 교정으로만
     * 들어간다(ADR-026). LLM이 DB를 직접 바꾸는 경로를 만들지 않는 것이 요점이다.
     */
    static String review(List<TitleCandidate> batch, String targetLang) {
        return """
        You are reviewing %s titles generated for Korean tourism places.

        For each item decide whether the English title correctly localizes the Korean name.

        Mark SUSPECT when:
        - a Korean proper name was translated by its dictionary meaning
          (e.g. 숫골 -> "Male bone", 아늑 -> "Cozy")
        - the romanization is garbled or clearly wrong
        - the title lost the identity of the original place
        - the title contains explanation, alternatives, or leftover markup

        Mark OK when the title is a reasonable localization, including when a generic
        descriptive name is translated by meaning
        (e.g. 경상북도독립운동기념관 -> "Gyeongsangbuk-do Independence Movement Memorial Hall").

        Return ONLY a JSON array with one object per item, using the given id:
        [{"id":"<id>","decision":"OK","reason":"<one short sentence>","suggested_title":null}]
        Use "SUSPECT" with a suggested_title when the title should change.
        No markdown fences, no commentary.

        Items:
        %s
        """
                .formatted(targetLang, items(batch, TitlePrompts::reviewItem));
    }

    private static String items(
            List<TitleCandidate> batch, java.util.function.BiFunction<Integer, TitleCandidate, String> renderer) {
        return IntStream.range(0, batch.size())
                .mapToObj(i -> renderer.apply(i + 1, batch.get(i)))
                .collect(Collectors.joining("\n"));
    }

    private static String localizeItem(int index, TitleCandidate c) {
        return """
               [%d] id: %s
                   Korean title: %s
                   Category: %s
                   Address: %s
                   Korean overview: %s
               """
                .formatted(
                        index,
                        c.contentId(),
                        c.koTitle(),
                        blankToNone(c.placeType()),
                        blankToNone(c.koAddr()),
                        blankToNone(context(c.koOverview())));
    }

    private static String reviewItem(int index, TitleCandidate c) {
        return """
               [%d] id: %s
                   Korean title: %s
                   English title: %s
                   Category: %s
                   Korean overview: %s
               """
                .formatted(
                        index,
                        c.contentId(),
                        c.koTitle(),
                        c.currentTitle(),
                        blankToNone(c.placeType()),
                        blankToNone(context(c.koOverview())));
    }

    private static String context(String overview) {
        if (overview == null) {
            return null;
        }
        String flat = overview.replaceAll("\\s+", " ").trim();
        return flat.length() > CONTEXT_LIMIT ? flat.substring(0, CONTEXT_LIMIT) : flat;
    }

    private static String blankToNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}
