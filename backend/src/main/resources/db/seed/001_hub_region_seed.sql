-- Hidden Gyeongbuk: hub 4개 + region 15곳 초기 데이터
-- 지역 코드 매핑표 v1.2 기준 (lDongRegnCd=47, signgu 3자리)
--
-- 좌표 출처 안내:
--   hub(안동역/동대구역/포항역)은 웹 검색으로 확인한 실측 좌표.
--   hub(북부내륙)은 대표 지점(상주시)의 시청 좌표로 근사.
--   region 15곳은 각 시군청 소재지 좌표(근사치, 위키백과/일반 지리 정보 기준).
--   region.lat/lng는 추천 점수 산정에는 쓰이지 않고(장소 단위 Haversine만 사용),
--   지역 목록 표시/참고용이라 이 정밀도로 충분함(ERD/DB 설계서 6장 참고).
--
-- 실행 방법: DBeaver에서 hidden_gyeongbuk 커넥션 열고 SQL 편집기에 붙여넣어 실행.
-- 이미 실행한 뒤 재실행하면 중복 INSERT 되므로, 재실행 전엔 TRUNCATE 필요.

INSERT INTO hub (name_ko, name_en, lat, lng, description) VALUES
('안동', 'Andong', 36.5750, 128.6747, '전통·자연·사찰'),
('동대구', 'Dongdaegu', 35.8794, 128.6284, '대구 진입 거점·환승'),
('포항', 'Pohang', 36.0716, 129.3419, '동해안·해양'),
('북부내륙', 'Bukbu Naeryuk', 36.4109, 128.1591, '내륙·산악');

INSERT INTO region (ldong_regn_cd, ldong_signgu_cd, name_ko, name_en, hub_id, lat, lng) VALUES
('47', '170', '안동', 'Andong',     (SELECT hub_id FROM hub WHERE name_ko = '안동'),     36.5684, 128.7294),
('47', '210', '영주', 'Yeongju',    (SELECT hub_id FROM hub WHERE name_ko = '안동'),     36.8056, 128.6239),
('47', '920', '봉화', 'Bonghwa',    (SELECT hub_id FROM hub WHERE name_ko = '안동'),     36.8931, 128.7326),
('47', '760', '영양', 'Yeongyang',  (SELECT hub_id FROM hub WHERE name_ko = '안동'),     36.6667, 129.1122),
('47', '750', '청송', 'Cheongsong', (SELECT hub_id FROM hub WHERE name_ko = '안동'),     36.4360, 129.0572),
('47', '730', '의성', 'Uiseong',    (SELECT hub_id FROM hub WHERE name_ko = '안동'),     36.3526, 128.6969),
('47', '820', '청도', 'Cheongdo',   (SELECT hub_id FROM hub WHERE name_ko = '동대구'),   35.6474, 128.7340),
('47', '230', '영천', 'Yeongcheon', (SELECT hub_id FROM hub WHERE name_ko = '동대구'),   35.9733, 128.9386),
('47', '840', '성주', 'Seongju',    (SELECT hub_id FROM hub WHERE name_ko = '동대구'),   35.9192, 128.2831),
('47', '830', '고령', 'Goryeong',   (SELECT hub_id FROM hub WHERE name_ko = '동대구'),   35.7263, 128.2626),
('47', '770', '영덕', 'Yeongdeok',  (SELECT hub_id FROM hub WHERE name_ko = '포항'),     36.4151, 129.3657),
('47', '930', '울진', 'Uljin',      (SELECT hub_id FROM hub WHERE name_ko = '포항'),     36.9930, 129.4004),
('47', '940', '울릉', 'Ulleung',    (SELECT hub_id FROM hub WHERE name_ko = '포항'),     37.4844, 130.9057),
('47', '280', '문경', 'Mungyeong',  (SELECT hub_id FROM hub WHERE name_ko = '북부내륙'), 36.5967, 128.1867),
('47', '250', '상주', 'Sangju',     (SELECT hub_id FROM hub WHERE name_ko = '북부내륙'), 36.4109, 128.1591);

-- 확인용
-- SELECT h.name_ko AS hub, r.name_ko AS region, r.ldong_regn_cd, r.ldong_signgu_cd
-- FROM region r JOIN hub h ON r.hub_id = h.hub_id
-- ORDER BY h.hub_id, r.region_id;
