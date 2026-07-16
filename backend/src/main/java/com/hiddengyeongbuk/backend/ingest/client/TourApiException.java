package com.hiddengyeongbuk.backend.ingest.client;

public class TourApiException extends RuntimeException {

    public TourApiException(String resultCode, String resultMsg, String rawBodySnippet) {
        super("TourAPI 오류 resultCode=%s, resultMsg=%s, 응답 원문: %s"
                .formatted(resultCode, resultMsg, rawBodySnippet));
    }
}
