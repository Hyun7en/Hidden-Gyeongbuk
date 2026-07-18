package com.hiddengyeongbuk.backend.catalog;

/** 존재하지 않는 hubId 조회. PlaceNotFoundException과 같은 404 계열 — "리소스 없음"의 상태코드를 통일한다. */
public class HubNotFoundException extends RuntimeException {
    public HubNotFoundException(Long hubId) {
        super("허브를 찾을 수 없습니다: " + hubId);
    }
}
