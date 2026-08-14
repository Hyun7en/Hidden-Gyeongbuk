# ADR-015 · 다국어 저장 = place_translation(언어=행) 정규화

**상태** Accepted (has_en 존치 부분만 ADR-017로 변경)  
**작성일자** 2026-07-19

## 컨텍스트

영문을 place의 언어별 컬럼(title_en·overview_en·content_id_en·source_modified_at_en)과 place_summary(번역 캐시)에 나눠 저장했다. 중문·일문 등 TourAPI가 제공하는 다른 언어(각 언어가 영문처럼 별도 contentId 공간)를 추가하면 언어마다 컬럼이 5개씩 늘어 테이블이 옆으로 폭발하고, 공식(place)·번역(place_summary)이 갈라져 조회도 복잡했다.

## 결정

언어를 "컬럼"이 아니라 "행"으로 관리한다. place_translation(place_id, lang, title, overview, addr, source, src_content_id, src_modified_at, generated_at), UNIQUE(place_id, lang). 언어당 1행이고 source(OFFICIAL/TRANSLATE)로 공식·번역을 통합한다. place에는 국문 원본만 남기고, place_summary와 언어별 컬럼은 제거한다(V10). 공식이 매칭되면 같은 (place_id, lang) 행을 OFFICIAL로 upsert해 번역을 덮어쓴다.

## 근거

언어를 늘려도 스키마 변경 없이 행만 추가된다. 공식/번역이 한 행에 통합돼 조회 폴백이 "번역행 → 국문" 2단계로 단순해진다(제목·설명·주소 공통). @MapsId 대신 place_id 스칼라 + surrogate PK를 써 PlaceSummary에서 겪은 detached/낙관락 함정도 피한다.

## 결과

has_en은 "en 번역 존재" 캐시 플래그로 place에 남겨 추천 완성도 점수가 조인 없이 참조한다(단일 boolean이라 언어마다 늘지 않음). 조회 응답은 title/overview/addr을 이미 언어 해석해 내려주고, 상세는 translationSource(OFFICIAL/TRANSLATE/null)로 출처를 알린다. 기존 데이터는 전량 재수집으로 재구성(클린 슬레이트).

## 대안

① 언어별 컬럼 유지 — 언어 추가 시 컬럼 폭발·공식/번역 분리 지속 → 배제. ② place당 언어 JSON 컬럼 — 인덱싱·부분 갱신·검색이 불리 → 배제.

## 후속 주석

**2026-07-30** — 핵심 결정(place_translation 언어=행 정규화)은 그대로 유효하며 ADR-018의 다국어 확장 구조의 토대가 된다. 다만 "결과"에 기술된 `has_en` 존치는 ADR-017에서 폐기되었다 — 영어만 특별 취급하는 전제가 다국어 확장과 맞지 않기 때문.
