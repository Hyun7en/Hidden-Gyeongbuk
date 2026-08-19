-- tel 폭을 50 → 255로 넓힌다.
--
-- 원인(2026-08-19 실측): 영문 축제(contentTypeId=85) 8건의 tel에 전화번호가 아니라
-- 안내문이 들어온다 — "• 1330 Travel Hotline: +82-2-1330<br>(Korean, English, Japanese,
-- Chinese)" 형태로 111~113자다. VARCHAR(50)이라 MySQL이 거부하고, Collector의
-- per-item try/catch가 그걸 삼켜 failed로 세고 있었다. 영문 수집 결과의 failed=8이
-- 정확히 이 8건이며, 국문은 짧은 번호가 와서 걸리지 않았다.
--
-- 폭은 실측 최대 113자의 두 배쯤인 255로 둔다. 소스가 이 칸에 자유 텍스트를 넣는다는
-- 것이 확인됐으므로 전화번호 길이를 기준으로 잡을 수 없다.
--
-- staging_place는 원본을 그대로 보존하고(ADR-016), place.tel은 Merge에서 HTML 태그를
-- 걷어낸 값이 들어간다 — 화면에 그대로 표시되는 값이라 <br>이 보이면 안 된다.
ALTER TABLE staging_place MODIFY COLUMN tel VARCHAR(255) NULL COMMENT '원본 그대로. 소스가 안내문을 넣기도 한다';
ALTER TABLE place         MODIFY COLUMN tel VARCHAR(255) NULL COMMENT 'HTML 제거·공백 정리된 값 (언어 무관)';
