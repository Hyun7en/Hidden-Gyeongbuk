-- hub.description이 국문뿐이라 EN 모드 허브 상세에 한국어가 그대로 노출되던 문제.
-- 외국인 우선 서비스이므로 영문 설명 컬럼을 추가하고 4개 허브 값을 채운다.
ALTER TABLE hub
    ADD COLUMN description_en VARCHAR(255) AFTER description;

UPDATE hub SET description_en = 'Tradition · Nature · Temples' WHERE name_ko = '안동';
UPDATE hub SET description_en = 'Daegu gateway · Transfer hub' WHERE name_ko = '동대구';
UPDATE hub SET description_en = 'East coast · Sea'             WHERE name_ko = '포항';
UPDATE hub SET description_en = 'Inland · Mountains'           WHERE name_ko = '북부내륙';
