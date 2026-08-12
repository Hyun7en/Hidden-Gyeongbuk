package com.hiddengyeongbuk.backend.domain.place;

/**
 * 장소의 언어 중립 내부 타입(ERD §4.1). {@code place.place_type}에 이름 그대로 저장된다.
 *
 * <p>구 {@code PlaceCategory}(한글 ENUM: 관광지/쇼핑/숙박/음식점/축제)를 대체한다. 한글 값을
 * 저장하면 다국어 응답에 특정 언어 문자열이 섞이고, 카테고리와 contentTypeId 두 컬럼을
 * 동기화할 이유도 없었다.
 *
 * <p><b>여기에 표시 라벨을 두지 않는다.</b> UI 그룹핑(관광지 = ATTRACTION+CULTURAL+LEISURE 등)은
 * 컬럼도 enum 필드도 아니라 설정의 그룹핑 정의로 처리하고, 사용자에게 보이는 이름은 프론트
 * i18n이 책임진다 — place_tag가 한글 라벨을 DB에 저장했다가 겪은 문제를 반복하지 않기 위함.
 *
 * <p>소스 contentTypeId와의 매핑((placeType, lang) → sourceTypeId)은 설정 파일에 있다(아키텍처 §4.3).
 */
public enum PlaceType {
    ATTRACTION,
    CULTURAL,
    FESTIVAL,
    LEISURE,
    STAY,
    SHOPPING,
    RESTAURANT
}
