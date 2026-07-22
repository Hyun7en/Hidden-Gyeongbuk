# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

2026 관광데이터 활용 공모전 출품작(마감 2026-09-21). 외국인 관광객에게 경북 인구감소지역 15곳의 관광지 탐색과 Hub 기반 코스 추천을 제공하는 웹 서비스. Spring Boot 3.5(Java 17) + React(Vite·TS) 모노리포, MySQL.

설계 문서(SRS, ERD, 외부 API 연동, 추천 알고리즘, API 명세)와 WBS는 Notion "Hidden Gyeongbuk" 페이지가 원본이다. 설계 결정을 바꾸면 해당 Notion 문서도 갱신할 것.

## 설계 결정

주요 아키텍처 결정은 `docs/adr/`에 기록되어 있다.

- 새 기능 추가, 기술 스택 변경, 외부 API 연동 방식 변경 시
  관련 ADR을 먼저 읽고 기존 결정과 충돌하지 않는지 확인할 것
- 기존 ADR과 다른 방향으로 가야 한다면 코드를 쓰기 전에 알려줄 것

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
- `ingest` — TourAPI 수집 파이프라인. `ingest.client`(TourAPI 통신), `ingest.summary`(번역: TranslateClient·PlaceTranslationService), `ingest.admin`(수동 트리거). 국문 수집(`PlaceCollectionService`) + 영문 매칭(`EngPlaceMatchingService`·`KoreanNameExtractor`) + 항목 단위 쓰기(`PlaceWriter`)
- `catalog` — 프론트향 조회·추천 API. `catalog.course`(추천 엔진), `catalog.dto`
- `support` — 도메인 무관 공용(Retry, GeoUtils, WebConfig, PlaceDataChangedEvent)

새 기능도 이 분류를 따른다: 외부 API 통신은 `client`(또는 기능 전용 서브패키지), 운영자용 HTTP는 `admin`, 프론트향 조회는 `catalog`. ingest→catalog 직접 의존은 금지 — `PlaceDataChangedEvent`(Spring 이벤트)로만 통신한다.

### 데이터 흐름의 대원칙

**수집(ingest)과 조회(catalog)는 완전히 분리된다.** catalog API는 요청 시점에 TourAPI·번역 API를 절대 호출하지 않고 MySQL만 읽는다(NFR-04가 설계로 충족되는 이유). 외부 API는 수집 배치와 백필(`POST /internal/summaries`)에서만 호출된다.

### 수집 파이프라인 (ingest)

- `PlaceCollectionService`가 KorService2 areaBasedSyncList2 페이지네이션으로 지역별 국문 수집. `place.source_modified_at`에 TourAPI modifiedtime을 저장해 행별로 비교, 안 바뀐 항목은 국문 상세조회(detailCommon2)를 생략한다 — 호출 한도 절약의 핵심.
- **영문은 국문과 contentId 공간이 다르다**(실측 확인) — 국문 contentId로 영문 상세를 부르면 NO_DATA. 그래서 국문 수집 뒤 `EngPlaceMatchingService.matchRegion`이 EngService2 자체 areaBasedSyncList2를 독자 조회해 **이름+좌표로** 기존 국문 place에 매칭하고, 매칭된 장소만 EngService2 detailCommon2로 공식 영문 제목·설명·주소를 받아 `place_translation`(lang='en', source=OFFICIAL)에 채운다(`src_content_id`·`src_modified_at`에 캐싱, 값 안 바뀌면 상세 재조회 생략). 매칭 키인 국문명은 영문 title 끝 괄호에서 뽑는다(`KoreanNameExtractor`). 매칭은 정밀 우선 — 이름 불일치·좌표 가드(1km) 초과·복수후보 애매는 스킵(오라벨 방지). 짝 못 찾은 장소의 영문은 번역 폴백이 담당.
- 관광공사 신분류체계 코드(`lcls_systm1/2/3`, 대/중/소분류)는 목록 응답에 함께 오므로 수집 시 place에 함께 적재한다(무료 로컬 데이터, 태그처럼 매 수집 갱신). **현재는 저장만** — 이 코드로 태그를 개편하는 건 후속 단계.
- **트랜잭션은 항목 단위**: 수집 루프에 `@Transactional`을 걸지 말 것. DB 쓰기는 `PlaceWriter`(별도 빈)가 항목마다 독립 커밋한다. 지역 전체를 트랜잭션 하나로 묶으면 외부 HTTP 대기 중 커넥션 점유 + 중간 DB 오류 1건이 rollback-only로 지역 전체를 날린다. 같은 클래스 내 메서드 분리는 self-invocation이라 AOP가 안 먹는다 — 반드시 별도 빈으로.
- **무료 로컬 연산은 매번 재계산, 유료/쿼터 외부 호출은 존재 여부로 캐싱**: 태그(`PlaceTagAssigner`)는 unchanged여도 매번 재부여(규칙 변경이 자동 반영), 번역/공식 영문은 `place_translation`에 해당 언어 행이 있으면 건너뜀. 새 외부 연동(detailImage2 등)도 이 구분을 따를 것.
- 항목 실패는 per-item try/catch로 격리 — sourceModifiedAt이 저장 안 되므로 다음 수집에서 자동 재시도된다.

