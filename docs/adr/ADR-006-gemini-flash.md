# ADR-006 · LLM = Gemini Flash

**상태** Superseded by ADR-013 (2026-07-17) — 영문 콘텐츠 생성 용도는 Google Cloud Translation으로 교체. Gemini는 향후 챗봇 등 LLM이 실제로 필요한 기능용 예비로 전환(코드·설정 유지).  
**작성일자** 2026-05-29

## 컨텍스트

영문 관광정보가 없는 장소에 쉬운 영어 요약을 생성해야 하며, 공모전 프로젝트라 비용이 중요하다.

## 결정

Gemini Flash(Google AI Studio)를 사용한다. 영문 필드가 없을 때만 호출하고 결과를 캐싱하며, 범위를 제한하는 프롬프트를 쓴다.

## 근거

무료 티어(약 1,500 req/day, 카드 불필요)로 MVP를 커버하고, 초과 시에도 최저가 수준이며 한→영 다국어 품질이 양호하다. 캐싱으로 호출량을 최소화한다.

## 결과

무료 티어는 입력이 학습에 활용될 수 있으므로 공개 TourAPI 데이터만 사용한다(개인정보 없음). API 키는 백엔드 환경변수로 관리(NFR-09).

## 대안

GPT-4o-mini·Claude Haiku — 무료 티어 관대함에서 Gemini에 열세; 국산(Solar·HyperCLOVA) — 비용·접근성에서 Gemini가 무난 → Gemini Flash 선택.
