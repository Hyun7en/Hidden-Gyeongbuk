package com.hiddengyeongbuk.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.support.LanguageRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 동일 장소 판정. 실제 TourAPI 데이터에서 관측된 좌표 어긋남을 회귀로 묶는다.
 *
 * <p>좌표 근접 가드가 1km였을 때 <b>이름이 글자 단위로 같은데도</b> 짝을 못 찾은 사례가 있었다
 * (죽계구곡 1.02km, 영양 자작나무숲 1.40km). 후보가 하나도 없으면 quarantine이 아니라 "그 언어에만
 * 있는 장소"로 판정돼 place가 하나 더 생기므로, <b>중복이 조용히 만들어진다</b> — 보류 목록에도
 * 안 잡혀서 발견이 어렵다. 그래서 이 경계를 테스트로 고정한다.
 */
class IdentityGrouperTest {

    private final IdentityGrouper grouper = new IdentityGrouper();

    /** 위도 1도 ≈ 111km. 북쪽으로 원하는 거리만큼 떨어진 좌표를 만든다. */
    private static StagingPlace row(String lang, String title, double kmNorth) {
        return StagingPlace.builder()
                .lang(lang)
                .sourceContentId(lang + "-" + title.hashCode() + "-" + kmNorth)
                .title(title)
                .lat(BigDecimal.valueOf(36.5 + kmNorth / 111.0))
                .lng(BigDecimal.valueOf(128.5))
                .build();
    }

    @Test
    void 이름이_같으면_좌표가_1km_넘게_어긋나도_같은_장소로_묶는다() {
        // 영양 자작나무숲 실측: 국문·영문 좌표가 1.40km 떨어져 있고 이름은 완전히 일치한다.
        StagingPlace ko = row(LanguageRegistry.BASE, "영양 자작나무숲", 0);
        StagingPlace en = row("en", "Yeongyang Birch Forest (영양 자작나무숲)", 1.4);

        IdentityGrouper.Grouping g = grouper.group(List.of(ko, en));

        assertThat(g.quarantined()).isEmpty();
        assertThat(g.groups()).hasSize(1);
        assertThat(g.groups().get(0)).containsExactly(ko, en);
    }

    @Test
    void 가드를_넘게_멀면_그_언어에만_있는_장소로_남긴다() {
        // 이름이 같아도 너무 멀면 붙이지 않는다 — 동명의 다른 장소일 수 있다.
        // 버리지는 않는다: 단독 그룹이 되어 place는 만들어진다(ADR-016).
        StagingPlace ko = row(LanguageRegistry.BASE, "청송 얼음골", 0);
        StagingPlace en = row("en", "Cheongsong Ice Valley (청송 얼음골)", 30);

        IdentityGrouper.Grouping g = grouper.group(List.of(ko, en));

        assertThat(g.quarantined()).isEmpty();
        assertThat(g.groups()).hasSize(2);
    }

    @Test
    void 가드_안에_후보가_있어도_이름이_다르면_보류한다() {
        // 오매칭보다 미매칭을 택한다 — 넓힌 반경이 이 방어막을 무너뜨리지 않는지 지킨다.
        StagingPlace ko = row(LanguageRegistry.BASE, "운문사계절 캠핑장", 0);
        StagingPlace en = row("en", "Unmunsa Temple (운문사)", 3.9);

        IdentityGrouper.Grouping g = grouper.group(List.of(ko, en));

        assertThat(g.quarantined()).containsOnlyKeys(en);
        assertThat(g.quarantined().get(en)).startsWith("이름 불일치");
    }

    @Test
    void 넓힌_반경_안에_같은_이름이_여럿이면_보류한다() {
        // 반경을 넓히면 동명 후보가 늘 수 있다. 그때는 확정하지 않는다.
        StagingPlace ko1 = row(LanguageRegistry.BASE, "약수터", 0);
        StagingPlace ko2 = row(LanguageRegistry.BASE, "약수터", 3);
        StagingPlace en = row("en", "Mineral Spring (약수터)", 1.5);

        IdentityGrouper.Grouping g = grouper.group(List.of(ko1, ko2, en));

        assertThat(g.quarantined()).containsOnlyKeys(en);
        assertThat(g.quarantined().get(en)).startsWith("동일 이름 복수 후보");
    }

    @Test
    void 좌표가_없으면_보류한다() {
        StagingPlace ko = row(LanguageRegistry.BASE, "하회마을", 0);
        StagingPlace en = StagingPlace.builder()
                .lang("en")
                .sourceContentId("en-nocoord")
                .title("Hahoe Village (하회마을)")
                .build();

        IdentityGrouper.Grouping g = grouper.group(List.of(ko, en));

        assertThat(g.quarantined()).containsEntry(en, "좌표 없음");
    }
}
