-- Hidden Gyeongbuk 초기 스키마 — ERD/DB 설계서 v0.9 기준.
--
-- 이 파일은 구 V1~V11(2026-05~07)을 스쿼시한 결과다. 구 스키마는 국문을 특별대우하는 구조였고
-- (텍스트가 place 컬럼에, 영문만 별도 테이블) 설계 재검토에서 전면 폐기됐다. 증분 마이그레이션을
-- 이어붙이면 15개 파일 중 절반이 이미 삭제된 테이블·컬럼을 기술하게 되고, 무엇보다 ERD와
-- 1:1 대조가 불가능해진다. 어차피 ERD §7이 클린 슬레이트(전량 재수집)를 전제하므로
-- 증분의 이득(데이터 보존)이 없어 스쿼시를 택했다 — 근거는 ADR-023.
--
-- 설계 원칙 (ERD §1.2):
--   1. 언어 중립 / 언어 의존 분리 — 표시 텍스트는 예외 없이 번역 테이블 행으로. 국문도 포함.
--   2. 원본 보존 — 외부 API 응답은 staging_place에 그대로. 해석·판정은 별도 단계.
--   3. 멱등성 — 모든 적재는 자연키 기준 upsert.
--   4. 소스 식별자는 소스 계층에 — contentId 같은 외부 식별자는 staging/translation에만.
--   5. 측정 전 비정규화하지 않는다 — 조인 회피용 캐시 컬럼을 선제적으로 두지 않는다.
--
-- 이 파일 이후로는 스키마 변경 시 새 버전(V2, V3…)을 추가하고, 이미 적용된 파일은 수정하지 않는다
-- (Flyway가 체크섬으로 감지해 실패시킨다, ADR-012).


-- ─────────────────────────────────────────────────────────────
-- 교통 거점
-- ─────────────────────────────────────────────────────────────

