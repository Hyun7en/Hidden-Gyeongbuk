# Hidden Gyeongbuk

외국인 관광객이 앱 설치 없이 경북 인구감소지역 15곳의 로컬 관광지를 탐색하고, 교통 거점(Hub) 기반 여행 코스를 추천받는 모바일 퍼스트 반응형 웹 서비스.

2026 관광데이터 활용 공모전 출품작 (마감 2026-09-21).

## 스택

- 백엔드: Spring Boot 3.5, Java 17, Gradle
- DB: MySQL 8.0 (Docker)
- 프론트: React SPA (Vite + TypeScript)
- 외부 API: 한국관광공사 TourAPI(KorService2/EngService2), Google Cloud Translation, Kakao Maps
  - Gemini는 **영문 제목 localization에만** 쓴다(ADR-026). 설명글·주소 번역은 Google Cloud Translation이 맡는다(ADR-013).

## 저장소 구조

```
hidden-gyeongbuk/
├── backend/            # Spring Boot
├── frontend/           # React (Vite + TypeScript)
├── docker-compose.yml  # app + mysql
├── .env.example
└── README.md
```

## 시작하기

### 1. 환경 변수 설정

```bash
cp .env.example .env
# .env: TOURAPI_SERVICE_KEY, TRANSLATE_API_KEY
cp frontend/.env.example frontend/.env
# frontend/.env: VITE_KAKAO_MAP_KEY (카카오 "JavaScript 키" — REST 키를 쓰면 동작하지 않는다)
```

### 2. 개발 실행 (프론트는 Vite dev 서버)

```bash
sudo service docker start           # WSL은 도커 데몬을 수동 기동해야 한다
docker compose up -d mysql          # 저장소 루트에서
cd backend && ./gradlew bootRun     # :8080
cd frontend && npm run dev          # :5173, /api를 8080으로 프록시
```

브라우저는 반드시 `localhost:5173`으로 열 것 — 카카오 도메인 등록이 호스트 문자열
단위라 `127.0.0.1`에서는 지도가 뜨지 않는다.

### 3. 배포 실행 (단일 인스턴스가 API + 정적 번들을 함께 서빙)

React 번들은 백엔드 jar 안에 들어간다(ADR-002, 아키텍처 §8). 프론트를 따로 띄우지 않는다.

```bash
docker compose up --build -d        # :8080 하나로 화면과 API가 모두 뜬다
```

**`SPRING_PROFILES_ACTIVE=prod`가 반드시 들어가야 한다.** 미지정이면 local로 동작해
`/internal/*` 운영 엔드포인트가 인증 없이 열린다. compose가 기본값으로 넘기고 있으니
다른 방식으로 배포할 때만 직접 챙기면 된다.

Docker 없이 jar만 만들려면:

```bash
cd frontend && npm run build
cd ../backend && ./gradlew bootJar -PskipFrontendBuild
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/*.jar
```

배포 전 카카오 디벨로퍼스에 **배포 도메인**을 등록할 것 — 지도는 [플랫폼] > Web,
공유는 [제품 링크 관리] > [웹 도메인]으로 **목록이 서로 다르다**.

## 더 읽을 것

- [`docs/adr/`](docs/adr/) — 기술·설계 결정과 그 근거. [`README`](docs/adr/README.md)에 결정
  요약표와 대체 관계 사슬이 있다. **번호가 큰 쪽이 최신**이다.
- [`CLAUDE.md`](CLAUDE.md) — Claude Code 작업 규칙(패키지 구조, 코드 관행, 실측 함정).
- 설계 문서(SRS·아키텍처·ERD·API 명세·화면 설계 등)는 Notion "Hidden Gyeongbuk Wiki"가 정본이다.
