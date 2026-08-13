-- ─────────────────────────────────────────────────────────────
-- V4 · 지역 소개 글 seed (15개 지역 × 한/영)
--
-- region_translation.description은 TourAPI가 주지 않는 콘텐츠라 수집으로 채울 수 없다.
-- 직접 작성해 seed로 넣는다(ERD §9, API 명세서 §2.5). 지역 소개 화면(S2)이 이 값에 의존한다.
--
-- 작성 기준: 주 사용자가 외국인이므로 "그 지역이 다른 곳과 무엇이 다른가"를 먼저 쓴다.
-- 홍보 문구가 아니라 판단 근거를 준다 — 화려한 수식 대신 실제로 확인 가능한 특징만 담았다.
-- 공모전 규정상 "한국관광공사"·"KTO" 명칭은 쓰지 않는다.
-- ─────────────────────────────────────────────────────────────

UPDATE region_translation rt
JOIN region r ON r.region_id = rt.region_id
SET rt.description = CASE WHEN rt.lang = 'ko' THEN CASE r.ldong_signgu_cd

WHEN '170' THEN '유교 문화가 가장 짙게 남은 도시입니다. 하회마을과 도산서원·병산서원이 유네스코 세계유산으로 등재돼 있고, 봉정사는 현존하는 가장 오래된 목조 건축물 중 하나를 품고 있습니다. 안동댐과 월영교는 해질 무렵이 가장 좋습니다.'
WHEN '210' THEN '소백산 남쪽 자락의 산과 절의 고장입니다. 부석사 무량수전은 한국 목조 건축의 정점으로 꼽히고, 소수서원은 조선 최초의 사액서원입니다. 두 곳 모두 유네스코 세계유산입니다.'
WHEN '920' THEN '경북에서 가장 산이 깊은 곳입니다. 청량산의 바위 봉우리와 백두대간 협곡을 따라 달리는 관광열차가 있고, 금강송 군락이 넓게 남아 있습니다. 사람보다 나무가 많은 지역입니다.'
WHEN '760' THEN '인구가 가장 적은 군 중 하나이자, 그래서 밤하늘이 가장 어두운 곳입니다. 국내 최초의 밤하늘 보호공원이 있어 맑은 날 은하수를 볼 수 있습니다. 두들마을에는 17세기 한글 조리서를 남긴 집안의 고택이 남아 있습니다.'
WHEN '750' THEN '주왕산의 기암 절벽과 계곡이 중심입니다. 지역 전체가 유네스코 세계지질공원으로 지정돼 있고, 탄산이 섞인 달기약수를 마셔볼 수 있습니다. 가을 단풍철이 가장 붐빕니다.'
WHEN '730' THEN '마늘로 알려졌지만 그보다 오래된 것이 있습니다. 삼국시대 이전 소국이던 조문국의 고분군이 들판에 남아 있고, 봄이면 산수유 마을이 노랗게 물듭니다. 빙계계곡은 한여름에도 찬 바람이 나옵니다.'
WHEN '820' THEN '소싸움과 감의 고장입니다. 폐터널을 개조한 와인터널에서 감으로 담근 와인을 맛볼 수 있고, 운문사는 비구니 승가대학이 있는 큰 사찰입니다. 대구에서 가장 가까운 인구감소지역이라 당일로도 다녀올 만합니다.'
WHEN '230' THEN '별을 보러 가는 곳입니다. 보현산 천문대가 국내 최대 광학망원경을 두고 있고, 은해사는 팔공산 동쪽 자락의 본사급 사찰입니다. 포도 산지라 와인을 만드는 농가도 있습니다.'
WHEN '840' THEN '참외 생산지로 널리 알려진 지역입니다. 세종대왕의 왕자들 태를 묻은 태실이 언덕 위에 모여 있는데, 이런 형태로 남은 곳은 드뭅니다. 가야산 자락이라 산세도 완만합니다.'
WHEN '830' THEN '대가야의 도읍이 있던 자리입니다. 지산동 고분군이 능선을 따라 늘어서 있고, 2023년 가야고분군의 일부로 유네스코 세계유산에 등재됐습니다. 신라·백제와 다른 갈래의 고대사를 볼 수 있는 곳입니다.'
WHEN '770' THEN '대게로 알려진 동해안 어항입니다. 강구항에서 배가 들어오는 것을 볼 수 있고, 해안을 따라 걷는 블루로드가 이어집니다. 능선의 풍력발전기가 바다와 함께 보이는 풍경이 이 지역의 인상을 만듭니다.'
WHEN '930' THEN '바다와 깊은 산을 하루에 다 볼 수 있는 지역입니다. 불영계곡과 금강송 숲이 내륙에 있고, 성류굴은 석회동굴로 연중 온도가 일정합니다. 온천이 두 곳 있어 겨울에도 찾는 사람이 있습니다.'
WHEN '940' THEN '배로만 갈 수 있는 화산섬입니다. 해안 일주도로가 절벽을 따라 이어지고, 섬 한가운데 나리분지는 화산 분화구가 평지가 된 드문 지형입니다. 독도로 가는 배도 이곳에서 출발합니다.'
WHEN '280' THEN '옛 사람들이 서울로 가던 고갯길이 남아 있습니다. 문경새재의 세 관문을 걸어서 넘을 수 있고, 폐선로를 달리는 철로자전거와 석탄박물관이 근대의 흔적을 보여줍니다. 오미자 산지이기도 합니다.'
WHEN '250' THEN '낙동강이 크게 굽어 도는 평야 지역입니다. 자전거 도로가 잘 닦여 있어 자전거의 도시로 불리고, 경천대에서 강줄기가 한눈에 들어옵니다. 가을에는 곶감을 말리는 풍경을 볼 수 있습니다.'

