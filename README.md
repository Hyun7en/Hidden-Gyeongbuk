# Hidden Gyeongbuk

외국인 관광객이 앱 설치 없이 경북 인구감소지역 15곳의 로컬 관광지를 탐색하고, 교통 거점(Hub) 기반 여행 코스를 추천받는 모바일 퍼스트 반응형 웹 서비스.

2026 관광데이터 활용 공모전 출품작 (마감 2026-09-21).

## 스택

- 백엔드: Spring Boot 3.5, Java 17, Gradle
- DB: MySQL 8.0 (Docker)
- 프론트: React SPA (Vite + TypeScript)
- 외부 API: 한국관광공사 TourAPI(KorService2/EngService2), Google Cloud Translation, Kakao Maps
  - Gemini는 **현재 미사용**이다. 번역 엔진이 전용 API로 교체됐고(ADR-013), 설정만 향후 LLM 기능용 예비로 남아 있다.

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
docker compose up -d mysql
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

## 개발 참고

- 데이터 수집은 배치(일 1회) 적재이며, 사용자 요청 시 TourAPI를 실시간 호출하지 않는다.
- 추천 점수는 캐시된 데이터 위에서 요청 시 실시간 계산한다.
- 시크릿은 `.env`로만 관리하며 커밋하지 않는다.
