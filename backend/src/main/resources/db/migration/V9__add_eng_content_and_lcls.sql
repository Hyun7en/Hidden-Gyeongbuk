-- 영문(EngService2)은 국문과 별개의 contentId 공간을 쓴다(실측 확인) — 매칭된 영문 서비스
-- contentId와 그 modifiedtime을 저장해 공식 영문 상세를 캐싱한다.
-- lclsSystm1/2/3은 관광공사 신분류체계 대/중/소분류 코드로, 목록 응답에 함께 오므로 수집 시
-- 함께 적재한다(현재는 저장만; 분류 기반 태그 개편은 후속 단계).
ALTER TABLE place
    ADD COLUMN content_id_en VARCHAR(20) NULL AFTER content_id,
    ADD COLUMN lcls_systm1 VARCHAR(10) NULL AFTER content_type_id,
    ADD COLUMN lcls_systm2 VARCHAR(10) NULL AFTER lcls_systm1,
    ADD COLUMN lcls_systm3 VARCHAR(20) NULL AFTER lcls_systm2,
    ADD COLUMN source_modified_at_en DATETIME NULL AFTER source_modified_at;

-- content_id_en은 유니크로 걸지 않는다 — 매칭이 불완전할 때 두 국문 place가 같은 영문
-- contentId 후보를 집으면 유니크 위반으로 저장이 깨진다. 1:1은 매처가 메모리에서 보장하고,
-- 여기서는 조회용 일반 인덱스만 둔다.
CREATE INDEX idx_place_content_id_en ON place (content_id_en);
