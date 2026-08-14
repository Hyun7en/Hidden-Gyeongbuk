# 의사결정 기록 (ADR)

개발 중 주요 기술·설계 결정 기록. **이 디렉터리가 ADR의 정본이다** — 2026-08-13에 Notion에서 이전했다.

## 기록 원칙

**ADR은 추가만 하고 수정하지 않는다.** 결정이 바뀌면 기존 ADR을 고치는 대신 상태를 `Superseded`로 바꾸고 새 ADR을 쓴다. "왜 그렇게 정했다가 왜 바꿨는지"가 남아야 같은 논의를 반복하지 않는다.

그래서 **번호가 큰 쪽이 이긴다.** ADR-015의 결론을 인용하기 전에 ADR-017이 그 일부를 뒤집지 않았는지 확인할 것. 낡은 내용도 지우지 않고, 후속 결정을 가리키는 `## 후속 주석`만 덧붙인다.

새 ADR은 [`ADR-template.md`](ADR-template.md)를 복사해 쓴다. 상태는 `Proposed` · `Accepted` · `Deprecated` · `Superseded` 넷.

## 결정 요약

| ID | 결정 | 상태 |
|---|---|---|
| [001](ADR-001-batch-ingest.md) | TourAPI 데이터 수집 = 배치 적재 | Accepted |
| [002](ADR-002-react-spa.md) | 프론트 = React SPA (Vite·TS) | Accepted (PWA 근거는 022로 폐기) |
| [003](ADR-003-realtime-scoring.md) | 추천 점수 = 요청 시 실시간 계산 | Accepted |
| [004](ADR-004-haversine-difficulty.md) | 거리·난이도 = Haversine + 등급화 | Accepted |
| [005](ADR-005-in-memory-cache.md) | 캐시 = 인메모리 → Redis(확장) | Accepted |
| [006](ADR-006-gemini-flash.md) | LLM = Gemini Flash | **Superseded → 013** |
| [007](ADR-007-response-time-sla.md) | 응답시간 SLA = 캐싱 후 실측 | **Proposed** (미해결) |
| [008](ADR-008-bigdata-reference-only.md) | 관광 빅데이터 = MVP 참고용 | Accepted |
| [009](ADR-009-anonymous-mvp.md) | 인증 = MVP 미적용(익명) | Accepted |
| [010](ADR-010-kakao-map-deeplink.md) | 지도 = Kakao 표시 + 외부 딥링크 | Accepted |
| [011](ADR-011-schedule-15-regions.md) | 일정 = 마감 9/21, 15곳 유지 | Accepted |
| [012](ADR-012-flyway.md) | DB 스키마 관리 = Flyway | Accepted (1회 예외는 023) |
| [013](ADR-013-google-translation.md) | 영문 콘텐츠 = Google Cloud Translation | Accepted (저장 위치는 015로 변경) |
| [014](ADR-014-eng-service-matching.md) | 영문 = EngService2 독자 수집 + 이름·좌표 매칭 | **Superseded → 016** |
| [015](ADR-015-place-translation.md) | 다국어 저장 = place_translation(언어=행) | Accepted (has_en 부분은 017로 변경) |
| [016](ADR-016-staging-pipeline.md) | 수집 = 언어별 독립 staging → Resolution → Merge | Accepted |
| [017](ADR-017-drop-has-en.md) | 언어 커버리지 = place_translation 조회 (has_en 폐기) | Accepted |
| [018](ADR-018-multilingual-structure.md) | 다국어 = 구조는 N개 언어, 실데이터는 국·영문 우선 | Accepted |
| [019](ADR-019-hub-plus-regions.md) | 추천 범위 = 거점 필수 + 지역 다중선택, 홈은 지역 카탈로그 | Accepted |
| [020](ADR-020-date-range-input.md) | 여행 기간 = 날짜 범위(캘린더), duration enum 폐기 | Accepted |
| [021](ADR-021-stay-fit-to-constraint.md) | 체류 적합도 = 점수 제외, 제약으로 전환 | Accepted |
| [022](ADR-022-no-pwa-no-back-button.md) | PWA 미사용, 자체 뒤로가기 버튼 전면 제거 | Accepted |
| [023](ADR-023-migration-squash.md) | 마이그레이션 스쿼시 — 배포 전 1회 한정 | Accepted |
| [024](ADR-024-lcls-systm-place-type.md) | 장소 분류 = 관광공사 대분류 채택 (place_tag 폐기) | Accepted |

## 대체 관계

인용 전에 이 사슬을 확인할 것.

```
006 ──→ 013            LLM 요약 → 전용 번역 API
014 ──→ 016            국문 기준 순회 → 언어별 독립 staging
015 ──(has_en 부분)──→ 017
012 ──(1회 예외)────→ 023
002 ──(PWA 근거)────→ 022
014 ──(예고한 후속)──→ 024
```

## 다른 설계 문서와의 관계

ADR은 **결정과 그 근거**만 담는다. 결정의 결과로 만들어진 구체적 명세(테이블 컬럼, 응답 스키마, 화면 블록 구조)는 Notion의 설계 문서에 있다 — ERD/DB 설계서, API 명세서, 시스템 아키텍처 설계서, 외부 API 연동 설계서, 추천 알고리즘 명세서, 화면 설계서.

두 곳이 어긋나면 **번호가 큰 ADR이 이긴다.** 설계 문서는 ADR을 반영하는 쪽이다.
