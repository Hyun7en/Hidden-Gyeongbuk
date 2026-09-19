# HANDOFF.md

세션을 넘길 때 **여기만 읽으면 되도록** 유지하는 작업 상태 정본.

**갱신 규칙** — 끝난 항목은 지우고 **누적하지 않는다**. **80줄을 넘기면 끝난 것부터 지운다**
(기록이 아니라 인수인계다). 검증하지 않은 것을 검증한 것처럼 적지 않는다.
결정의 근거는 `docs/adr/`, 설계는 Notion, 미해결 이슈는 Notion 이슈 로그 — **여기 옮겨 적지 않는다.**

**갱신** 2026-09-20 · **마감** 2026-09-21 (D-1) · 단독 개발

## 배포됨 — https://hiddengyeongbuk.com

P0~P4 ✅ · **P5 마감 제출만 남음**

place 1,302 · `place_image` 7,418 · `festival_detail` 38 · 영문 커버리지 100% ·
제목 localize 잔여 1,129건(미실행, **배포를 막지 않음**)

**운영 구성** — AWS Lightsail(서울, Ubuntu 22.04, RAM 2GB + 스왑 2GB, 정적 IP 13.124.54.154).
`~/app`에서 `docker compose`로 app + mysql. HTTPS는 **Cloudflare Tunnel**(`cloudflared`
systemd 서비스)이 종단하고 `localhost:8080`으로 넘긴다. 도메인은 Cloudflare Registrar.
**인바운드로 열린 포트는 SSH 22 하나** — 8080·3306은 루프백 바인딩. 자동 스냅샷 켜짐.

**표어** — "Gyeongbuk, Where Time Takes Its Time" / "시간도 자기 시간을 갖는 곳, 경북"
(홈 히어로 + `/about` 태그라인). 파비콘은 로고 마크로 교체됨.

**09-20 실도메인 검증 통과** — `prod` 프로파일 활성 / `/internal/sync/status` **404** /
`/api/bogus` 404 / `/`·`/privacy`·`/places/1` 200(SPA 딥링크) / `/api/meta/options`·`/api/places`
200 / 이미지 http 0·https 45 / 번들에 카카오 키 구워짐 / 웹폰트 `<link>` 2건 /
Flyway V8 validate 통과(덤프 이관 무결성 증거).

## 지금 이어서 할 것

1. **제출** — 브라우저 검증은 09-20에 끝났다(데스크톱·모바일 모두 지도·카카오 공유·영문 전환·
   코스 추천 정상). 남은 것은 제출뿐이다
2. **규정 확인** — 서비스 화면·카피에 "한국관광공사"/"KTO" 명칭·로고 금지(규정)
3. 선택 — 제목 localization 1,129건. **우선순위 최하.** 실패해도 구글 제목 유지라 손실 경로 없음

## 운영 메모

- 서버 접속 `ssh -i ~/.ssh/lightsail.pem ubuntu@13.124.54.154`
- 코드 갱신은 `git archive`로 tar 전송(저장소가 비공개라 서버에서 clone 불가) 후
  `docker compose up --build -d app`. **`VITE_KAKAO_MAP_KEY`를 바꾸면 재시작이 아니라 재빌드다**
- 운영 `.env`(chmod 600)의 진짜 비밀은 **DB 비밀번호뿐**. `TOURAPI_/TRANSLATE_/GEMINI_`는 빈 값
  (prod엔 쓰는 코드가 없다). DB 비밀번호는 로컬과 다른 새 값이다
- DB 백업 정본은 **덤프**다 — 로컬 `~/hg-dump.sql`, 서버 `~/hg-dump.sql`(09-19 시점).
  디스크 스냅샷은 crash-consistent라 덤프를 대신하지 못한다
- **`docker compose down -v`를 치지 않는다** — `mysql_data` 볼륨이 날아간다
- 로컬 `mysql` 컨테이너는 원본 데이터라 유지 중

## 절대 하지 말 것

- **`collect` 뒤에 `resolve` → `merge`를 건너뛰지 않는다.** 08-20에 7곳을 잃었다
- **번역 사용량을 로그로 판단하지 않는다** — 구글 콘솔이 정본
- **`SPRING_PROFILES_ACTIVE=prod`를 빠뜨리지 않는다.** `/internal/sync/status`가 404인지로 확인
- **운영 서버에 TourAPI·Translate·Gemini 키를 두지 않는다**

## 제목 localization이 막힌 이유 (2026-09-19 실측)

Gemini가 **503 UNAVAILABLE "high demand"**를 간헐적으로 돌려준다. 성공률 약 40~50%.
**쿼터가 아니다** — 쿼터면 429이고, 같은 키로 ListModels는 정상이다.

- ⚠ **`stoppedBy`의 "쿼터 소진 추정"을 믿지 말 것.** 실제 원인은 **로그를 봐야** 안다
- ⚠ **크기 때문도, 특정 모델 때문도 아니다.** 5건·15건 실패, 30건 성공, 50건 실패로 단조롭지
  않다. **표본 몇 개로 인과를 단정하지 말 것**(09-19에 두 번 틀렸다)
- 모델은 `GEMINI_MODEL`로 코드 변경 없이 바꾼다(`application.yml:153`). 일일 한도 20회/모델별
- 재시도가 3회·1초·2초뿐이라(`GeminiClient.java:34-35`) 짧다. `TranslateClient.java:35-36`은
  4회·3초·6초·12초 — **고칠 값어치는 있으나 마감 후에**

## 참고 경로 · 링크

- 행동 규칙 [`CLAUDE.md`](CLAUDE.md) · 결정 [`docs/adr/README.md`](docs/adr/README.md)
- 마이그레이션 현재 **V8** (새 번호는 `ls`로 확인) · 수집 `backend/.../ingest/`
- Deployment Guide — DevOps Engineer ▸ · Security Checklist —
  https://app.notion.com/p/3a518ff007d98125930dd9afde0cc04f
- 미해결 이슈 — QA Engineer ▸ [이슈 로그](https://app.notion.com/p/bdc1213313254e76a807f9997337d32f)
