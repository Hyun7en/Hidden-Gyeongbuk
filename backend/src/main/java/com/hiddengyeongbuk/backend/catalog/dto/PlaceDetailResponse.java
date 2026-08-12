package com.hiddengyeongbuk.backend.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 장소 상세(API 명세서 §3.2). contentId는 응답에서 제거됐다 — place에 외부 식별자가
 * 더 이상 없고(ERD v0.7), 있더라도 클라이언트에 노출할 이유가 없다.
 */
public record PlaceDetailResponse(
        Long placeId,
        String placeType,
        Long regionId,
        String title,
        String addr,
        /**
         * 현지 표기 주소(한국어). 요청 언어와 무관하게 항상 내려간다 — 택시 기사·현지인에게
         * 보여주는 용도라, 번역된 주소만으로는 실제 이동이 불가능하다(User Persona §4-1).
         */
        String addrLocal,
        String tel,
        BigDecimal lat,
        BigDecimal lng,
        String overview,
        String firstImage,
        List<String> images,
        List<String> tags,
        /** 실제 표시에 사용된 언어. 요청 언어와 다르면 폴백이 일어난 것이다. */
        String resolvedLang,
        /** OFFICIAL(해당 언어 서비스 공식) / TRANSLATE(기계 번역) / null(해당 행 없음). */
        String translationSource,
        FestivalResponse festival) {

    public record FestivalResponse(LocalDate startDate, LocalDate endDate) {}
}
