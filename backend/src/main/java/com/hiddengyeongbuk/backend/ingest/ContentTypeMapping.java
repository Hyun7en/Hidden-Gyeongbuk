package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.PlaceType;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code (내부 타입, lang) → 소스 contentTypeId} 매핑(외부 API 연동 설계서 §2.5).
 *
 * <p>언어마다 contentTypeId 체계가 다르다(관광지: ko=12, en=76). 구 구현은 언어별로 Map을
 * 따로 두고 {@code categoryOfKor}/{@code categoryOfEng} 메서드를 나눠 가졌는데, 이 방식은
 * 언어를 늘릴 때마다 Map과 메서드가 하나씩 늘어난다 — ADR-018의 "언어 추가에 코드 수정 없음"이
 * 깨진다. 설정 데이터로 옮겨 언어 추가가 항목 추가로 끝나게 한다.
 *
 * <p>양방향으로 쓴다. 수집은 {@code sourceTypeId → PlaceType}(응답 해석), 향후 타입별
 * 오퍼레이션 호출은 {@code PlaceType → sourceTypeId}가 필요하다.
 *
 * <p>매핑에 없는 코드는 <b>수집 대상이 아니다</b>. 여행코스(ko=25)는 다중 스팟 루트라 place
 * 모델과 맞지 않고(ADR-003/009), 교통(en=77)은 대응 국문 타입이 없다.
 */
@Component
@ConfigurationProperties(prefix = "app.content-type")
public class ContentTypeMapping {

    /** 설정 바인딩용 — {@code {ATTRACTION: {ko: 12, en: 76}}}. */
    private Map<PlaceType, Map<String, Integer>> mappings = new EnumMap<>(PlaceType.class);

    /** lang -> (sourceTypeId -> PlaceType). 수집 응답 해석용 역인덱스. */
    private Map<String, Map<Integer, PlaceType>> reverse = Map.of();

    public void setMappings(Map<PlaceType, Map<String, Integer>> mappings) {
        this.mappings = mappings;
    }

    public Map<PlaceType, Map<String, Integer>> getMappings() {
        return mappings;
    }

    @PostConstruct
    void index() {
        Map<String, Map<Integer, PlaceType>> byLang = new HashMap<>();
        mappings.forEach((placeType, perLang) -> perLang.forEach((lang, sourceTypeId) -> {
            PlaceType previous = byLang.computeIfAbsent(key(lang), k -> new HashMap<>())
                    .put(sourceTypeId, placeType);
            if (previous != null) {
                throw new IllegalStateException(
                        "contentTypeId 매핑이 중복이다: lang=" + lang + ", sourceTypeId=" + sourceTypeId
                                + " → " + previous + " / " + placeType);
            }
        }));
        this.reverse = Map.copyOf(byLang);
    }

    /**
     * 소스 응답의 contenttypeid를 내부 타입으로 해석한다. 매핑에 없으면 빈 값 — 수집에서
     * 걸러내라는 뜻이다.
     */
    public Optional<PlaceType> placeTypeOf(String lang, Integer sourceTypeId) {
        if (lang == null || sourceTypeId == null) {
            return Optional.empty();
        }
        Map<Integer, PlaceType> perLang = reverse.get(key(lang));
        return perLang == null ? Optional.empty() : Optional.ofNullable(perLang.get(sourceTypeId));
    }

    /** 문자열 contenttypeid를 그대로 받는 편의 오버로드 — TourAPI 응답이 문자열이다. */
    public Optional<PlaceType> placeTypeOf(String lang, String sourceTypeId) {
        if (sourceTypeId == null || sourceTypeId.isBlank()) {
            return Optional.empty();
        }
        try {
            return placeTypeOf(lang, Integer.valueOf(sourceTypeId.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 활성 언어에 매핑이 빠지지 않았는지 기동 시 검증하기 위한 조회(아키텍처 §4.3). */
    public Optional<Integer> sourceTypeIdOf(PlaceType placeType, String lang) {
        Map<String, Integer> perLang = mappings.get(placeType);
        if (perLang == null) {
            return Optional.empty();
        }
        return perLang.entrySet().stream()
                .filter(e -> key(e.getKey()).equals(key(lang)))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static String key(String lang) {
        return lang.toLowerCase(Locale.ROOT);
    }
}
