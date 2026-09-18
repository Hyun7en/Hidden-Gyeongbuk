# HANDOFF.md

세션을 넘길 때 **여기만 읽으면 되도록** 유지하는 작업 상태 정본.

**갱신 규칙** — 끝난 항목은 지우고 **누적하지 않는다**. **80줄을 넘기면 끝난 것부터 지운다**
(기록이 아니라 인수인계다). 검증하지 않은 것을 검증한 것처럼 적지 않는다.
결정의 근거는 `docs/adr/`, 설계는 Notion, 미해결 이슈는 Notion 이슈 로그 — **여기 옮겨 적지 않는다.**

**갱신** 2026-09-19 · **마감** 2026-09-21 (D-2) · 단독 개발

## 확정된 인프라 (2026-09-19 사용자 결정)

- **서버** AWS Lightsail · 서울(ap-northeast-2) · **RAM 2GB 이상**
  (1GB는 컨테이너 안에서 `npm ci`+Gradle을 돌리다 OOM 난다)
- **도메인** Cloudflare Registrar. **프록시를 켜면 서버에서 인증서 작업이 필요 없다**
- S10 호스팅 칸 문구: `Amazon Web Services (AWS) — 서버 인프라 제공`

## 현재 수치 (2026-09-19 실측)

| 항목 | 값 |
|---|---|
| place | 1,302 · `place_image` **7,418** · `festival_detail` **38** |
| 영문 커버리지 | **1,302 / 1,302 = 100%** |
| 축제 중 오늘 이후 유효 | 13건 (2026년 31건, 2025년 잔재 7건) |
| 제목 localize 잔여 | **1,129건** (미실행) |

P0 ✅ · P1 데이터 ✅(제목 제외) · P2 백엔드 ✅ · P3 프론트 ✅ · P4 배포 🔄 · P5 마감 ⬜

## 로컬 prod 리허설 — 통과 (2026-09-19)

`docker compose up --build`로 실제 배포와 같은 경로를 검증했다. 카카오 키 번들 반영,
`/internal/*` 404, SPA 폴백, 웹폰트 `<link>`, CSS `@import` 0, 이미지 https 100%,
코스 추천까지 전부 통과. **남은 건 실도메인이 있어야만 되는 것뿐이다**(인증서·카카오 등록).

## 지금 이어서 할 것

**병목은 하나다 — 서버와 도메인이 아직 없다.** 그전까지 진행할 수 있는 항목이 거의 없다.
상세 절차는 `~/.claude/plans/nifty-drifting-crescent.md`.

1. **AWS Lightsail 인스턴스** (사용자) — 서울, **RAM 2GB 이상**(1GB는 이미지 빌드 중 OOM).
   Docker + compose 설치. **3306은 어떤 경우에도 열지 않는다**
2. **Cloudflare Registrar 도메인 등록** (사용자) → 네임서버 Cloudflare, VM 연결
3. **HTTPS 경로 선택** — ① **Cloudflare Tunnel**(포트 0개·인증서 작업 없음, 권장)
   또는 ② Caddy + Let's Encrypt(80/443 개방)
4. **카카오 디벨로퍼스에 배포 도메인 등록** — 목록이 **둘**이고 서로 다르다:
   지도 [플랫폼]>Web, 공유 [제품 링크 관리]>[웹 도메인]. **도메인 확정 후에 할 것**
5. **운영 서버 `.env`** — 카카오 키 + DB 비밀번호만. `TOURAPI_/TRANSLATE_/GEMINI_`는 **빈 값으로
   둔다**(prod엔 쓰는 코드가 없다). `chmod 600`. `SPRING_PROFILES_ACTIVE=prod` 필수
6. **DB 이관** — `mysqldump --single-transaction --default-character-set=utf8mb4`.
   `flyway_schema_history`를 **빼지 않는다**. **app을 내린 상태에서 복원하고 그다음 app을 올린다**
