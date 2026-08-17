package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 동일 장소 판정 로직(외부 API 연동 설계서 §2.6.3). <b>순수 계산이며 DB를 건드리지 않는다.</b>
 *
 * <p>Resolver(상태 기록)와 Merger(place 생성)가 같은 판정을 봐야 하는데, 판정 결과를 담을
 * 컬럼이 {@code resolved_place_id} 하나뿐이고 그건 place가 생긴 뒤에야 채울 수 있다. 그래서
 * 두 단계가 이 컴포넌트를 각각 호출한다 — 입력이 같으면 결과도 같은 결정적 로직이라 안전하고,
 * 외부 호출이 없어 두 번 도는 비용도 무시할 수 있다.
 *
 * <p>판정 키의 우선순위는 <b>좌표 → 이름</b>이다. 구 구현(ADR-014)은 이름이 1순위였는데,
 * 이름 표기는 언어마다 다르고 좌표는 언어 무관이라 뒤집었다(ADR-016).
 */
@Component
public class IdentityGrouper {

    /**
     * 좌표 근접 가드. 이 반경 밖은 같은 장소로 보지 않는다.
     *
     * <p><b>2026-08-17 실측으로 1km → 5km.</b> 이름이 정확히 일치하는 490쌍의 국문·영문 좌표
     * 거리를 재 보니 중간값 0.00km(절반 이상이 소수점까지 동일)에 95.3%가 1km 안이었는데,
     * 나머지가 1.02km(죽계구곡)·1.40km(영양 자작나무숲)처럼 <b>아깝게 밖</b>이었다. 관광공사가
     * 같은 장소를 언어별로 등록할 때 찍은 점이 조금씩 다르다(주차장 vs 입구 등).
     *
     * <p>반경별로 잡히는 쌍: 1km 467 / 2km 481 / 3km 485 / <b>5km 489</b> / 10km 490.
     * 5km면 8.57km짜리 하나만 남는다.
     *
     * <p>넓혀도 안전한 이유는 <b>확정은 이름이 하기 때문</b>이다. 반경은 후보를 좁히는 역할일
     * 뿐이고, 국문명이 글자 단위로 완전히 일치해야 확정된다. 같은 이름이 여럿 걸리면 지금처럼
     * 보류로 빠지며, 15개 지역 전체에서 동명 복수는 8건뿐이었다.
     *
     * <p>좌표를 1순위로 둔 ADR-016의 판단 자체는 유지된다 — 좌표는 대체로 정확했고, 틀린 것은
     * "1km면 충분하다"는 가정이었다.
     */
    static final double PROXIMITY_GUARD_KM = 5.0;

    /**
     * @param groups 같은 장소로 판정된 staging 행 묶음. 각 묶음의 첫 원소가 대표(앵커)다
     * @param quarantined 판정이 애매해 보류된 행 → 사유
     */
    public record Grouping(List<List<StagingPlace>> groups, Map<StagingPlace, String> quarantined) {}

    /**
     * 기준 언어(ko) 행을 앵커로 삼아 다른 언어 행을 붙인다. 기준 언어에 없는 장소는 자기들끼리
     * 독립 그룹이 되므로 누락되지 않는다 — 어느 한 언어에만 존재하는 장소도 버리지 않는다(ADR-016).
     */
    public Grouping group(List<StagingPlace> rows) {
        List<StagingPlace> anchors = new ArrayList<>();
        List<StagingPlace> others = new ArrayList<>();
        for (StagingPlace row : rows) {
            if (LanguageRegistry.BASE.equalsIgnoreCase(row.getLang())) {
                anchors.add(row);
            } else {
                others.add(row);
            }
        }

        Map<StagingPlace, List<StagingPlace>> byAnchor = new LinkedHashMap<>();
        anchors.forEach(a -> byAnchor.computeIfAbsent(a, k -> new ArrayList<>()).add(a));
        Map<StagingPlace, String> quarantined = new LinkedHashMap<>();

        for (StagingPlace candidate : others) {
            Outcome outcome = findAnchor(candidate, anchors);
            if (outcome.anchor() != null) {
                byAnchor.get(outcome.anchor()).add(candidate);
            } else if (outcome.reason() != null) {
                quarantined.put(candidate, outcome.reason());
            } else {
                // 후보가 아예 없음 = 그 언어에만 존재하는 장소. 단독 그룹으로 살린다.
                byAnchor.computeIfAbsent(candidate, k -> new ArrayList<>()).add(candidate);
            }
        }

        return new Grouping(List.copyOf(byAnchor.values()), quarantined);
    }

    private record Outcome(StagingPlace anchor, String reason) {}

    /**
     * 좌표로 후보를 좁힌 뒤 이름으로 확정한다.
     *
     * <ul>
     *   <li>좌표 없음 → 보류. 1순위 키가 없으면 나머지 근거는 약하다
     *   <li>좌표 후보 0개 → 그 언어에만 있는 장소
     *   <li>이름 일치가 <b>정확히 1건</b>일 때만 확정 — 0건·복수는 보류
     * </ul>
     *
     * <p>이름을 못 뽑으면 좌표만 남는데, 1km 안에 여러 장소가 있는 것은 흔하다. 좌표 단독
     * 판정은 오매칭 위험이 커서 후보가 1건이어도 보류한다.
     */
    private Outcome findAnchor(StagingPlace candidate, List<StagingPlace> anchors) {
        if (candidate.getLat() == null || candidate.getLng() == null) {
            return new Outcome(null, "좌표 없음");
        }

        List<StagingPlace> nearby = anchors.stream()
                .filter(a -> a.getLat() != null && a.getLng() != null)
                .filter(a -> distanceKm(a, candidate) <= PROXIMITY_GUARD_KM)
                .toList();

        if (nearby.isEmpty()) {
            return new Outcome(null, null);
        }

        String candidateName = KoreanNameExtractor.normalize(KoreanNameExtractor.extract(candidate.getTitle()));
        if (candidateName == null) {
            return new Outcome(null, "이름 추출 불가(좌표 후보 " + nearby.size() + "건)");
        }

        List<StagingPlace> nameMatches = nearby.stream()
                .filter(a -> candidateName.equals(KoreanNameExtractor.normalize(a.getTitle())))
                .toList();

        if (nameMatches.size() == 1) {
            return new Outcome(nameMatches.get(0), null);
        }
        if (nameMatches.isEmpty()) {
            return new Outcome(null, "이름 불일치(좌표 후보 " + nearby.size() + "건)");
        }
        return new Outcome(null, "동일 이름 복수 후보 " + nameMatches.size() + "건");
    }

    private static double distanceKm(StagingPlace a, StagingPlace b) {
        return GeoUtils.haversineKm(
                toDouble(a.getLat()), toDouble(a.getLng()), toDouble(b.getLat()), toDouble(b.getLng()));
    }

    private static double toDouble(BigDecimal value) {
        return value.doubleValue();
    }
}
