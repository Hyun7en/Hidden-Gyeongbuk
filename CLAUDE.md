# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

2026 관광데이터 활용 공모전 출품작(마감 2026-09-21). 외국인 관광객에게 경북 인구감소지역 15곳의 관광지 탐색과 Hub 기반 코스 추천을 제공하는 웹 서비스. Spring Boot 3.5(Java 17) + React(Vite·TS) 모노리포, MySQL.

설계 문서(SRS, ADR, ERD, 외부 API 연동, 추천 알고리즘, API 명세)와 WBS는 Notion "Hidden Gyeongbuk" 페이지가 원본이다. 설계 결정을 바꾸면 해당 Notion 문서도 갱신할 것.

## 명령어

```bash
# 백엔드 (backend/ 에서)
./gradlew test                                  # 단위·슬라이스 테스트 (*IT 제외, DB 불필요)
./gradlew test --tests "*CourseScorerTest"      # 단일 테스트 클래스
./gradlew integrationTest                       # *IT 통합 테스트 — 실제 MySQL(+일부는 TourAPI) 필요
./gradlew bootRun                               # 서버 실행 (MySQL 필요)

# MySQL (루트에서; WSL이라 systemd 없음 — 도커 데몬 수동 기동)
sudo service docker start
docker compose up -d mysql

# 프론트 (frontend/ 에서)
npm run dev        # Vite dev 서버 (localhost:5173)
npm run lint       # oxlint
npx tsc --noEmit   # 타입체크만
npm run build      # tsc -b + vite build
```

- 루트 `.env`는 `backend/build.gradle`이 bootRun/test/integrationTest에 자동 주입한다(셸 export 값이 우선). 수동 source 불필요.
- **DB·외부 API가 필요한 실행(integrationTest, bootRun, 수집/백필 트리거)은 직접 실행하지 말고 명령어를 사용자에게 제시할 것.** TourAPI는 개발계정 일 1,000건 한도라 호출 낭비가 실제 비용이다.
- 커밋 메시지는 Conventional Commits 형식.

## 아키텍처

### 백엔드 패키지 규칙 (`com.hiddengyeongbuk.backend`)

- `domain.{hub,region,place,sync}` — JPA 엔티티+레포지토리
- `ingest` — TourAPI 수집 파이프라인. `ingest.client`(TourAPI 통신), `ingest.summary`(번역: TranslateClient·EnglishSummaryService), `ingest.admin`(수동 트리거)
- `catalog` — 프론트향 조회·추천 API. `catalog.course`(추천 엔진), `catalog.dto`
- `support` — 도메인 무관 공용(Retry, GeoUtils, WebConfig, PlaceDataChangedEvent)

새 기능도 이 분류를 따른다: 외부 API 통신은 `client`(또는 기능 전용 서브패키지), 운영자용 HTTP는 `admin`, 프론트향 조회는 `catalog`. ingest→catalog 직접 의존은 금지 — `PlaceDataChangedEvent`(Spring 이벤트)로만 통신한다.

### 데이터 흐름의 대원칙

**수집(ingest)과 조회(catalog)는 완전히 분리된다.** catalog API는 요청 시점에 TourAPI·번역 API를 절대 호출하지 않고 MySQL만 읽는다(NFR-04가 설계로 충족되는 이유). 외부 API는 수집 배치와 백필(`POST /internal/summaries`)에서만 호출된다.

### 수집 파이프라인 (ingest)

- `PlaceCollectionService`가 areaBasedSyncList2 페이지네이션으로 지역별 수집. `place.source_modified_at`에 TourAPI modifiedtime을 저장해 행별로 비교, 안 바뀐 항목은 상세조회(국문+영문 2회)를 생략한다 — 호출 한도 절약의 핵심.
- **트랜잭션은 항목 단위**: 수집 루프에 `@Transactional`을 걸지 말 것. DB 쓰기는 `PlaceWriter`(별도 빈)가 항목마다 독립 커밋한다. 지역 전체를 트랜잭션 하나로 묶으면 외부 HTTP 대기 중 커넥션 점유 + 중간 DB 오류 1건이 rollback-only로 지역 전체를 날린다. 같은 클래스 내 메서드 분리는 self-invocation이라 AOP가 안 먹는다 — 반드시 별도 빈으로.
- **무료 로컬 연산은 매번 재계산, 유료/쿼터 외부 호출은 존재 여부로 캐싱**: 태그(`PlaceTagAssigner`)는 unchanged여도 매번 재부여(규칙 변경이 자동 반영), 번역은 `place_summary`에 있으면 건너뜀. 새 외부 연동(detailImage2 등)도 이 구분을 따를 것.
- 항목 실패는 per-item try/catch로 격리 — sourceModifiedAt이 저장 안 되므로 다음 수집에서 자동 재시도된다.

