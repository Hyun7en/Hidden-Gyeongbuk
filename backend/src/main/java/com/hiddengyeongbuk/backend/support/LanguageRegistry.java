package com.hiddengyeongbuk.backend.support;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 서비스가 다루는 언어 목록. <b>언어는 데이터이지 코드가 아니다</b>(ADR-018) — 언어별 분기를
 * 코드에 두지 않고 설정으로 관리하며, 언어 추가가 스키마 변경이나 코드 수정을 유발하지 않는 것을
 * 설계 목표로 한다(아키텍처 §4.3).
 *
 * <p>두 곳이 공유한다.
 *
 * <ul>
 *   <li><b>조회</b> — 요청 {@code lang}을 여기 있는 코드로만 해석한다. 임의 문자열을 그대로
 *       흘리면 {@code @Cacheable} 키 공간이 무한해지므로(CLAUDE.md 캐시 규율), 레지스트리에
 *       없는 언어는 폴백으로 해석한 뒤 <b>해석된 언어</b>를 캐시 키로 쓴다. 그래서 키 공간은
 *       활성 언어 수만큼만 늘어난다.
 *   <li><b>수집</b> — Collector가 활성 언어를 순회하며 각 TourAPI 서비스를 독립 호출한다.
 * </ul>
 *
 * <p>DB가 아니라 설정 파일에 두는 이유: 다른 데이터가 FK로 참조하지 않고, 값을 넣는 주체가
 * 배치 하나뿐이라 오타가 유입될 경로가 없다. 런타임 변경도 필요 없다 — 언어 추가는 이름 추출
 * 로직 같은 코드 변경을 동반하므로 어차피 배포가 따른다.
 */
@Component
@ConfigurationProperties(prefix = "app.language")
public class LanguageRegistry {

    /** 폴백 체인의 2순위이자 번역 기준 언어. 어떤 장소든 최소한 이 언어는 있다고 기대한다. */
    public static final String BASE = "ko";

    private List<Entry> entries = new ArrayList<>();

    /** 정규화된 코드 -> 엔트리. BCP 47은 대소문자 규칙이 있어(zh-CN) 원본 표기를 보존한다. */
    private Map<String, Entry> byCode = Map.of();

    private List<String> activeCodes = List.of();

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    @PostConstruct
    void index() {
        Map<String, Entry> map = new LinkedHashMap<>();
        List<String> active = new ArrayList<>();
        for (Entry entry : entries) {
            String code = canonical(entry.getCode());
            entry.setCode(code);
            map.put(lookupKey(code), entry);
            if (entry.isActive()) {
                active.add(code);
            }
        }
        this.byCode = Map.copyOf(map);
        this.activeCodes = List.copyOf(active);

        if (!map.containsKey(lookupKey(BASE))) {
            throw new IllegalStateException(
                    "언어 레지스트리에 기준 언어 '" + BASE + "'가 없다 — 폴백 체인이 성립하지 않는다");
        }
    }

    /** 수집·번역 대상 언어. 설정에서 active=false로 내리면 순회에서 빠진다. */
    public List<String> activeCodes() {
        return activeCodes;
    }

    public boolean isSupported(String lang) {
        return lang != null && byCode.containsKey(lookupKey(lang));
    }

    /**
     * 요청 언어를 레지스트리의 코드로 해석한다. 지원하지 않는 언어는 <b>에러가 아니라</b>
     * 기준 언어로 떨어진다(API 명세서 §1.1) — 이 반환값이 응답의 {@code resolvedLang}이자
     * 캐시 키가 되므로, 여기서 유한한 집합으로 좁히는 것이 캐시 안전성의 핵심이다.
     */
    public String resolve(String requested) {
        if (requested == null || requested.isBlank()) {
            return BASE;
        }
        Entry entry = byCode.get(lookupKey(requested));
        return entry != null && entry.isActive() ? entry.getCode() : BASE;
    }

    /** TourAPI 서비스명 조회(예: ko -> KorService2). Collector가 쓴다. */
    public String serviceNameOf(String lang) {
        Entry entry = byCode.get(lookupKey(lang));
        return entry != null ? entry.getServiceName() : null;
    }

    /** 언어 선택기 표시용 자기 언어 이름(ko -> 한국어). */
    public String displayNameOf(String lang) {
        Entry entry = byCode.get(lookupKey(lang));
        return entry != null ? entry.getDisplayName() : null;
    }

    /** 비교용 키. BCP 47은 대소문자를 구분하지 않으므로 소문자로 눕혀 찾는다. */
    private static String lookupKey(String lang) {
        return lang.toLowerCase(Locale.ROOT);
    }

    /** {@code zh-cn} 같은 입력을 표준 표기({@code zh-CN})로 교정한다. */
    private static String canonical(String raw) {
        String trimmed = raw.trim();
        int dash = trimmed.indexOf('-');
        if (dash < 0) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed.substring(0, dash).toLowerCase(Locale.ROOT)
                + "-"
                + trimmed.substring(dash + 1).toUpperCase(Locale.ROOT);
    }

    /** 언어 하나의 등록 정보. 언어를 늘리는 것은 여기 항목을 추가하는 일이어야 한다. */
    public static class Entry {
        /** BCP 47 (ko, en, ja, zh-CN…). */
        private String code;

        /** 사용자에게 보이는 자기 언어 이름 (한국어, English…). */
        private String displayName;

        /** 대응 TourAPI 서비스 (KorService2, EngService2…). */
        private String serviceName;

        /** false면 수집·번역 대상에서 빠지고 조회에서도 폴백된다. */
        private boolean active = true;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
