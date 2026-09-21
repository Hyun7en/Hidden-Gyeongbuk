# HANDOFF.md

세션을 넘길 때 **여기만 읽으면 되도록** 유지하는 작업 상태 정본.

**갱신 규칙** — 끝난 항목은 지우고 **누적하지 않는다**. **80줄을 넘기면 끝난 것부터 지운다**
(기록이 아니라 인수인계다). 검증하지 않은 것을 검증한 것처럼 적지 않는다.
결정의 근거는 `docs/adr/`, 설계는 Notion, 미해결 이슈는 Notion 이슈 로그 — **여기 옮겨 적지 않는다.**

**갱신** 2026-09-21 · **마감 2026-09-21(월) 16:00 정각** · 단독 개발

## 배포됨 — https://hiddengyeongbuk.com

P0~P4 ✅ · **P5 마감 제출만 남음**

place 1,302 · `place_image` 7,418 · `festival_detail` 38 · 영문 커버리지 100% ·
제목 localize 잔여 1,129건(미실행, **배포를 막지 않음**)

**운영 구성** — AWS Lightsail(서울, Ubuntu 22.04, RAM 2GB + 스왑 2GB, 정적 IP 13.124.54.154).
`~/app`에서 `docker compose`로 app + mysql. HTTPS는 **Cloudflare Tunnel**(`cloudflared`
systemd 서비스)이 종단하고 `localhost:8080`으로 넘긴다. 도메인은 Cloudflare Registrar.
**인바운드로 열린 포트는 SSH 22 하나** — 8080·3306은 루프백 바인딩. 자동 스냅샷 켜짐.

**표어** — "Gyeongbuk, Where Time Takes Its Time" / "시간이 잠시 머무는 곳, 경북"
(홈 히어로 + `/about` 태그라인). 파비콘은 로고 마크로 교체됨.
**09-21 재배포·검증 완료(2회)** — ① 한글 표어 교체, 약관 요약 문구 방향 수정, 사진 없는
카드의 빈 상태와 이미지 로딩 실패 폴백. ② `201d7da` 테마 Provider 수정 — 토글로 다크↔라이트를
바꾸면 로고 워드마크가 배경에 묻히던 버그(`useTheme`가 Provider 없는 훅이라 Header와 Logo가
각자 state를 가졌다). **미배포 커밋 없음.**
검증 — 서빙 번들이 로컬 빌드와 바이트 단위 동일(`index-iuREGwEr.js`) / `/`·`/places/1`·
`/privacy`·`/terms`·`/api/meta/options` 200 / `/internal/sync/status` 404 / 다크 토글 육안 확인.

## 지금 이어서 할 것

개발·배포·검증은 끝났다. **남은 것은 제출 자료뿐이다.**

**배포 절차** — `git archive --format=tar HEAD | ssh … 'tar -x -C ~/app'` →
`docker compose up --build -d app`. 기동에 1~2분 걸려 그동안 502가 난다(장애 아님).
**운영 서버 ssh는 Claude가 실행할 수 없다 — 사용자가 직접 친다.**

1. **기능설명서 PDF 변환 후 제출** — 이 환경엔 변환 도구가 없다. 파워포인트나 구글 슬라이드로 내보낼 것.
   **지정 양식 미사용·항목 누락은 심사 제외 사유다**.
   이미지는 삽입 완료 — `C:\Users\rhqud\Downloads\기능설명서_Hidden Gyeongbuk_이미지삽입.pptx`
   (4·6·10~13장, 그림 19장). **원본 pptx는 그대로 두었다.** 이 환경엔 렌더러가 없어
   **시각 확인은 안 했다** — 열어서 표 행 높이에 밀린 그림이 없는지 볼 것
2. **제출 폼 입력** — 문구는 `C:\Users\rhqud\Downloads\제출폼_문구.txt`에 저장해 뒀다
3. 선택 — 제목 localization 1,129건. **우선순위 최하.** 실패해도 구글 제목 유지라 손실 경로 없음

**주최측 공지에서 확인한 것(09-20)** — 제출처는 한국관광 콘텐츠랩(참가 계정 로그인+이메일 인증).
**운영계정 신청은 선택 사항**이고 제출은 인증키(인코딩·디코딩)로 한다. **지역 특화 서비스는 가점
2점** 대상이라 경북 한정임을 드러낼 것. 배포 번들에 "한국관광공사"·"KTO" 표기 0건으로 규정 통과.

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