### 번역 (ingest.summary)

- has_en=false 장소의 국문 제목·설명·주소를 Google Cloud Translation v2로 **한 번의 호출에 함께** 번역해 `place_summary`(title_en/summary_en/addr_en)에 장소별 1회 캐싱(ADR-013). Gemini 코드는 향후 LLM 기능용 예비 — 번역에 쓰지 말 것.
- 속도 제한: 연속 호출 시 403 `userRateLimitExceeded`(429 아님, 레거시 v2 특성). `ensureSummary` 내부에서 1초 페이싱 + 이 에러만 재시도. 무료 한도는 월 50만 자 — 전량 백필은 하루 16,000자 페이스로 분할 진행 중(사용자 결정, 완전 무료 유지).
- 외부 API 재시도는 `support.Retry.withBackoff` + transient 오류(타임아웃·5xx·TourAPI 05)만 대상. 실패 루프의 상한은 "성공 횟수"가 아니라 "시도 횟수"에 걸 것.

### 조회·추천 (catalog)

- 언어: `lang`은 컨트롤러에서 `LangFallback.normalize()`로 en/ko 두 값으로 정규화 후 서비스에 전달(캐시 키 공간 고정). 제목은 3단계 폴백 — TourAPI 영문 → 번역(`place_summary.title_en`) → 국문. 번역 제목은 `TranslatedTitleLookup`으로 페이지 단위 일괄 조회(장소별 findById 금지, N+1).
- 캐싱: `@Cacheable` + 만료 없는 ConcurrentMapCache. **키 공간이 유한한 인자만 캐시 키에 넣을 것** — nearby(좌표)와 keyword 검색이 캐싱에서 빠진 이유. place 데이터를 바꾸는 새 쓰기 경로를 추가하면 반드시 `PlaceDataChangedEvent`를 발행할 것(안 하면 캐시가 stale로 남는다).
- 추천 엔진(`catalog.course`)은 추천 알고리즘 명세서 4~5장을 그대로 구현 — 가중치·조립 규칙 변경은 명세서 갱신과 함께.

### 프론트 (frontend/src)

- API 레이어는 `api/client.ts`+`api/types.ts`(백엔드 DTO와 필드명 일치 유지). place 태그는 한글 값 그대로 내려오므로 `api/labels.ts`의 역매핑으로 표시.
- 디자인은 한지/쪽빛/단청 토큰(Tailwind `@theme`). 반응형은 `Container` 컴포넌트 기준 sm/md/lg.

## 함정·제약 (실측으로 확인된 것)

- **Flyway**: `ddl-auto: validate`. 엔티티를 바꾸면 새 V파일부터 작성. 이미 적용된 V파일 수정 금지(체크섬 실패).
- **@MapsId 연관관계(PlaceSummary·FestivalDetail)**: ID를 직접 세팅하지 말 것 — 연관 엔티티만 세팅하면 자동으로 채워진다. 직접 세팅하면 merge가 update를 시도하다 낙관적 락 예외.
- **delete 후 같은 트랜잭션에서 같은 유니크 키 재삽입**(태그 재부여 패턴): delete 직후 `flush()` 필수 — 안 하면 Hibernate가 insert를 먼저 내보내 유니크 위반.
- **TourAPI**: serviceKey에 `+`가 있으면 UriComponentsBuilder 기본 인코딩이 그대로 통과시켜 401 — `URLEncoder`로 직접 인코딩(TourApiClient.get 참고). detailCommon2(v2)는 v1의 YN 플래그를 안 받는다. contentTypeId는 국문(12/14/15/28/32/38/39)과 영문(76/78/85/75/80/79/82)이 다르다. 여행코스(25)는 영구 제외(ADR-003/009와 충돌).
- **`/internal/*` 엔드포인트**(수집·백필 트리거)는 인증 없는 로컬 전용 — 배포 전 제거/접근 제한 필수(P4 태스크).
- **공모전 규정**: 서비스 화면·카피에 "한국관광공사"/"KTO" 명칭·로고 사용 금지.
- **통합 테스트(*IT)**: 테스트 데이터는 전용 접두사로 격리하고 컬럼 길이 제약을 먼저 확인. 정리는 저장 시 받은 엔티티를 기본 CRUD `delete(entity)`로 — 파생 삭제 쿼리는 앰비언트 트랜잭션이 없는 teardown에서 실패한다. `TestRestTemplate`는 별도 커넥션이라 테스트 `@Transactional` 롤백 패턴을 쓰지 말 것.
