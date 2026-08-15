package com.hiddengyeongbuk.backend.catalog.course;

/**
 * 조건은 유효한데 코스를 만들 장소가 부족한 경우(API 명세서 §4.1, HTTP 422).
 *
 * <p>빈 목록이 아니라 에러인 이유는 <b>클라이언트가 할 일이 다르기 때문</b>이다. 빈 목록은
 * "결과 없음" 화면이지만 이 경우는 "조건을 넓혀 보세요"라는 안내가 필요하다 — 지역을 하나만
 * 골랐을 때 주로 발생한다(ADR-019 결과 ②).
 */
public class NoCourseFoundException extends RuntimeException {

    public NoCourseFoundException(String message) {
        super(message);
    }
}
