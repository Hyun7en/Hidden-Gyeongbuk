package com.hiddengyeongbuk.backend.catalog.course;

import com.hiddengyeongbuk.backend.catalog.Difficulty;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 추천 점수 가중치와 코스 제약(아키텍처 §4.3). 코드 상수가 아니라 설정에 두는 이유는 항목 증감·수치
 * 조정이 잦기 때문이다 — ADR-021에서 점수 항목이 5개에서 4개로 바뀐 것이 그 사례다.
 *
 * <p><b>합계 100 검증은 기동 시에 한다.</b> 합이 어긋나도 코스 <i>간</i> 상대 순위는 그대로라
 * 응답이 멀쩡해 보이고, 어긋나는 것은 점수의 절대 스케일뿐이다 — 요청 시점에는 아무도 눈치채지
 * 못한다. 그래서 실패를 기동 시점으로 당긴다.
 */
@ConfigurationProperties(prefix = "course")
public record CourseProperties(Weights weights, Constraints constraints) {

    public CourseProperties {
        if (weights == null || constraints == null) {
            throw new IllegalStateException("course.weights와 course.constraints는 필수 설정입니다");
        }
    }

    /** 추천 알고리즘 명세서 4장의 4개 항목. 체류 적합도는 여기 없다 — 제약으로 갔다(ADR-021). */
    public record Weights(int interest, int access, int dispersion, int completeness) {

        public Weights {
            int sum = interest + access + dispersion + completeness;
            if (sum != 100) {
                throw new IllegalStateException("course.weights 합계는 100이어야 합니다 — 현재 %d (interest=%d, access=%d, dispersion=%d, completeness=%d)"
                        .formatted(sum, interest, access, dispersion, completeness));
            }
        }
    }

    /**
     * 채점하지 않고 조립만 제약하는 값들. ADR-021이 체류 적합도를 점수에서 빼면서 옮겨온 자리이며,
     * 적용된 결과는 응답의 {@code constraints} 블록으로 나간다.
     *
     * <p>기간별 룩업 표가 아니라 <b>공식의 계수</b>다 — 날짜 범위 입력이 임의 박 수를 허용하므로
     * 표로는 표현할 수 없다(ADR-020).
     */
    public record Constraints(double placesPerDay, int maxNights, Map<Difficulty, Integer> baseDistanceKm) {

        public Constraints {
            if (placesPerDay <= 0) {
                throw new IllegalStateException("course.constraints.places-per-day는 양수여야 합니다 — 현재 " + placesPerDay);
            }
            if (maxNights < 1) {
                throw new IllegalStateException("course.constraints.max-nights는 1 이상이어야 합니다 — 현재 " + maxNights);
            }
            // 난이도 하나가 빠지면 그 난이도로 들어온 요청에서만 NPE가 난다 — 기동 시 전부 있는지 본다.
            List<Difficulty> missing = Arrays.stream(Difficulty.values())
                    .filter(d -> baseDistanceKm == null || !baseDistanceKm.containsKey(d))
                    .toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("course.constraints.base-distance-km에 난이도가 빠졌습니다 — " + missing);
            }
        }
    }
}