7. **실서버 스모크** — 로컬에서 재현 안 되는 두 가지가 핵심:
   `/internal/sync/status`가 **404**인가, **지도가 실제로 뜨는가**
8. **제목 localization** — 아래 참조. **배포를 막지 않는다.** 우선순위 최하
9. **지역 전용 사진 15장** — 보류 확정(사용자 결정 08-20). 임시방편 금지

## 로컬 상태 (2026-09-19 세션 종료 시점)

- `bootRun` 종료됨, 8080 비어 있음. `app` 컨테이너 정지, **`mysql` 컨테이너는 실행 중**
  (데이터가 여기 있다 — 내리지 말 것)
- 루트 `.env`에 `VITE_KAKAO_MAP_KEY` 추가됨(`chmod 600`). compose가 `frontend/.env`를 읽지
  않아 필요하다 — 없으면 배포본 지도가 죽는다
- **커밋 4개가 push 안 된 상태다** (`b53d9b9`·`0201b09`·`a2c39a6`·`dc5453d`)

## 제목 localization이 막힌 이유 (2026-09-19 실측)

Gemini가 **503 UNAVAILABLE "high demand"**를 간헐적으로 돌려준다. 성공률 약 40~50%.
**쿼터가 아니다** — 쿼터면 429이고, 같은 키로 ListModels는 정상이다.

- ⚠ **`stoppedBy`의 "쿼터 소진 추정"을 믿지 말 것.** 3연속 실패를 보고 넘겨짚는 문구다 —
  실제 원인은 **로그를 봐야** 안다
- ⚠ **크기 때문도, 특정 모델 때문도 아니다.** 5건·15건 실패, 30건 성공, 50건 실패로 단조롭지
  않고 같은 모델이 번갈아 되고 안 된다. **표본 몇 개로 인과를 단정하지 말 것**(09-19에 두 번 틀렸다)
- 모델은 `GEMINI_MODEL`로 코드 변경 없이 바꾼다(`application.yml:153`). `3.5`·`3.6`이 상대적으로
  잘 받고 `latest`·`3.8`은 타임아웃이 잦다 — 어디까지나 경향이다
- 일일 한도 **20회 / 모델별**(`PerProjectPerModel`). 12회 성공이 필요한데 성공률이 절반이라
  **한 모델로는 하루에 못 끝낸다**
- 재시도가 3회·1초·2초뿐이라(`GeminiClient.java:34-35`) 이 환경엔 짧다.
  `TranslateClient.java:35-36`은 4회·3초·6초·12초다 — **고칠 값어치는 있으나 마감 후에**
- **손실 경로가 없다** — 실패하면 기존 구글 제목을 유지한다(ADR-026). 표시도 안 남아 재실행이 이어받는다

## 절대 하지 말 것

- **`collect` 뒤에 `resolve` → `merge`를 건너뛰지 않는다.** 08-20에 7곳을 잃었다
- **번역 사용량을 로그로 판단하지 않는다** — 구글 콘솔이 정본
- **`SPRING_PROFILES_ACTIVE=prod`를 빠뜨리지 않는다.** 배포 후 **`/internal/sync/status`가
  404인지로 확인**한다
- **운영 서버에 TourAPI·Translate·Gemini 키를 두지 않는다**

## 참고 경로 · 링크

- 행동 규칙 [`CLAUDE.md`](CLAUDE.md) · 결정 [`docs/adr/README.md`](docs/adr/README.md)
- 마이그레이션 현재 **V8** (새 번호는 `ls`로 확인) · 수집 `backend/.../ingest/`
- Deployment Guide — DevOps Engineer ▸ · Security Checklist —
  https://app.notion.com/p/3a518ff007d98125930dd9afde0cc04f
- 미해결 이슈 — QA Engineer ▸ [이슈 로그](https://app.notion.com/p/bdc1213313254e76a807f9997337d32f)