### 번역 (ingest.summary)

- has_en=false 장소의 국문 제목·설명·주소를 Google Cloud Translation v2로 **한 번의 호출에 함께** 번역해 `place_translation`(lang='en', source=TRANSLATE, title/overview/addr)에 장소별 1회 캐싱(ADR-013). Gemini 코드는 향후 LLM 기능용 예비 — 번역에 쓰지 말 것.
- **다국어는 컬럼이 아니라 `place_translation` 행으로**(언어당 1행, source=OFFICIAL/TRANSLATE, ADR-015). 언어를 늘려도 스키마 변경 없이 행만 추가. `place.has_en`은 "en 번역 존재" 캐시 플래그로만 남겨 추천 완성도 점수가 조인 없이 참조한다(실제 영문 텍스트는 place_translation에 있음).
- **순서: 영문 매칭 먼저 → 남은 장소만 번역.** 번역은 수집 루프 인라인이 아니라 `/internal/summaries` 백필로만 돈다(`findByHasEnFalse` 대상). 영문 매칭이 has_en=true로 바꾼 장소는 자동 제외되고, 공식 영문이 붙을 때 `PlaceWriter.applyEnglishOfficial`이 같은 (place_id,'en') 행을 OFFICIAL로 upsert해 기존 번역을 덮어쓴다(별도 삭제 불필요). 그래서 매칭 전에 번역부터 돌리면 곧 대체될 번역에 무료 글자수를 낭비하므로, 반드시 매칭(수집) 후 번역할 것.
- 속도 제한: 연속 호출 시 403 `userRateLimitExceeded`(429 아님, 레거시 v2 특성). `ensureTranslation` 내부에서 1초 페이싱 + 이 에러만 재시도. 무료 한도는 월 50만 자 — 전량 백필은 하루 16,000자 페이스로 분할 진행 중(사용자 결정, 완전 무료 유지).
- `place_translation.source`는 `OFFICIAL`(공식 매칭)/`TRANSLATE`(기계 번역) 둘뿐 — 옛 `place_summary`의 LLM/TOURAPI 값은 폐기됨(V10에서 테이블째 대체).
- 외부 API 재시도는 `support.Retry.withBackoff` + transient 오류(타임아웃·5xx·TourAPI 05)만 대상. 실패 루프의 상한은 "성공 횟수"가 아니라 "시도 횟수"에 걸 것.

### 조회·추천 (catalog)

- 언어: `lang`은 컨트롤러에서 `LangFallback.normalize()`로 en/ko 두 값으로 정규화 후 서비스에 전달(캐시 키 공간 고정). 제목·설명·주소는 2단계 폴백 — `place_translation`(해당 언어 행, 공식/번역 통합) → 국문. 번역 행은 `TranslationLookup`으로 페이지 단위 일괄 조회(장소별 findById 금지, N+1). 상세 응답의 `translationSource`가 그 영문 출처(OFFICIAL/TRANSLATE/null)를 알려준다.
- 캐싱: `@Cacheable` + Caffeine(`CacheConfig`, maximumSize 2000·12h TTL — page 같은 무한 키 인자 방어). **키 공간이 유한한 인자만 캐시 키에 넣을 것** — nearby(좌표)와 keyword 검색이 캐싱에서 빠진 이유. place 데이터를 바꾸는 새 쓰기 경로를 추가하면 반드시 `PlaceDataChangedEvent`를 발행할 것(안 하면 캐시가 stale로 남는다).
- 페이지네이션 조회는 반드시 정렬 키를 고정할 것(현재 PK ASC) — ORDER BY 없는 페이지는 페이지 간 중복/누락이 난다. 태그 어휘는 `domain.place.PlaceTagType`이 단일 출처 — ingest/catalog에 한글 라벨을 중복 정의하지 말 것.
- 추천 엔진(`catalog.course`)은 추천 알고리즘 명세서 4~5장을 그대로 구현 — 가중치·조립 규칙 변경은 명세서 갱신과 함께.

