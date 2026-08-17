package com.hiddengyeongbuk.backend.ingest.translate;

/**
 * 재시도 정책을 <b>다 소진하고도</b> 같은 오류가 난 실패.
 *
 * <p>재시도 정책(3초→6초→12초)이 곧 "얼마나 일시적이라고 믿어줄지"의 정의다. 그걸 다 쓰고도
 * 같은 오류라면 우리 기준으로 일시적이 아니므로, 남은 후보를 더 두드릴 근거가 없다. 그래서
 * 이 예외는 <b>배치를 중단하라는 신호</b>다.
 *
 * <p>원인은 일일 문자 수 할당량 소진일 가능성이 높지만 <b>응답만으로 단정할 수 없다</b> —
 * 초당 요청 제한과 일일 할당량 소진이 같은 403 {@code userRateLimitExceeded}로 오기 때문이다
 * (2026-07-17 속도 제한 / 2026-08-17 일일 소진 양쪽 실측). 그래서 이름을 원인이 아니라
 * <b>대응</b>을 기준으로 붙였다.
 *
 * <p>중단이 이르게 걸려도 잃는 것이 없다. 백필은 이미 번역된 장소를 건너뛰므로 다시 돌리면
 * 그대로 이어진다 — 늦게 멈추면 시간을 버리는데 일찍 멈추면 재실행 한 번이라, 비대칭적으로
 * 일찍 멈추는 쪽이 싸다.
 */
public class TranslateExhaustedException extends TranslateException {
    public TranslateExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
