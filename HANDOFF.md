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

`docker compose up --build`로 **실제 배포와 같은 경로**를 로컬에서 검증했다. 통과 항목:

- 카카오 키가 **실제 값으로** 번들에 구워짐 · 웹폰트 `<link>` 적용 · 빌드 CSS에 `@import` 0개
- `/internal/*` 3종 전부 **404** (prod 프로파일이 먹었다는 증거)
- SPA 폴백 정상(`/places/1`·`/privacy` 등 200/html), 확장자 경로는 404
- 이미지 URL **https 100%** (mixed content 없음) · 영문 응답에 한글 누수 없음
- 코스 추천 정상(살아있는 축제 기간과 겹치는 날짜로 3일 11곳 코스 2개)

**남은 건 실도메인이 있어야만 되는 것뿐이다** — HTTPS 인증서, 카카오 도메인 등록.

## 지금 이어서 할 것

1. **VM 생성 + 도메인** (사용자). 방화벽 **22/80/443만**, **3306은 절대 열지 않는다**
2. **카카오 디벨로퍼스에 배포 도메인 등록** — 목록이 **둘**이다:
   지도 [플랫폼]>Web, 공유 [제품 링크 관리]>[웹 도메인].
   ⚠ 로컬 리허설에서 지도를 눈으로 보려면 `localhost:8080`도 등록해야 한다(5173과 다른 호스트)
3. **S10 개인정보처리방침 4곳** — 3곳은 AWS 확정으로 채울 수 있다. 남은 1곳은
   **보호책임자 성명/직책**(사용자가 정해야 함). 이메일은 `Contact.tsx`의 주소에 맞춘다.
   위치 `i18n/ko.json:255,268,283,319` + `en.json` 동일. **ko/en을 쌍으로 고칠 것**.
   `effectiveDate`가 빈 문자열이면 화면에 "초안" 배지가 뜬다
4. **운영 서버 `.env`** — 카카오 키 + DB 비밀번호만. `TOURAPI_/TRANSLATE_/GEMINI_`는 **빈 값으로
   둔다**(prod엔 쓰는 코드가 없다). `chmod 600`. `SPRING_PROFILES_ACTIVE=prod` 필수
5. **DB 이관** — `mysqldump --single-transaction --default-character-set=utf8mb4`.
   `flyway_schema_history`를 **빼지 않는다**. **app을 내린 상태에서 복원하고 그다음 app을 올린다**
6. **제목 localization** — 아래 참조. 배포를 막지 않으므로 우선순위 최하
7. **지역 전용 사진 15장** — 보류 확정(사용자 결정 08-20). 임시방편 금지

## 제목 localization이 막힌 이유 (2026-09-18 실측)

`gemini-flash-latest`가 **503 UNAVAILABLE "This model is currently experiencing high demand"**
를 돌려준다. **쿼터가 아니다** — 쿼터면 429이고, 같은 키로 ListModels가 41개 모델을 정상 반환했다.

- ⚠ **`stoppedBy`의 "쿼터 소진 추정"을 믿지 말 것.** 코드가 3연속 실패를 보고 넘겨짚는 문구다.
  실제 원인은 **로그를 봐야** 알 수 있다
- 실측: `gemini-flash-latest` 503 · `gemini-3.7-flash` 503/파싱실패 · **`gemini-3.6-flash` 정상**
- 모델은 `GEMINI_MODEL` 환경변수로 **코드 변경 없이** 바꿀 수 있다(`application.yml:153`)
- **손실 경로가 없다** — 실패하면 기존 구글 제목을 유지한다(ADR-026). 표시도 안 남아 재실행이 이어받는다
- 사용자 결정(09-18): **503이 풀리기를 기다렸다 검증된 모델로 재시도**한다(3.6으로 강행하지 않음)

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
