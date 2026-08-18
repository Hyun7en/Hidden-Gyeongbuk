package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 수집 타입 ↔ 소스 contentTypeId 매핑(외부 API 연동 설계서 §2.5).
 *
 * <p>언어마다 contentTypeId 체계가 다르다(관광지: ko=12, en=76). 언어별로 Map과 메서드를 나눠
 * 가지면 언어를 늘릴 때마다 코드가 늘어 ADR-018의 "언어 추가에 코드 수정 없음"이 깨지므로,
 * 설정 데이터로 두어 언어 추가가 항목 추가로 끝나게 한다.
 *
 * <p><b>ADR-024로 역할이 줄었다.</b> 예전에는 이 매핑이 {@code place_type}을 결정했지만, 이제
 * {@code place_type}은 {@code lclsSystm1}에서 직접 온다. 여기 남은 역할은 둘뿐이다.
 *
 * <ol>
 *   <li><b>수집 대상 선정</b> — 매핑에 없는 contentTypeId는 수집하지 않는다. 여행코스(ko=25)는
 *       다중 스팟 루트라 place 모델과 맞지 않고(ADR-003/009), 교통(en=77)은 대응 국문 타입이 없다.
 *   <li><b>{@code lclsSystm1} 결측 시 폴백</b> — 소스가 대분류를 비워 보내면 채울 값이 없는데
 *       {@code place_type}은 NOT NULL이다. 1:1 대응이 명확한 타입만 폴백을 갖는다.
 * </ol>
 *
 * <p><b>관광지(12/76)에는 폴백이 없다.</b> 대분류에서 NA·HS·VE·EX 넷으로 갈라져 단일 대응이
 * 없기 때문이다 — 임의로 하나를 고르는 것은 ADR-024가 없애려던 "우리가 분류를 판정하는" 행위
 * 그 자체다. 이런 건은 place를 만들지 않고 quarantine으로 보낸다(오매칭보다 미매칭, ADR-016).
 */
@Component
@ConfigurationProperties(prefix = "app.content-type")
public class ContentTypeMapping {

    /** 수집 타입 하나의 설정. {@code fallback}은 없을 수 있다(관광지). */
    public static class TypeMapping {
        private Map<String, Integer> sourceTypeIds = Map.of();
        private PlaceType fallback;

        public Map<String, Integer> getSourceTypeIds() {
            return sourceTypeIds;
        }

        public void setSourceTypeIds(Map<String, Integer> sourceTypeIds) {
            this.sourceTypeIds = sourceTypeIds;
        }

        public PlaceType getFallback() {
            return fallback;
        }

        public void setFallback(PlaceType fallback) {
            this.fallback = fallback;
        }
    }

    /** 수집 타입 이름(ATTRACTION 등) → 설정. 이 이름은 사람이 읽는 식별자일 뿐 저장되지 않는다. */
    private Map<String, TypeMapping> mappings = new LinkedHashMap<>();

    /** lang -> (sourceTypeId -> 폴백 대분류). 폴백이 없는 타입은 값이 비어 있다. */
    private Map<String, Map<Integer, Optional<PlaceType>>> reverse = Map.of();

    public void setMappings(Map<String, TypeMapping> mappings) {
        this.mappings = mappings;
    }

    public Map<String, TypeMapping> getMappings() {
        return mappings;
    }

    @PostConstruct
    void index() {
        Map<String, Map<Integer, Optional<PlaceType>>> byLang = new HashMap<>();
        mappings.forEach((name, mapping) -> mapping.getSourceTypeIds().forEach((lang, sourceTypeId) -> {
            Optional<PlaceType> previous = byLang.computeIfAbsent(key(lang), k -> new HashMap<>())
                    .put(sourceTypeId, Optional.ofNullable(mapping.getFallback()));
            if (previous != null) {
                throw new IllegalStateException(
                        "contentTypeId 매핑이 중복이다: lang=" + lang + ", sourceTypeId=" + sourceTypeId
                                + " (수집 타입 " + name + ")");
            }
        }));
        this.reverse = Map.copyOf(byLang);
    }

    /**
     * 이 contentTypeId를 수집하는가. 매핑에 없으면 수집 대상이 아니라는 뜻이다.
     *
     * <p>문자열을 그대로 받는다 — TourAPI 응답이 문자열이다.
     */
    public boolean isCollectable(String lang, String sourceTypeId) {
        return entry(lang, sourceTypeId).isPresent();
    }

    /** staging에 이미 적재된 행을 되짚을 때 쓴다 — 그쪽은 contentTypeId가 정수 컬럼이다. */
    public boolean isCollectable(String lang, Integer sourceTypeId) {
        return entry(lang, sourceTypeId).isPresent();
    }

    /**
     * {@code lclsSystm1}이 비어 있을 때 쓸 폴백 대분류. 매핑에 없거나(수집 대상 아님) 폴백이
     * 정의되지 않은 타입(관광지)이면 빈 값이며, 호출부는 place를 만들지 않아야 한다.
     */
    public Optional<PlaceType> fallbackPlaceType(String lang, Integer sourceTypeId) {
        return entry(lang, sourceTypeId).flatMap(o -> o);
    }

    private Optional<Optional<PlaceType>> entry(String lang, Integer sourceTypeId) {
        if (lang == null || sourceTypeId == null) {
            return Optional.empty();
        }
        Map<Integer, Optional<PlaceType>> perLang = reverse.get(key(lang));
        return perLang == null ? Optional.empty() : Optional.ofNullable(perLang.get(sourceTypeId));
    }

    private Optional<Optional<PlaceType>> entry(String lang, String sourceTypeId) {
        if (sourceTypeId == null || sourceTypeId.isBlank()) {
            return Optional.empty();
        }
        try {
            return entry(lang, Integer.valueOf(sourceTypeId.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String key(String lang) {
        return lang.toLowerCase(Locale.ROOT);
    }
}
