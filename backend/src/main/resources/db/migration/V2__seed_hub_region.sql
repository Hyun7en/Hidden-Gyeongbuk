-- hub 4개 + region 15곳 초기 데이터 (지역 코드 매핑표 v1.2, lDongRegnCd=47 · signgu 3자리)
--
-- 좌표 출처: hub(안동역/동대구역/포항역)는 실측 좌표, hub(북부내륙)은 대표 지점(상주시) 좌표로 근사.
-- region 15곳은 각 시군청 소재지 좌표(근사치). region.lat/lng는 지역 목록 표시용이며
-- 추천 점수 산정에는 장소(place) 단위 좌표만 쓰인다.
--
-- 표시명은 hub/region이 아니라 번역 테이블에 들어간다 — 국문도 예외가 아니다(ERD §1.2).
-- 로마자 표기는 국립국어원 표기법.

INSERT INTO hub (code, lat, lng) VALUES
('ANDONG',       36.5750, 128.6747),
('DONGDAEGU',    35.8794, 128.6284),
('POHANG',       36.0716, 129.3419),
('NORTH_INLAND', 36.4109, 128.1591);

-- 영문 성격 설명은 국문 라벨의 직역이다(신규 콘텐츠 아님) — 카피 검토 시 다듬을 것.
INSERT INTO hub_translation (hub_id, lang, name, description) VALUES
((SELECT hub_id FROM hub WHERE code = 'ANDONG'),       'ko', '안동',       '전통·자연·사찰'),
((SELECT hub_id FROM hub WHERE code = 'ANDONG'),       'en', 'Andong',     'Tradition · Nature · Temples'),
((SELECT hub_id FROM hub WHERE code = 'DONGDAEGU'),    'ko', '동대구',     '대구 진입 거점·환승'),
((SELECT hub_id FROM hub WHERE code = 'DONGDAEGU'),    'en', 'Dongdaegu',  'Daegu Gateway · Transit'),
((SELECT hub_id FROM hub WHERE code = 'POHANG'),       'ko', '포항',       '동해안·해양'),
((SELECT hub_id FROM hub WHERE code = 'POHANG'),       'en', 'Pohang',     'East Coast · Sea'),
((SELECT hub_id FROM hub WHERE code = 'NORTH_INLAND'), 'ko', '북부내륙',   '내륙·산악'),
((SELECT hub_id FROM hub WHERE code = 'NORTH_INLAND'), 'en', 'Bukbu Naeryuk', 'Inland · Mountains');

INSERT INTO region (ldong_regn_cd, ldong_signgu_cd, hub_id, lat, lng) VALUES
('47', '170', (SELECT hub_id FROM hub WHERE code = 'ANDONG'),       36.5684, 128.7294),
('47', '210', (SELECT hub_id FROM hub WHERE code = 'ANDONG'),       36.8056, 128.6239),
('47', '920', (SELECT hub_id FROM hub WHERE code = 'ANDONG'),       36.8931, 128.7326),
('47', '760', (SELECT hub_id FROM hub WHERE code = 'ANDONG'),       36.6667, 129.1122),
('47', '750', (SELECT hub_id FROM hub WHERE code = 'ANDONG'),       36.4360, 129.0572),
('47', '730', (SELECT hub_id FROM hub WHERE code = 'ANDONG'),       36.3526, 128.6969),
('47', '820', (SELECT hub_id FROM hub WHERE code = 'DONGDAEGU'),    35.6474, 128.7340),
('47', '230', (SELECT hub_id FROM hub WHERE code = 'DONGDAEGU'),    35.9733, 128.9386),
('47', '840', (SELECT hub_id FROM hub WHERE code = 'DONGDAEGU'),    35.9192, 128.2831),
('47', '830', (SELECT hub_id FROM hub WHERE code = 'DONGDAEGU'),    35.7263, 128.2626),
('47', '770', (SELECT hub_id FROM hub WHERE code = 'POHANG'),       36.4151, 129.3657),
('47', '930', (SELECT hub_id FROM hub WHERE code = 'POHANG'),       36.9930, 129.4004),
('47', '940', (SELECT hub_id FROM hub WHERE code = 'POHANG'),       37.4844, 130.9057),
('47', '280', (SELECT hub_id FROM hub WHERE code = 'NORTH_INLAND'), 36.5967, 128.1867),
('47', '250', (SELECT hub_id FROM hub WHERE code = 'NORTH_INLAND'), 36.4109, 128.1591);

-- description(지역 소개 글)은 여기서 채우지 않는다 — TourAPI가 주지 않는 콘텐츠라 15개 지역 ×
-- 한/영을 직접 작성해야 하고, 이는 별도 콘텐츠 작업이다(ERD §9). S2 지역 소개 화면은 그때까지 미완.
INSERT INTO region_translation (region_id, lang, name) VALUES
((SELECT region_id FROM region WHERE ldong_signgu_cd = '170'), 'ko', '안동'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '170'), 'en', 'Andong'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '210'), 'ko', '영주'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '210'), 'en', 'Yeongju'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '920'), 'ko', '봉화'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '920'), 'en', 'Bonghwa'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '760'), 'ko', '영양'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '760'), 'en', 'Yeongyang'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '750'), 'ko', '청송'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '750'), 'en', 'Cheongsong'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '730'), 'ko', '의성'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '730'), 'en', 'Uiseong'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '820'), 'ko', '청도'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '820'), 'en', 'Cheongdo'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '230'), 'ko', '영천'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '230'), 'en', 'Yeongcheon'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '840'), 'ko', '성주'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '840'), 'en', 'Seongju'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '830'), 'ko', '고령'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '830'), 'en', 'Goryeong'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '770'), 'ko', '영덕'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '770'), 'en', 'Yeongdeok'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '930'), 'ko', '울진'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '930'), 'en', 'Uljin'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '940'), 'ko', '울릉'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '940'), 'en', 'Ulleung'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '280'), 'ko', '문경'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '280'), 'en', 'Mungyeong'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '250'), 'ko', '상주'),
((SELECT region_id FROM region WHERE ldong_signgu_cd = '250'), 'en', 'Sangju');
