package com.hiddengyeongbuk.backend.domain.place;

import java.util.Arrays;
import java.util.List;

/**
 * 장소 분류 — 한국관광공사 신분류체계 <b>대분류</b>({@code lclsSystm1}) 코드다(ERD §4.1, ADR-024).
 *
 * <p><b>enum 상수 이름이 곧 소스 코드값이고, 그대로 DB에 저장된다.</b> 내부 이름으로 재매핑하지
 * 않으므로 매핑표가 존재하지 않는다 — 이게 이 결정의 요점이다. 편의상 {@code NATURE("NA")}
 * 같은 별칭을 다시 만들면 우리가 어휘를 소유하는 구조로 되돌아간다.
 *
 * <p>구 값(ATTRACTION/CULTURAL/FESTIVAL/LEISURE/STAY/SHOPPING/RESTAURANT)은 contentTypeId
 * 7종에서 파생한 자체 어휘였고, 별도로 자체 태그 6종({@code place_tag})까지 두어 <b>같은 질문에
 * 답하는 분류축이 둘</b>이었다. 태그 부여 규칙은 우리가 근거를 댈 수 없어 끝내 미정으로 남았고,
 * 그 위에 추천 가중치 35%가 얹혀 있었다. 관광공사가 이미 판정해 목록 응답에 실어 보내는 분류를
 * 채택해 축을 하나로 합쳤다.
 *
 * <p><b>관심사(interest)도 이 어휘를 쓴다.</b> 추천 입력의 {@code interests}가 받는 값이 곧
 * 이 enum이며, 별도 {@code Interest} enum은 폐기됐다(API 명세서 §1.3).
 *
 * <p>대분류는 전체 10종이나 {@code C01}(추천코스)은 제외한다 — 여행코스(ko=25)에 대응하는
 * 값인데 다중 스팟 루트라 place 모델과 맞지 않아 수집 대상이 아니다(ADR-003/009).
 *
 * <p>아래 라벨은 저장 값이 아니라 {@code GET /api/meta/options}가 요청 언어로 해석해 내려보내는
 * 선택지 문구다(API 명세서 §2.3). 프론트가 자체 라벨 사전을 갖지 않게 해서, 언어를 늘릴 때
 * 백엔드 한 곳만 고치면 되도록 한다.
 */
public enum PlaceType {
    AC("숙박", "Stays"),
    EV("축제·공연·행사", "Festivals & Events"),
    EX("체험관광", "Experiences"),
    FD("음식", "Food"),
    HS("역사관광", "History"),
    LS("레저스포츠", "Leisure & Sports"),
    NA("자연관광", "Nature"),
    SH("쇼핑", "Shopping"),
    VE("문화관광", "Culture");

    /**
     * 서비스가 실제로 다루는 분류. {@link #FD}는 빠진다 — 수집·노출 대상에서 제외했다(ADR-025).
     *
     * <p><b>enum에서 지우지 않는 이유</b>는 이 어휘가 우리 것이 아니기 때문이다. 관광공사
     * 대분류를 원본 그대로 쓰기로 했으므로(ADR-024) 우리가 안 쓴다고 어휘를 고치지 않는다.
     * 과거에 수집된 FD 행을 읽을 때도 값이 필요하다.
     */
    public static List<PlaceType> served() {
        return Arrays.stream(values()).filter(type -> type != FD).toList();
    }

    private final String labelKo;
    private final String labelEn;

    PlaceType(String labelKo, String labelEn) {
        this.labelKo = labelKo;
        this.labelEn = labelEn;
    }

    public String labelKo() {
        return labelKo;
    }

    public String labelEn() {
        return labelEn;
    }

    /**
     * 소스가 준 {@code lclsSystm1} 값을 분류로 해석한다. 어휘에 없는 값(공백, {@code C01},
     * 알 수 없는 코드)은 빈 값이며, 호출부가 폴백 또는 quarantine을 결정한다.
     */
    public static java.util.Optional<PlaceType> ofSourceCode(String lclsSystm1) {
        if (lclsSystm1 == null || lclsSystm1.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(valueOf(lclsSystm1.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            // C01(추천코스)이 여기로 온다 — 수집 대상이 아니므로 정상 경로다.
            return java.util.Optional.empty();
        }
    }
}
