-- place_summary.source에 TRANSLATE 추가.
-- 2026-07-17에 생성 엔진이 Gemini(LLM 요약)에서 Google Cloud Translation(전문 번역)으로
-- 바뀌었는데 enum이 그대로라 새 행들이 LLM으로 잘못 표기돼 왔다. 이후 새 행은 TRANSLATE를 쓴다.
-- 기존 행은 어느 엔진 산출물인지 행 단위로 구분할 수 없어 값을 소급 수정하지 않는다.
ALTER TABLE place_summary
    MODIFY COLUMN source ENUM('LLM','TOURAPI','TRANSLATE') NOT NULL;
