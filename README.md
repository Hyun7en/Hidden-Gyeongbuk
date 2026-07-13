# Hidden Gyeongbuk

외국인 관광객이 앱 설치 없이 경북 인구감소지역 15곳의 로컬 관광지를 탐색하고, 교통 거점(Hub) 기반 여행 코스를 추천받는 모바일 퍼스트 반응형 웹 서비스.

2026 관광데이터 활용 공모전 출품작 (마감 2026-09-21).

## 스택

- 백엔드: Spring Boot 3.5, Java 17, Gradle
- DB: MySQL 8.0 (Docker)
- 프론트: React SPA (Vite + TypeScript)
- 외부 API: 한국관광공사 TourAPI(KorService2/EngService2), Kakao Maps, Gemini Flash

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
# .env를 열어 TOURAPI_SERVICE_KEY, GEMINI_API_KEY 등을 채운다
```

### 2. Docker Compose로 실행 (app + mysql)

```bash
docker compose up --build
```

### 3. 백엔드만 로컬 실행 (MySQL은 Docker로)

```bash
docker compose up -d mysql
cd backend
./gradlew bootRun
```

## 개발 참고

- 데이터 수집은 배치(일 1회) 적재이며, 사용자 요청 시 TourAPI를 실시간 호출하지 않는다.
- 추천 점수는 캐시된 데이터 위에서 요청 시 실시간 계산한다.
- 시크릿은 `.env`로만 관리하며 커밋하지 않는다.
