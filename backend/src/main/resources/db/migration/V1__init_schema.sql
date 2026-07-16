-- Hidden Gyeongbuk 초기 스키마
-- ERD/DB 설계서 v0.2 기준. 이 파일 이후로는 스키마 변경 시 새 버전 마이그레이션(V2, V3...)으로 추가하고,
-- 이미 배포된 버전 파일은 절대 수정하지 않는다 (Flyway는 체크섬으로 변경을 감지해 실패시킴).

CREATE TABLE hub (
    hub_id      BIGINT NOT NULL AUTO_INCREMENT,
    name_ko     VARCHAR(50) NOT NULL,
    name_en     VARCHAR(50),
    lat         DECIMAL(10,7),
    lng         DECIMAL(10,7),
    description VARCHAR(255),
    PRIMARY KEY (hub_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE region (
    region_id       BIGINT NOT NULL AUTO_INCREMENT,
    ldong_regn_cd   VARCHAR(2) NOT NULL,
    ldong_signgu_cd VARCHAR(3) NOT NULL,
    name_ko         VARCHAR(50) NOT NULL,
    name_en         VARCHAR(50),
    hub_id          BIGINT NOT NULL,
    lat             DECIMAL(10,7),
    lng             DECIMAL(10,7),
    PRIMARY KEY (region_id),
    CONSTRAINT uk_region_ldong_code UNIQUE (ldong_regn_cd, ldong_signgu_cd),
    CONSTRAINT fk_region_hub FOREIGN KEY (hub_id) REFERENCES hub (hub_id),
    KEY idx_region_hub (hub_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place (
    place_id        BIGINT NOT NULL AUTO_INCREMENT,
    content_id      VARCHAR(20) NOT NULL,
    content_type_id INT NOT NULL,
    category        ENUM('관광지','숙박','음식점','축제') NOT NULL,
    region_id       BIGINT NOT NULL,
    title_ko        VARCHAR(200) NOT NULL,
    title_en        VARCHAR(200),
    addr            VARCHAR(255),
    lat             DECIMAL(10,7),
    lng             DECIMAL(10,7),
    tel             VARCHAR(50),
    overview_ko     LONGTEXT,
    overview_en     LONGTEXT,
    first_image     VARCHAR(500),
    has_en          BIT(1) NOT NULL,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (place_id),
    CONSTRAINT uk_place_content_id UNIQUE (content_id),
    CONSTRAINT fk_place_region FOREIGN KEY (region_id) REFERENCES region (region_id),
    KEY idx_place_region_content_type (region_id, content_type_id),
    KEY idx_place_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place_tag (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    tag      VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_place_tag UNIQUE (place_id, tag),
    CONSTRAINT fk_place_tag_place FOREIGN KEY (place_id) REFERENCES place (place_id),
    KEY idx_place_tag_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place_image (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    place_id   BIGINT NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT,
    PRIMARY KEY (id),
    CONSTRAINT fk_place_image_place FOREIGN KEY (place_id) REFERENCES place (place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE festival_detail (
    place_id   BIGINT NOT NULL,
    start_date DATE,
    end_date   DATE,
    PRIMARY KEY (place_id),
    CONSTRAINT fk_festival_detail_place FOREIGN KEY (place_id) REFERENCES place (place_id),
    KEY idx_festival_detail_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place_summary (
    place_id     BIGINT NOT NULL,
    summary_en   LONGTEXT NOT NULL,
    source       ENUM('LLM','TOURAPI') NOT NULL,
    generated_at DATETIME(6),
    PRIMARY KEY (place_id),
    CONSTRAINT fk_place_summary_place FOREIGN KEY (place_id) REFERENCES place (place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE data_sync_log (
    sync_id       BIGINT NOT NULL AUTO_INCREMENT,
    target        VARCHAR(50) NOT NULL,
    started_at    DATETIME(6),
    finished_at   DATETIME(6),
    status        ENUM('FAIL','PARTIAL','SUCCESS') NOT NULL,
    record_count  INT,
    message       VARCHAR(500),
    PRIMARY KEY (sync_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
