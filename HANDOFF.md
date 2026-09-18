# HANDOFF.md

세션을 넘길 때 **여기만 읽으면 되도록** 유지하는 작업 상태 정본.

**갱신 규칙** — 끝난 항목은 지우고 **누적하지 않는다**. **80줄을 넘기면 끝난 것부터 지운다**
(기록이 아니라 인수인계다). 검증하지 않은 것을 검증한 것처럼 적지 않는다.
결정의 근거는 `docs/adr/`, 설계는 Notion, 미해결 이슈는 Notion 이슈 로그 — **여기 옮겨 적지 않는다.**

**갱신** 2026-09-18 · **마감** 2026-09-21 (D-3, 오늘 포함 4일) · 단독 개발

## 상태

**08-21 이후 약 한 달간 진행이 없었다** — 마지막 커밋은 `76d5509`(08-21). 아래 수치는 전부
**2026-08-20 실측이고 그 뒤로 재측정하지 않았다.** 이어서 할 때 `limit=0` dry-run으로 먼저 실측할 것.

P0 ✅ · P1 데이터 🔄 · P2 백엔드 ✅ · P3 프론트 ✅ · P4 배포 🔄(**실서버 미배포**) · P5 마감 ⬜

| 항목 | 값 (08-20) |
|---|---|
| place | 1,302 |
| `place_image` | 5,811 · `festival_detail` 33 |
| 영문 커버리지 | 1,278 / 1,302 = 98.2% |

## 이번 세션(09-18)에서 한 일

- 4일 배포 계획 수립. 탐색으로 확정한 사실은 `~/.claude/plans/nifty-drifting-crescent.md`
- **배포를 조용히 깨뜨릴 결함을 찾아 고쳤다**: `docker-compose.yml`이 `VITE_KAKAO_MAP_KEY`를
  루트 `.env`에서 읽는데 그 키가 루트에 없어(실제 키는 `frontend/.env`) 빈 키가 번들에 구워진다
  → `.env.example`에 항목·경고 추가. 로컬에서는 끝까지 안 드러나는 종류다
- `docker-compose.yml` MySQL 포트를 `127.0.0.1`에만 바인딩(공인 IP 서버에서 3306 노출 차단)
- `.dockerignore` 신설 — `frontend/.env`가 이미지 빌드 단계로 들어가는 것 차단 + 컨텍스트 축소

## 지금 이어서 할 것 (순서가 강제된다)

**전제**: `/internal/*` 3개 컨트롤러는 전부 `@Profile("local")`이라 **prod엔 존재하지 않는다.**
데이터 작업은 로컬에서 끝내고 운영에는 **덤프로만** 옮긴다.

1. **잔량 실측** — `limit=0`은 외부 호출 0의 dry-run이다(`TitleLocalizer.java:86`,
   `Translator.java:133`). `GET /internal/sync/status`도 무료.
   ⚠ `titles/review?limit=0`은 `data/title-review-en.json`을 덮어쓰니 실측용으로 쓰지 말 것
2. **국문 사진 302곳** — `POST /internal/sync/collect?lang=ko` → **`resolve` → `merge`**.
   ⚠ 뒤 둘을 건너뛰면 PENDING 행의 place가 지워진다(08-20에 7곳 손실, 복구함)
3. **번역 백필 잔여** — `POST /internal/translations?lang=en&limit=100`, `remaining` 0까지 반복.
   9월이라 Google 월 한도 50만 자가 리셋됐다 — 8월의 잔여 58,000자 제약은 없다
4. **제목 localization (ADR-026)** — 반드시 3번 다음(입력이 `source=TRANSLATE` 행이다).
   `POST /internal/titles/localize?lang=en&limit=2000`. `batch-size`는 HTTP 인자가 아니라
   `application.yml:122`의 설정이고 기본 100이라 1,105건 ≈ 12호출.
   ⚠ Gemini 무료 티어는 **하루 20호출** — localize와 review를 같은 날 못 돌린다
5. **VM 프로비저닝** — x86_64 · RAM 2GB↑(컨테이너 안에서 `npm ci`+Gradle을 돌린다).
   방화벽 22/80/443만. **무료 서브도메인 필요** — raw IP는 Let's Encrypt 인증서를 못 받고
   카카오 콘솔이 IP 등록을 안 받아 지도·공유가 둘 다 죽는다
6. **S10 개인정보처리방침** — 원문 미작성이 아니다. 국·영문 초안이 다 있고 **사람이 정할 4곳**만
   남았다(`i18n/ko.json:255,268,283,319` + `en.json` 동일 위치). 3곳이 호스팅 결정에 물려 있다.
   `effectiveDate`가 빈 문자열이라 화면에 "초안" 배지가 뜬다. **ko/en을 쌍으로 고칠 것**
7. **운영 DB 이관** — 반드시 덤프·복원(재수집은 TourAPI 며칠 + 번역 40만 자).
   `flyway_schema_history`를 **빼지 않는다**(`ddl-auto: validate`라 기동이 깨진다).
   **app을 내린 상태에서 복원하고 그다음 app을 올린다** — 순서가 바뀌면 Flyway 히스토리가 꼬인다
8. **카카오 배포 도메인 등록** — 목록이 둘이다: 지도 [플랫폼]>Web, 공유 [제품 링크 관리]>[웹 도메인]
9. **지역 전용 사진 15장 (D안)** — 홈 지역 카드가 그 지역 장소 사진을 빌려 쓴다. 결과가 나쁘지만
   **이대로 배포한다**(사용자 결정 08-20). ⚠ 정렬 기준 변경 같은 **임시방편을 쌓지 말 것**

## 절대 하지 말 것

- **`collect` 뒤에 `resolve` → `merge`를 건너뛰지 않는다**(위 2번). 08-20에 7곳을 잃었다
- **번역 사용량을 로그로 판단하지 않는다** — 구글 콘솔이 정본(로그 30,697 vs 콘솔 46,694자)
- **Gemini를 문자 수로 계산하지 않는다** — 하루 **20호출**이 한도다
- **운영 서버에 TourAPI·Translate·Gemini 키를 두지 않는다** — prod엔 쓰는 코드가 없다
  (`SYNC_ENABLED` 기본 false, 관리 엔드포인트는 local 전용)
- **`SPRING_PROFILES_ACTIVE=prod`를 빠뜨리지 않는다** — 미지정이면 local이라 `/internal/*`이
  인증 없이 열린다. 배포 후 **`/internal/sync/status`가 404인지로 확인**한다

## 남은 결정

- WBS 일정 재조정, ADR-025 후속(ERD §4.1·추천 §5의 옛 결정 잔존 여부) — **마감 후로 넘긴다**

## 참고 경로 · 링크

- 행동 규칙 [`CLAUDE.md`](CLAUDE.md) · 결정 [`docs/adr/README.md`](docs/adr/README.md) ·
  이 파일 주입 훅 `.claude/hooks/session-start-handoff.sh`
- 수집 `backend/src/main/java/com/hiddengyeongbuk/backend/ingest/` ·
  마이그레이션 `backend/src/main/resources/db/migration/` (현재 V8, **새 번호는 `ls`로 확인**)
- Deployment Guide — DevOps Engineer ▸ (2026-08-21 작성)
- Security Checklist — https://app.notion.com/p/3a518ff007d98125930dd9afde0cc04f
- 미해결 이슈 — QA Engineer ▸ [이슈 로그](https://app.notion.com/p/bdc1213313254e76a807f9997337d32f)
- 개선 기록(트러블슈팅) — https://app.notion.com/p/3c018ff007d9813f9d0ddf67786db8d2
