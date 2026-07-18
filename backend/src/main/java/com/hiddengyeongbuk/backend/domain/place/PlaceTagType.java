package com.hiddengyeongbuk.backend.domain.place;

/**
 * place_tag.tag에 저장되는 태그 어휘의 단일 출처. 이 한글 라벨 문자열이 곧 DB 값이며,
 * 수집(PlaceTagAssigner의 부여 규칙)과 조회·추천(Interest의 관심사 매칭)이 모두 이걸 참조한다.
 * 예전엔 같은 문자열이 ingest와 catalog에 각각 상수로 중복 정의돼 있어서, 한쪽만 바꾸면
 * 컴파일 에러 없이 관심사 매칭 점수만 조용히 0이 되는 구조였다.
 */
public enum PlaceTagType {
    NATURE("자연"),
    FOOD("음식"),
    TRADITION("전통"),
    TEMPLE("사찰"),
    FESTIVAL("축제"),
    REST("휴식");

    private final String labelKo;

    PlaceTagType(String labelKo) {
        this.labelKo = labelKo;
    }

    public String labelKo() {
        return labelKo;
    }
}
