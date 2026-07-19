-- 다국어를 "컬럼"이 아니라 "행"으로 관리한다: place_translation(언어당 1행, source로 공식/번역 구분).
-- 기존의 place 언어별 컬럼(title_en·overview_en·has_en·content_id_en·source_modified_at_en)과
-- place_summary(번역 캐시)를 이 한 테이블로 대체한다. 언어를 늘려도 스키마 변경 없이 행만 늘어난다.
CREATE TABLE place_translation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    lang VARCHAR(5) NOT NULL,
    title VARCHAR(200) NULL,
    overview LONGTEXT NULL,
    addr VARCHAR(255) NULL,
    source VARCHAR(20) NOT NULL,           -- OFFICIAL(공식 매칭) / TRANSLATE(기계 번역)
    src_content_id VARCHAR(20) NULL,        -- 공식일 때 해당 언어 서비스의 contentId
    src_modified_at DATETIME NULL,          -- 공식 캐싱 워터마크(값 그대로면 상세 재조회 생략)
    generated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_place_translation_place_lang UNIQUE (place_id, lang),
    CONSTRAINT fk_place_translation_place FOREIGN KEY (place_id) REFERENCES place (place_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_place_translation_lang ON place_translation (lang);

-- 언어별 텍스트/ID 컬럼을 제거해 place를 언어 중립으로 슬림화(content_id_en 인덱스는 컬럼과 함께 제거됨).
-- has_en은 남긴다 — "en 번역(공식/기계) 존재 여부" 캐시 플래그로, 추천 완성도 점수가 조인 없이 참조한다.
-- 언어마다 늘어나는 컬럼이 아니라 단일 boolean이라 정규화 취지(텍스트/ID 컬럼 폭발 제거)와 상충하지 않는다.
ALTER TABLE place
    DROP COLUMN title_en,
    DROP COLUMN overview_en,
    DROP COLUMN content_id_en,
    DROP COLUMN source_modified_at_en;

-- place_summary는 place_translation(source=TRANSLATE)으로 대체.
DROP TABLE place_summary;
