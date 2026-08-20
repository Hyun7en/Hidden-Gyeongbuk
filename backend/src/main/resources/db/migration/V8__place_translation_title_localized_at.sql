-- 제목 localization이 끝난 (장소, 언어) 조합을 표시한다(ADR-026).
--
-- 왜 필요한가. place_translation.source는 OFFICIAL/TRANSLATE 둘뿐이고 여기에 엔진을 더하지
-- 않기로 했다(폴백·공식 덮어쓰기 규칙이 따라 복잡해진다). 그래서 DB만 봐서는 이 제목을
-- Google 번역이 만들었는지 Gemini localization이 만들었는지 구별할 수 없고, 재실행할 때마다
-- 1,105건 전부를 다시 부르게 된다 — 무료 티어가 분당 5회라 그 대가가 크다.
--
-- generated_at으로는 안 된다: OFFICIAL·TRANSLATE 전 행에 이미 채워져 있어(2026-08-20 실측
-- null 0건) 구별 근거가 되지 못한다.
--
-- 언어별로 판정되므로 언어를 추가해도 스키마가 그대로다(ADR-018).
ALTER TABLE place_translation
    ADD COLUMN title_localized_at DATETIME NULL COMMENT '제목 LLM localization 완료 시각(ADR-026). NULL이면 미처리';
