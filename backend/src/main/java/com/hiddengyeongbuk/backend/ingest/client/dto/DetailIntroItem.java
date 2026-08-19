package com.hiddengyeongbuk.backend.ingest.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * detailIntro2 응답 한 건(외부 API 연동 설계서 §2.7).
 *
 * <p><b>응답 필드가 contentTypeId마다 완전히 다르다.</b> 우리가 필요한 것은 축제(ko=15,
 * en=85)의 개최 기간뿐이라 그 두 필드만 받고 나머지는 무시한다 —
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}가 그 역할을 한다.
 *
 * <p>형식은 {@code yyyyMMdd}(예: {@code 20260925}). 파싱은 {@code StagingWriter}가 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailIntroItem(String contentid, String eventstartdate, String eventenddate) {}
