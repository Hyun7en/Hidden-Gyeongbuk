package com.hiddengyeongbuk.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

    @Test
    void 재시도_가능한_예외면_성공할_때까지_다시_시도한다() {
        AtomicInteger attempts = new AtomicInteger();
        String result = Retry.withBackoff(3, 1, e -> true, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("일시적 오류");
            }
            return "성공";
        });

        assertThat(result).isEqualTo("성공");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void 재시도_불가능한_예외는_즉시_던진다() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> Retry.withBackoff(3, 1, e -> false, () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("영구 오류");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("영구 오류");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void 최대_시도_횟수를_넘기면_마지막_예외를_던진다() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> Retry.withBackoff(3, 1, e -> true, () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("계속 실패");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(attempts.get()).isEqualTo(3);
    }
}
