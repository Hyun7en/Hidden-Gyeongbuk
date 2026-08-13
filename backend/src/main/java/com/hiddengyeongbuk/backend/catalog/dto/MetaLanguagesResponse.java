package com.hiddengyeongbuk.backend.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 지원 언어 목록(API 명세서 §2.4). 프론트 언어 선택기가 이걸로 채워진다.
 *
 * <p>언어 레지스트리에서 {@code active=true}인 항목만 내려간다 — 언어를 추가하면 설정에 항목만
 * 늘고 이 응답이 자동으로 늘어난다(ADR-018). 프론트에 언어를 하드코딩하지 않는 것이 요점이다.
 */
public record MetaLanguagesResponse(
        List<Language> languages,
        // 명세서의 필드명은 `default`인데 자바 예약어라 컴포넌트 이름으로 쓸 수 없다.
        // 직렬화 이름만 맞춘다 — 프론트 타입(MetaLanguagesResponse.default)이 이 이름을 본다.
        @JsonProperty("default") String defaultLang) {

    /** {@code name}은 자기 언어로 쓴 이름이다(ko → 한국어) — 선택기에서 그래야 읽힌다. */
    public record Language(String code, String name) {}
}
