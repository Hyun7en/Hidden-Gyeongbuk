package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 장소 하나의 언어별 텍스트를 폴백 체인에 따라 해석한다(API 명세서 §1.1, ERD §6.1).
 *
 * <p><b>요청 언어 행 → {@code ko} 행 → 존재하는 아무 행</b> 순으로 찾는다. 국문이라고 특별
 * 취급하지 않는다 — 국문도 {@code place_translation}의 한 행일 뿐이라, 요청이 ko든 en이든
 * 같은 코드 경로를 탄다. 구 구조는 "번역행 → place의 국문 컬럼" 2단계였고, 국문 컬럼이
 * 사라지면서 성립하지 않게 됐다.
 *
 * <p>3순위("아무 행")가 필요한 이유는 <b>어느 한 언어에만 존재하는 장소</b>가 있기 때문이다
 * (ADR-016). 영문 소스에만 있는 장소는 국문 행이 아직 없을 수 있는데, 그렇다고 제목을 비워
 * 내보낼 수는 없다.
 */
public record PlaceTexts(
        PlaceTranslation requested, PlaceTranslation base, PlaceTranslation any, String resolvedLang) {

    /**
     * 한 장소의 모든 번역 행에서 폴백 체인을 미리 확정한다.
     *
     * @param rows 그 장소의 place_translation 행 전부
     */
    static PlaceTexts of(List<PlaceTranslation> rows, String requestedLang) {
        PlaceTranslation requested = null;
        PlaceTranslation base = null;
        PlaceTranslation any = null;
        for (PlaceTranslation row : rows) {
            if (row.getLang().equalsIgnoreCase(requestedLang)) {
                requested = row;
            } else if (row.getLang().equalsIgnoreCase(LanguageRegistry.BASE)) {
                base = row;
            }
            if (any == null) {
                any = row;
            }
        }
        PlaceTranslation chosen = firstNonNull(requested, base, any);
        return new PlaceTexts(requested, base, any, chosen != null ? chosen.getLang() : null);
    }

    public static PlaceTexts empty() {
        return new PlaceTexts(null, null, null, null);
    }

    /** 폴백 체인에서 실제로 선택된 행. 없으면 null. */
    private PlaceTranslation chosen() {
        return firstNonNull(requested, base, any);
    }

    public String title() {
        PlaceTranslation row = chosen();
        return row != null ? row.getTitle() : null;
    }

    /**
     * 설명은 제목과 달리 행이 있어도 비어 있을 수 있다(상세를 아직 안 받은 장소). 그래서
     * 값 단위로 폴백한다 — 요청 언어 행이 있지만 overview가 비었으면 국문 설명이라도 보여준다.
     */
    public String overview() {
        return firstPresent(
                requested != null ? requested.getOverview() : null,
                base != null ? base.getOverview() : null,
                any != null ? any.getOverview() : null);
    }

    public String addr() {
        return firstPresent(
                requested != null ? requested.getAddr() : null,
                base != null ? base.getAddr() : null,
                any != null ? any.getAddr() : null);
    }

    /**
     * 현지 표기 주소(한국어). <b>요청 언어와 무관하게 항상 국문을 준다</b> — 택시 기사·현지인에게
     * 보여주는 용도라, 번역된 주소만으로는 실제 이동이 불가능하다(User Persona §4-1,
     * API 명세서 §3.2). 그래서 목록·상세 조회는 요청 언어 행만이 아니라 ko 행도 함께 읽는다.
     */
    public String addrLocal() {
        return base != null ? base.getAddr() : null;
    }

    /** 표시에 실제로 쓰인 텍스트의 출처. 폴백이 일어났으면 그 행 기준이다. */
    public String translationSource() {
        PlaceTranslation row = chosen();
        return row != null && row.getSource() != null ? row.getSource().name() : null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** 여러 장소의 번역 행을 placeId -> {@link PlaceTexts}로 묶는다. */
    static Map<Long, PlaceTexts> group(Collection<PlaceTranslation> rows, String requestedLang) {
        return rows.stream().collect(Collectors.groupingBy(PlaceTranslation::getPlaceId)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> of(e.getValue(), requestedLang), (a, b) -> a));
    }

    /** 단일 장소용. */
    static PlaceTexts single(List<PlaceTranslation> rows, String requestedLang) {
        return rows.isEmpty() ? empty() : of(rows, requestedLang);
    }
}
