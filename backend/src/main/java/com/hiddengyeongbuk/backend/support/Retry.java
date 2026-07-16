package com.hiddengyeongbuk.backend.support;

import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 외부 API 호출의 일시적 오류(네트워크 타임아웃·5xx)를 지수 백오프로 재시도한다(NFR-03).
 * 인증 실패·잘못된 요청 파라미터처럼 다시 불러도 안 풀리는 오류까지 재시도하면 오히려
 * 외부 API를 불필요하게 두드리게 되므로, 어떤 예외가 재시도 대상인지는 호출부가 판단해서
 * isRetryable로 넘긴다.
 */
public final class Retry {

    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    private Retry() {}

    public static <T> T withBackoff(
            int maxAttempts, long initialDelayMs, Predicate<RuntimeException> isRetryable, Supplier<T> action) {
        long delay = initialDelayMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt == maxAttempts || !isRetryable.test(e)) {
                    throw e;
                }
                log.warn("일시적 오류로 재시도합니다 ({}/{}회, {}ms 후): {}", attempt, maxAttempts, delay, e.getMessage());
                sleep(delay);
                delay *= 2;
            }
        }
        throw new IllegalStateException("도달할 수 없음");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트됨", e);
        }
    }
}
