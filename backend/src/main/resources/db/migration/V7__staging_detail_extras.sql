-- 이미지 목록(detailImage2)과 축제 기간(detailIntro2)을 staging에 보존한다.
--
-- 외부 API 연동 설계서 §2.3이 detailImage2 → place_image, 축제 날짜 → festival_detail로
-- 규정해 두었으나 호출부도 DTO 필드도 없어 두 테이블이 계속 0행이었다. 그 결과:
--   · 장소 상세의 사진이 목록이 준 first_image 1장뿐이었다
--   · 축제 날짜 겹침이 한 번도 동작한 적이 없다 — 추천 알고리즘 §4.7이 규정한 유일한 계절
--     반영 근거인데, EV 32곳 전부 날짜가 없어 판정 자체가 돌지 않았다
--
-- 원본을 staging에 두고 place_image·festival_detail은 Merge가 만든다. "수집과 해석의 분리"
-- 원칙 그대로이며(ADR-016), 해석이 바뀌어도 재수집 없이 Merge만 다시 돌리면 된다.

ALTER TABLE staging_place
    -- 줄바꿈으로 구분한 원본 이미지 URL 목록(순서 = 소스가 준 순서).
    --
    -- 자식 테이블로 정규화하지 않는 이유: staging은 "원본 그대로"를 담는 계층이고 아무도
    -- 조인해 읽지 않는다. 정규화 결과는 place_image가 이미 갖고 있으므로 같은 구조를 두 번
    -- 만들 이유가 없다. 구분자가 줄바꿈인 것은 URL에 절대 나타나지 않는 문자라서다.
    ADD COLUMN detail_images TEXT NULL COMMENT 'detailImage2 원본 URL 목록(줄바꿈 구분)' AFTER first_image,

    -- 이미지를 "물어봤다"는 표시. 받은 개수와 무관하게 응답이 오면 찍는다.
    --
    -- 이게 없으면 이미지가 없는 장소를 매 수집마다 영원히 다시 묻는다 — overview에서 이미
    -- 겪은 함정이다(응답은 왔는데 비어 있는 것과 응답을 못 받은 것은 다르다). 원본이 바뀌면
    -- (modifiedtime 변경) StagingWriter가 이 값을 NULL로 되돌려 다시 묻게 한다.
    --
    -- 기존 행은 전부 NULL이라 "아직 안 물어봤다"로 읽힌다 — 그게 맞다. 다음 수집이 채운다.
    ADD COLUMN image_fetched_at DATETIME NULL COMMENT 'detailImage2 응답을 받은 시각(개수 무관)' AFTER detail_images,

    -- 축제 기간. 언어 무관한 값이라 어느 언어 행에서 와도 같아야 한다.
    ADD COLUMN event_start_date DATE NULL COMMENT 'detailIntro2 eventstartdate' AFTER image_fetched_at,
    ADD COLUMN event_end_date DATE NULL COMMENT 'detailIntro2 eventenddate' AFTER event_start_date,

    -- 축제 기간을 물어봤다는 표시. image_fetched_at과 같은 이유로 따로 둔다 —
    -- 두 오퍼레이션은 서로 다른 호출 예산을 쓰고 독립적으로 실패한다.
    ADD COLUMN intro_fetched_at DATETIME NULL COMMENT 'detailIntro2 응답을 받은 시각(값 유무 무관)' AFTER event_end_date;
