package com.hiddengyeongbuk.backend.domain.sync;

/**
 * 수집 파이프라인 4단(아키텍처 §4.2). {@code data_sync_log.phase}에 기록해 어느 단계가
 * 실패했는지 구분한다 — 외부 호출이 있는 단계(COLLECT·TRANSLATE)와 없는 단계(RESOLVE·MERGE)는
 * 재실행 비용이 전혀 달라서, 한 덩어리로 기록하면 무엇을 다시 돌려도 되는지 알 수 없다.
 */
public enum SyncPhase {

    /** 언어별 서비스를 독립 수집해 staging_place에 원본 적재. 외부 호출 있음, 증분 워터마크 기준. */
    COLLECT,

    /** staging 간 동일 장소 판정. 외부 호출 없음 — 자유롭게 재실행 가능. */
    RESOLVE,

    /** MATCHED 건을 place + place_translation으로 병합. 외부 호출 없음 — 자유롭게 재실행 가능. */
    MERGE,

    /** 행이 없는 (장소, 언어) 조합에 기계 번역 생성. 외부 호출 있음, 월 50만 자 한도. */
    TRANSLATE
}
