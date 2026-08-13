package com.hiddengyeongbuk.backend.catalog;

public class RegionNotFoundException extends RuntimeException {
    public RegionNotFoundException(Long regionId) {
        super("지역을 찾을 수 없습니다: " + regionId);
    }
}
