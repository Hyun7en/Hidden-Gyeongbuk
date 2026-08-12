package com.hiddengyeongbuk.backend.domain.place;

/**
 * {@link StagingPlace}의 동일 장소 판정 상태(아키텍처 §4.2).
 *
 * <p>보류 건을 별도 quarantine 테이블로 분리하지 않고 이 상태 컬럼으로 관리한다 — 이 규모
 * (언어당 수천 건, 보류는 그중 일부)에서 테이블을 나눌 실익이 없고, 재처리도 상태를
 * {@link #PENDING}으로 되돌리면 되기 때문이다.
 */
public enum ResolutionStatus {

    /** 아직 판정하지 않음. Resolver의 대상. */
    PENDING,

    /** 동일 장소 판정 완료. Merger의 대상이며 resolvedPlaceId가 채워져 있다. */
    MATCHED,

    /**
     * 판정이 애매해 보류. place를 만들지 않는다.
     *
     * <p><b>오매칭보다 미매칭을 택한다</b> — 오매칭은 서로 다른 장소를 합쳐 잘못된 정보를
     * 노출하고 탐지가 어려운 반면, 미매칭은 장소가 둘로 쪼개질 뿐이고 사후 병합이 가능하다.
     * 누적 건수는 데이터 품질 지표로 관측한다(NFR-10).
     */
    QUARANTINED
}
