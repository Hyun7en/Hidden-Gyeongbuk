package com.hiddengyeongbuk.backend.ingest.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * detailImage2 응답 한 건(외부 API 연동 설계서 §2.3·§2.7).
 *
 * <p>{@code originimgurl}이 원본 크기이고 {@code smallimageurl}이 썸네일이다. 상세 화면의
 * 갤러리에 쓰므로 원본을 받되, 원본이 비어 있을 때를 대비해 썸네일도 함께 받아 둔다.
 *
 * <p>{@code serialnum}은 소스가 매긴 일련번호다. 노출 순서를 우리가 정하지 않고 소스가 준
 * 순서를 그대로 쓰므로 정렬에는 쓰지 않지만, 응답을 눈으로 확인할 때 필요해 받아 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailImageItem(
        String contentid, String originimgurl, String smallimageurl, String serialnum, String imgname) {

    /** 화면에 걸 수 있는 URL. 원본이 비면 썸네일로 떨어진다. 둘 다 없으면 버릴 항목이다. */
    public String bestUrl() {
        if (originimgurl != null && !originimgurl.isBlank()) {
            return originimgurl.trim();
        }
        if (smallimageurl != null && !smallimageurl.isBlank()) {
            return smallimageurl.trim();
        }
        return null;
    }
}
