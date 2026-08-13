-- ─────────────────────────────────────────────────────────────
-- V3 · 분류체계 전환 (ADR-024)
--
-- place_type의 값 체계를 contentTypeId 파생 7종에서 관광공사 신분류체계 대분류 9종으로
-- 바꾸고, 자체 관심사 태그(place_tag)를 폐기한다. 관심사는 이제 place_type과 같은 어휘를 쓴다.
--
-- 스쿼시(ADR-023) 이후 첫 증분 마이그레이션이다. 앞으로는 다시 V파일을 쌓는다.
--
-- 재수집하지 않는다. 대분류 원본이 place.lcls_systm1에 이미 적재돼 있어 그 값을 그대로
-- 옮기면 되고, place_translation(공식·기계 번역)도 보존된다 — 번역은 무료 한도를 써서
-- 만든 자산이라 지울 이유가 없다.
-- ─────────────────────────────────────────────────────────────

-- 1) 대분류 원본이 있는 행은 그 값을 그대로 쓴다. 이게 정상 경로다.
--    C01(추천코스)은 애초에 수집 대상이 아니라 존재하지 않지만, 있더라도 여기 걸리지 않는다.
UPDATE place
SET place_type = UPPER(TRIM(lcls_systm1))
WHERE lcls_systm1 IS NOT NULL
  AND UPPER(TRIM(lcls_systm1)) IN ('AC', 'EV', 'EX', 'FD', 'HS', 'LS', 'NA', 'SH', 'VE');

-- 2) 대분류가 비어 있으면 구 place_type으로 폴백한다. 런타임의 contentTypeId 폴백과 같은
--    표다(application.yml app.content-type). 1:1 대응이 명확한 것만 있다.
UPDATE place SET place_type = 'VE' WHERE place_type = 'CULTURAL';
UPDATE place SET place_type = 'EV' WHERE place_type = 'FESTIVAL';
UPDATE place SET place_type = 'LS' WHERE place_type = 'LEISURE';
UPDATE place SET place_type = 'AC' WHERE place_type = 'STAY';
UPDATE place SET place_type = 'SH' WHERE place_type = 'SHOPPING';
UPDATE place SET place_type = 'FD' WHERE place_type = 'RESTAURANT';

-- 3) 남은 것은 구 ATTRACTION(관광지)뿐이다. 대분류에서 NA·HS·VE·EX 넷으로 갈라져 단일
--    대응이 없어 폴백할 수 없다 — 임의로 하나를 고르는 것은 ADR-024가 없애려던 "우리가
--    분류를 판정하는" 행위 그 자체다. 해당 place를 지우고 staging을 보류로 되돌려,
--    다음 수집에서 대분류가 채워지면 자연히 복구되게 한다.
--
--    자식 행 → place 순으로 지운다(FK).
DELETE FROM festival_detail WHERE place_id IN (SELECT place_id FROM place WHERE place_type = 'ATTRACTION');
DELETE FROM place_image     WHERE place_id IN (SELECT place_id FROM place WHERE place_type = 'ATTRACTION');
DELETE FROM place_tag       WHERE place_id IN (SELECT place_id FROM place WHERE place_type = 'ATTRACTION');
DELETE FROM place_translation WHERE place_id IN (SELECT place_id FROM place WHERE place_type = 'ATTRACTION');

UPDATE staging_place
SET resolution_status = 'QUARANTINED',
    quarantine_reason = '분류 없음 (lclsSystm1 결측, contentTypeId 폴백 불가)',
    resolved_place_id = NULL
WHERE resolved_place_id IN (SELECT place_id FROM place WHERE place_type = 'ATTRACTION');

DELETE FROM place WHERE place_type = 'ATTRACTION';

-- 4) 자체 태그 폐기. 부여 규칙을 우리가 만들어야 했는데 끝내 미정으로 남았고, 그 위에
--    추천 가중치 35%가 얹혀 있었다. 분류축은 place_type 하나로 합쳐진다.
DROP TABLE place_tag;

-- 5) 컬럼 정리. 대분류는 place_type으로 흡수됐고, 중·소분류는 staging_place에만 남긴다 —
--    세분 필터가 필요해지면 그때 컬럼을 추가하고 Merge를 재실행하면 된다(Merge는 외부
--    호출이 없어 자유롭게 재실행 가능). 측정 전에 비정규화하지 않는다(ADR-007/017).
ALTER TABLE place
    DROP COLUMN lcls_systm1,
    DROP COLUMN lcls_systm2,
    DROP COLUMN lcls_systm3,
    MODIFY COLUMN place_type VARCHAR(10) NOT NULL COMMENT '관광공사 신분류체계 대분류 코드 (AC/EV/EX/FD/HS/LS/NA/SH/VE)';