ELSE rt.description END

WHEN rt.lang = 'en' THEN CASE r.ldong_signgu_cd

WHEN '170' THEN 'The strongest surviving centre of Korean Confucian culture. Hahoe Village and the Dosan and Byeongsan academies are UNESCO World Heritage sites, and Bongjeongsa holds one of the oldest wooden buildings still standing in Korea. Andong Dam and Woryeong Bridge are best at dusk.'
WHEN '210' THEN 'Mountains and temples on the southern slopes of Sobaeksan. Buseoksa''s main hall is considered a high point of Korean wooden architecture, and Sosu Seowon was the first royally chartered Confucian academy. Both are UNESCO World Heritage sites.'
WHEN '920' THEN 'The deepest mountain country in Gyeongbuk. Rock peaks rise at Cheongnyangsan, a sightseeing train runs through the Baekdudaegan gorges, and wide stands of Korean red pine remain. There are more trees here than people.'
WHEN '760' THEN 'One of the least populated counties in the country — which is why its night sky is the darkest. Korea''s first designated night sky reserve is here, and on clear nights the Milky Way is visible. At Dudeul Village stands the house of the family that left Korea''s oldest cookbook written in Hangul.'
WHEN '750' THEN 'Built around the cliffs and ravines of Juwangsan. The whole county is a UNESCO Global Geopark, and you can drink Dalgi mineral water, naturally carbonated. Autumn foliage season is the busiest.'
WHEN '730' THEN 'Known for garlic, but something older sits here too. Burial mounds of Jomunguk, a statelet that predates the Three Kingdoms, stand in open fields, and in spring the cornelian cherry villages turn yellow. Binggye Valley stays cold even in midsummer.'
WHEN '820' THEN 'Bullfighting and persimmons. A disused railway tunnel now serves wine made from persimmons, and Unmunsa is a large temple with a college for Buddhist nuns. It is the closest of these regions to Daegu, making it workable as a day trip.'
WHEN '230' THEN 'A place people come to see stars. Bohyeonsan Observatory holds Korea''s largest optical telescope, and Eunhaesa is a head temple on the eastern slopes of Palgongsan. Grapes grow here, and some farms make wine.'
WHEN '840' THEN 'Widely known for its melons. On a hill sit the placenta chambers of King Sejong''s sons — few sites of this kind survive in this form. The land slopes gently, on the skirts of Gayasan.'
WHEN '830' THEN 'The former capital of Daegaya. Burial mounds run along the ridge at Jisan-dong, inscribed in 2023 as part of the Gaya Tumuli on the UNESCO World Heritage list. It offers a strand of ancient history separate from Silla and Baekje.'
WHEN '770' THEN 'An East Sea fishing town known for snow crab. You can watch the boats come in at Gangku Port, and the Blue Road trail follows the coast. Wind turbines along the ridge, seen against the sea, define the look of this place.'
WHEN '930' THEN 'A region where you can see the sea and deep mountains in one day. Bulyeong Valley and red pine forest lie inland, and Seongnyu Cave keeps a steady temperature year round. Two hot spring areas draw visitors even in winter.'
WHEN '940' THEN 'A volcanic island reachable only by boat. A coastal road runs along the cliffs, and Nari Basin at its centre is a rare flat plain formed inside a volcanic crater. Boats to Dokdo also depart from here.'
WHEN '280' THEN 'The old road to Seoul still runs through here. You can walk the three gates of Mungyeong Saejae, while a rail bike on disused track and a coal museum show the more recent past. It is also a growing area for omija berries.'
WHEN '250' THEN 'Plains where the Nakdong River makes a wide bend. Its bicycle paths have earned it the name "the bicycle city", and from Gyeongcheondae the river is visible in one sweep. In autumn you can see persimmons hung out to dry.'

ELSE rt.description END
ELSE rt.description END
WHERE r.ldong_regn_cd = '47';
