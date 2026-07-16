package com.hiddengyeongbuk.backend.catalog;

public class PlaceNotFoundException extends RuntimeException {
    public PlaceNotFoundException(Long placeId) {
        super("장소를 찾을 수 없습니다: " + placeId);
    }
}