-- 표시명·성격 설명은 hub_translation으로 간다. 여기 남는 건 좌표(접근성 점수 기준점)와
-- 설정·URL 파라미터에서 참조하는 내부 코드뿐이다.
CREATE TABLE hub (
    hub_id BIGINT      NOT NULL AUTO_INCREMENT,
    code   VARCHAR(30) NOT NULL,               -- ANDONG / DONGDAEGU / POHANG / NORTH_INLAND
    lat    DECIMAL(10, 7) NOT NULL,
    lng    DECIMAL(10, 7) NOT NULL,
    PRIMARY KEY (hub_id),
    CONSTRAINT uk_hub_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE hub_translation (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    hub_id      BIGINT      NOT NULL,
    lang        VARCHAR(10) NOT NULL,          -- BCP 47
    name        VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL,             -- 성격 (전통·자연·사찰 등)
    PRIMARY KEY (id),
    CONSTRAINT uk_hub_translation_hub_lang UNIQUE (hub_id, lang),
    CONSTRAINT fk_hub_translation_hub FOREIGN KEY (hub_id) REFERENCES hub (hub_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ─────────────────────────────────────────────────────────────
-- 인구감소지역
-- ─────────────────────────────────────────────────────────────

CREATE TABLE region (
    region_id       BIGINT      NOT NULL AUTO_INCREMENT,
    ldong_regn_cd   VARCHAR(2)  NOT NULL,      -- 법정동 시도코드 (경북 = 47)
    ldong_signgu_cd VARCHAR(3)  NOT NULL,      -- 법정동 시군구코드
    hub_id          BIGINT      NOT NULL,
    lat             DECIMAL(10, 7) NULL,
    lng             DECIMAL(10, 7) NULL,
    -- 인구감소지역 지정 여부. 지정이 해제돼도 데이터를 지우지 않고 false로 전환한다.
    -- Collector는 active=true 행만 순회하므로, 지정 변경에 행 추가·수정으로 대응하고
    -- 코드를 고치지 않는다(NFR-06).
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (region_id),
    CONSTRAINT uk_region_ldong_code UNIQUE (ldong_regn_cd, ldong_signgu_cd),
    CONSTRAINT fk_region_hub FOREIGN KEY (hub_id) REFERENCES hub (hub_id),
    KEY idx_region_hub_active (hub_id, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE region_translation (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    region_id   BIGINT      NOT NULL,
    lang        VARCHAR(10) NOT NULL,
    name        VARCHAR(50) NOT NULL,          -- ko=안동, en=Andong (로마자는 국립국어원 표기법)
    -- 지역 소개 글 (S2 화면, GET /api/regions/{id}). TourAPI 수집 대상이 아니라
    -- 직접 작성해 seed로 투입한다 — 콘텐츠 작업 미완이라 현재는 NULL.
    description TEXT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_region_translation_region_lang UNIQUE (region_id, lang),
    CONSTRAINT fk_region_translation_region FOREIGN KEY (region_id) REFERENCES region (region_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ─────────────────────────────────────────────────────────────
-- 장소 (언어 중립) + 언어별 콘텐츠
-- ─────────────────────────────────────────────────────────────

-- 언어에 의존하는 값은 하나도 두지 않는다. 표시 텍스트는 전부 place_translation에 있다.
-- place에는 자연키가 없다 — 정체성은 Resolution 결과로만 결정되며 외부 식별자에 의존하지 않는다.
-- 중복 생성 방지는 staging_place.resolved_place_id가 담당한다.
CREATE TABLE place (
    place_id    BIGINT      NOT NULL AUTO_INCREMENT,
    region_id   BIGINT      NOT NULL,          -- staging의 법정동코드로 결정
    -- 언어 중립 내부 타입: ATTRACTION / CULTURAL / FESTIVAL / LEISURE / STAY / SHOPPING / RESTAURANT
    -- 소스의 contentTypeId는 언어마다 코드가 다르므로(ko=12, en=76 …) 여기 저장하지 않는다.
    -- (place_type, lang) → source_type_id 매핑은 DB가 아니라 설정 파일에 둔다(아키텍처 §4.3).
    place_type  VARCHAR(20) NOT NULL,
    lat         DECIMAL(10, 7) NULL,           -- Haversine 거리 계산 (ADR-004)
    lng         DECIMAL(10, 7) NULL,
    tel         VARCHAR(50)  NULL,
    first_image VARCHAR(500) NULL,
    -- 관광공사 신분류체계 대/중/소분류 — 관심사 태깅의 근거가 될 예정(부여 규칙은 미정, ERD §9).
    lcls_systm1 VARCHAR(10)  NULL,
    lcls_systm2 VARCHAR(10)  NULL,
    lcls_systm3 VARCHAR(20)  NULL,
    created_at  DATETIME(6)  NULL,
    updated_at  DATETIME(6)  NULL,
    PRIMARY KEY (place_id),
    CONSTRAINT fk_place_region FOREIGN KEY (region_id) REFERENCES region (region_id),
    KEY idx_place_region_type (region_id, place_type),
    KEY idx_place_coord (lat, lng)             -- 주변 조회 (FR-S-04)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 모든 언어가 동등하다. 국문도 여기 들어간다.
-- 언어를 늘려도 스키마가 바뀌지 않고 행만 늘어난다(ADR-015, ADR-018).
CREATE TABLE place_translation (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    place_id        BIGINT       NOT NULL,
    lang            VARCHAR(10)  NOT NULL,     -- BCP 47 (ko, en, ja, zh-CN…)
    title           VARCHAR(200) NOT NULL,
    overview        LONGTEXT     NULL,
    addr            VARCHAR(255) NULL,         -- 언어별 표기가 다르므로 번역 계층에 둔다
    -- OFFICIAL(해당 언어 서비스 원본) / TRANSLATE(기계 번역).
    -- 공식이 확보되면 같은 행을 OFFICIAL로 덮어쓴다 — 단방향이며 역방향은 없다.
    source          VARCHAR(20)  NOT NULL,
    src_content_id  VARCHAR(20)  NULL,         -- OFFICIAL일 때 출처 서비스의 contentid
    src_modified_at DATETIME     NULL,         -- OFFICIAL 캐싱 워터마크 (상세 재조회 생략 판단)
    generated_at    DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_place_translation_place_lang UNIQUE (place_id, lang),
    CONSTRAINT fk_place_translation_place FOREIGN KEY (place_id) REFERENCES place (place_id),
    KEY idx_place_translation_lang_source (lang, source)   -- 번역 백필 대상 조회
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ─────────────────────────────────────────────────────────────
-- 수집 원본 (staging)
-- ─────────────────────────────────────────────────────────────

-- 외부 API 응답을 변환 없이 적재한다. 언어별 테이블을 만들지 않고 lang 컬럼으로 구분한다.
--
-- staging은 임시 테이블이 아니라 재매칭의 근거다(ADR-016). 오퍼레이션별 일 1,000건 한도에서
-- 3,000건 재수집은 며칠이 걸리므로, 매칭 로직을 고칠 때마다 외부 API를 다시 부르지 않으려면
-- 원본을 상시 보존해야 한다. 보존은 언어당 최신 1세트이며 이력은 누적하지 않는다(아키텍처 §7).
CREATE TABLE staging_place (
    staging_id         BIGINT      NOT NULL AUTO_INCREMENT,
    lang               VARCHAR(10) NOT NULL,
    source_content_id  VARCHAR(20) NOT NULL,   -- 해당 언어 서비스의 contentid
    source_type_id     INT         NOT NULL,   -- 해당 언어 서비스의 contenttypeid (원본 그대로)
    ldong_regn_cd      VARCHAR(2)  NULL,
    ldong_signgu_cd    VARCHAR(3)  NULL,       -- Merge 시 region 매칭에 사용
    title              VARCHAR(200) NULL,
    overview           LONGTEXT    NULL,
    addr               VARCHAR(255) NULL,
    lat                DECIMAL(10, 7) NULL,    -- mapy — Resolution 1순위 키
    lng                DECIMAL(10, 7) NULL,    -- mapx
    tel                VARCHAR(50)  NULL,
    first_image        VARCHAR(500) NULL,
    lcls_systm1        VARCHAR(10)  NULL,
    lcls_systm2        VARCHAR(10)  NULL,
    lcls_systm3        VARCHAR(20)  NULL,
    -- TourAPI modifiedtime. 언어별 증분 워터마크이며 행별로 비교해 안 바뀐 항목의
    -- 상세조회(detailCommon2)를 생략한다 — 전역 워터마크가 아니다. 호출 한도 절약의 핵심.
    source_modified_at DATETIME    NULL,
    -- PENDING(미판정) / MATCHED(판정 완료) / QUARANTINED(애매 — 보류).
    -- 보류 건을 별도 테이블로 분리하지 않는다. 재처리도 상태를 PENDING으로 되돌리면 된다.
    resolution_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- MATCHED일 때만 존재. Merge 시 중복 생성을 막는 근거다.
    resolved_place_id  BIGINT      NULL,
    quarantine_reason  VARCHAR(200) NULL,      -- 수동 검토 근거
    collected_at       DATETIME    NOT NULL,
    PRIMARY KEY (staging_id),
    -- 멱등성의 근거. 언어마다 contentId 공간이 달라 언어를 빼면 의미가 없다(실측).
    CONSTRAINT uk_staging_place_lang_src UNIQUE (lang, source_content_id),
    -- ON DELETE 절을 두지 않는다(RESTRICT). SET NULL로 두면 status='MATCHED'인데
    -- resolved_place_id가 NULL인 모순 상태가 조용히 생긴다.
    CONSTRAINT fk_staging_place_resolved FOREIGN KEY (resolved_place_id) REFERENCES place (place_id),
    KEY idx_staging_place_status_lang (resolution_status, lang),  -- Resolver 대상 조회, QUARANTINED 집계
    KEY idx_staging_place_coord (lat, lng)     -- 좌표 근접 후보 탐색 (매칭 1순위 키)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ─────────────────────────────────────────────────────────────
-- 장소 부속
-- ─────────────────────────────────────────────────────────────

-- 태그 값은 언어 무관 내부 enum 코드다(NATURE / FOOD / TRADITION / TEMPLE / FESTIVAL / REST).
-- 한글 라벨을 저장하면 다국어 응답에 특정 언어 문자열이 섞인다 — 표시 라벨은 프론트 i18n 책임.
CREATE TABLE place_tag (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    place_id BIGINT      NOT NULL,
    tag      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_place_tag UNIQUE (place_id, tag),
    CONSTRAINT fk_place_tag_place FOREIGN KEY (place_id) REFERENCES place (place_id),
    KEY idx_place_tag_tag (tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE place_image (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    place_id   BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT          NULL,
    PRIMARY KEY (id),
    -- 재수집 시 중복 방지(멱등성). 구 스키마에 없어 재수집마다 행이 늘던 문제를 여기서 막는다.
    CONSTRAINT uk_place_image UNIQUE (place_id, image_url),
    CONSTRAINT fk_place_image_place FOREIGN KEY (place_id) REFERENCES place (place_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- place_type = FESTIVAL인 장소와 1:1. 날짜는 언어 무관.
CREATE TABLE festival_detail (
    place_id   BIGINT NOT NULL,
    start_date DATE   NULL,
    end_date   DATE   NULL,
    PRIMARY KEY (place_id),
    CONSTRAINT fk_festival_detail_place FOREIGN KEY (place_id) REFERENCES place (place_id),
    KEY idx_festival_detail_dates (start_date, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- ─────────────────────────────────────────────────────────────
-- 운영
-- ─────────────────────────────────────────────────────────────

-- 관측성(NFR-10). lang별 기록이 부분 실패 처리와 연속 실패 감지의 근거다(아키텍처 §5.1) —
-- "실패해도 진행"이 습관이 되면 장기 장애를 놓친다.
CREATE TABLE data_sync_log (
    sync_id      BIGINT      NOT NULL AUTO_INCREMENT,
    phase        VARCHAR(20) NOT NULL,         -- COLLECT / RESOLVE / MERGE / TRANSLATE
    lang         VARCHAR(10) NULL,             -- COLLECT·TRANSLATE 단계의 대상 언어
    target       VARCHAR(50) NULL,             -- 서비스명·지역 등 세부 대상
    started_at   DATETIME(6) NULL,
    finished_at  DATETIME(6) NULL,
    status       VARCHAR(20) NOT NULL,         -- SUCCESS / FAIL / PARTIAL
    record_count INT         NULL,
    message      VARCHAR(500) NULL,
    PRIMARY KEY (sync_id),
    KEY idx_data_sync_log_phase_lang_started (phase, lang, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
