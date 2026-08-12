package com.hiddengyeongbuk.backend.support;

/**
 * 두 좌표 간 거리 계산(Haversine). 추천 엔진의 접근성 적합도 계산(ADR-004)과
 * 장소 주변 조회(FR-S-04)에서 공용으로 쓴다.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {}

    /**
     * TourAPI 좌표 문자열(mapx/mapy)을 파싱한다. 좌표는 Identity Resolution의 1순위 키라
     * 수집·판정 양쪽에서 쓰여 support로 올렸다.
     *
     * <p>TourAPI는 좌표를 확보하지 못한 항목을 {@code "0"}으로 내려주기도 한다 — 실제 좌표가
     * 아니라 결측치이므로 null로 취급한다. 이걸 0.0으로 받으면 적도·본초자오선 근처에
     * 가짜 후보가 생겨 좌표 매칭이 오염된다.
     */
    public static java.math.BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            java.math.BigDecimal parsed = new java.math.BigDecimal(value.trim());
            return parsed.compareTo(java.math.BigDecimal.ZERO) == 0 ? null : parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2)
                        * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