### 프론트 (frontend/src)

- API 레이어는 `api/client.ts`+`api/types.ts`(백엔드 DTO와 필드명 일치 유지). place 태그는 한글 값 그대로 내려오므로 `api/labels.ts`의 역매핑으로 표시.
- 디자인은 한지/쪽빛/단청 토큰(Tailwind `@theme`). 반응형은 `Container` 컴포넌트 기준 sm/md/lg.

## 함정·제약 (실측으로 확인된 것)

- **Flyway**: `ddl-auto: validate`. 엔티티를 바꾸면 새 V파일부터 작성. 이미 적용된 V파일 수정 금지(체크섬 실패).
- **@MapsId 연관관계(FestivalDetail)**: ID를 직접 세팅하지 말 것 — 연관 엔티티만 세팅하면 자동으로 채워진다. 직접 세팅하면 merge가 update를 시도하다 낙관적 락 예외. (`place_translation`은 이 함정을 피하려 @MapsId 대신 place_id 스칼라 + surrogate PK를 쓴다.)
- **delete 후 같은 트랜잭션에서 같은 유니크 키 재삽입**(태그 재부여 패턴): delete 직후 `flush()` 필수 — 안 하면 Hibernate가 insert를 먼저 내보내 유니크 위반.
- **TourAPI**: serviceKey에 `+`가 있으면 UriComponentsBuilder 기본 인코딩이 그대로 통과시켜 401 — `URLEncoder`로 직접 인코딩(TourApiClient.get 참고). detailCommon2(v2)는 v1의 YN 플래그를 안 받는다. contentTypeId는 국문(12/14/15/28/32/38/39)과 영문(76/78/85/75/80/79/82)이 다르다. 여행코스(국문25/영문75)는 영구 제외(ADR-003/009와 충돌).
- **국문/영문 contentId 공간 분리**: KorService2와 EngService2는 같은 장소라도 contentId가 다르다(실측: 영문 "Andong Dam" contentId를 KorService2 detailCommon2로 조회 시 NO_DATA). 영문 데이터는 반드시 EngService2 자체 areaBasedSyncList2로 목록을 받아 이름·좌표로 매칭할 것 — 국문 contentId 재사용 금지.
- **호출 한도는 오퍼레이션별**로 각각 일 1,000건(사용자 확인). areaBasedSyncList2와 detailCommon2가 별도 카운트라 영문 상세 백필에 여유가 있다. 그래도 무한정은 아니니 매칭 상세는 `place_translation`의 `src_content_id`+`src_modified_at`으로 캐싱해 재호출을 막는다.
- **`/internal/*` 엔드포인트**(수집·백필 트리거)는 인증 없는 로컬 전용이며 `@Profile("local")` 빈이다. 프로파일 미지정 실행은 local로 동작(`spring.profiles.default`), **운영 배포는 반드시 `SPRING_PROFILES_ACTIVE=prod`** — 그러면 이 엔드포인트가 아예 안 뜨고 show-sql도 꺼진다.
- **공모전 규정**: 서비스 화면·카피에 "한국관광공사"/"KTO" 명칭·로고 사용 금지.
- **통합 테스트(*IT)**: 테스트 데이터는 전용 접두사로 격리하고 컬럼 길이 제약을 먼저 확인. 정리는 저장 시 받은 엔티티를 기본 CRUD `delete(entity)`로 — 파생 삭제 쿼리는 앰비언트 트랜잭션이 없는 teardown에서 실패한다. `TestRestTemplate`는 별도 커넥션이라 테스트 `@Transactional` 롤백 패턴을 쓰지 말 것.
- **Kakao Maps/Share**: JS SDK는 반드시 "JavaScript 키"(`VITE_KAKAO_MAP_KEY`)를 쓸 것 — REST API 키를 쓰면 에러. 카카오맵은 2024-12-01 이후 신규 앱이면 카카오 디벨로퍼스 콘솔의 앱 관리 > [카카오맵] > [사용 설정]을 ON으로 켜야 API 호출이 동작함(비즈 앱 전환은 불필요, 콘솔 설정이라 Claude가 대신 못 함 — 사용자 확인 필요). 카카오톡 공유는 별도 사용 설정 토글이 없고 앱 생성만으로 기본 제공되지만, 대신 공유 메시지의 webUrl/mobileWebUrl 도메인을 [앱] > [제품 링크 관리] > [웹 도메인]에 등록해야 함(카카오맵이 쓰는 [플랫폼] Web 도메인 목록과는 다른 목록). 두 제품 다 같은 앱의 JS 키를 공유해서 쓴다.
