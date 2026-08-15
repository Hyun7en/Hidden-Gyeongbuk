# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

2026 관광데이터 활용 공모전 출품작(마감 2026-09-21). 외국인 관광객에게 경북 인구감소지역 15곳의 관광지 탐색과 Hub 기반 코스 추천을 제공하는 웹 서비스. Spring Boot 3.5(Java 17) + React(Vite·TS) 모노리포, MySQL.

## 설계 결정 — 문서가 정본이다

설계 문서(SRS, 아키텍처, ERD, 외부 API 연동, 추천 알고리즘, API 명세, 화면 설계)와 WBS는 Notion "Hidden Gyeongbuk" 페이지가 원본이다. **ADR만 예외로 저장소 `docs/adr/`가 정본이다** — 코드 결정의 근거라 코드와 같이 버전 관리한다.

**이 문서들은 스스로 "본 문서는 TO-BE(목표 구조)를 기술한다. 코드는 본 문서에 맞춰 변경한다"고 명시하고 있다.** 따라서 코드와 문서가 다르면 **코드가 틀린 것**이다. 코드를 근거로 설계를 추론하지 말 것.

기준 버전(2026-08-13 확인):

| 문서 | 버전 |
|---|---|
| ERD/DB 설계서 | **v0.10** (2026-08-13) |
| 의사결정 기록 (ADR) | **ADR-001~024** (최신 2026-08-13) — 정본은 `docs/adr/` |
| 시스템 아키텍처 설계서 | **v0.6** (2026-08-13) |
| API 명세서 | **v0.10** (2026-08-13) |
| 외부 API 연동 설계서 | **v0.8** (2026-08-13) |
| 추천 알고리즘 명세서 | **v0.3** (2026-08-13) |
| 요구사항 정의서 (SRS) | v0.5 |

- **ADR 정본은 저장소 `docs/adr/`다**(001~024, 2026-08-13에 Notion에서 이전). 코드 작업 중 결정 근거가 필요하면 Notion에 가지 말고 여기를 읽을 것. [`docs/adr/README.md`](docs/adr/README.md)에 결정 요약표와 대체 관계 사슬이 있다 — 본문 전에 여기부터 본다.
- 새 ADR도 `docs/adr/`에 쓴다. Notion ADR 페이지는 저장소를 가리키는 포인터로만 남아 있다.
- ADR은 **추가만 하고 수정하지 않는다**. 결정이 바뀌면 기존 ADR을 Superseded로 바꾸고 새 ADR을 쓴다. 그래서 **번호가 큰 쪽이 이긴다** — ADR-015의 결론을 인용하기 전에 ADR-017이 그 일부를 뒤집지 않았는지 확인할 것.
- 새 기능, 스택 변경, 외부 API 연동 방식 변경 시 관련 ADR을 먼저 읽을 것. 기존 결정과 다른 방향으로 가야 한다면 코드를 쓰기 전에 알릴 것.

---

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
- `ingest` — TourAPI 수집 파이프라인 4단(Collector·Resolver·Merger). `ingest.client`(TourAPI 통신), `ingest.translate`(Translator·TranslateClient), `ingest.admin`(수동 트리거)
- `catalog` — 프론트향 조회·추천 API. `catalog.course`(추천 엔진), `catalog.dto`
- `support` — 도메인 무관 공용(Retry, GeoUtils, WebConfig, PlaceDataChangedEvent, LanguageRegistry). `support.llm`(Gemini — 미사용 예비, ADR-006)

새 기능도 이 분류를 따른다: 외부 API 통신은 `client`(또는 기능 전용 서브패키지), 운영자용 HTTP는 `admin`, 프론트향 조회는 `catalog`. ingest→catalog 직접 의존은 금지 — `PlaceDataChangedEvent`(Spring 이벤트)로만 통신한다.

> 어느 설계 문서도 자바 패키지명을 규정하지 않는다. 아키텍처가 규정하는 것은 **컴포넌트 4단**(Collector/Resolver/Merger/Translator)과 `data_sync_log.phase` 값(COLLECT/RESOLVE/MERGE/TRANSLATE)이다. `ingest` → `ingestion` 같은 리네임은 근거 없으니 하지 말 것.

### 데이터 흐름의 대원칙

**수집(ingest)과 조회(catalog)는 완전히 분리된다.** catalog API는 요청 시점에 TourAPI·번역 API를 절대 호출하지 않고 MySQL만 읽는다(NFR-04가 설계로 충족되는 이유). 외부 API는 수집 배치와 번역 백필에서만 호출된다.

### 수집 파이프라인 — 4단 (ADR-016, 아키텍처 §4.2)

| 단계 | 책임 | 외부 호출 | 재실행 |
|---|---|---|---|
| **Collector** | 언어별 서비스를 **독립적으로** areaBasedSyncList2 수집해 `staging_place`에 원본 그대로 적재. 변환·판정 안 함 | TourAPI | 증분 워터마크 기준 |
| **Resolver** | staging 간 동일 장소 판정. `resolution_status`에 기록 | 없음 | **자유롭게** |
| **Merger** | MATCHED 건을 언어 중립 `place` 1건 + 언어별 `place_translation` 행으로 병합 | 없음 | **자유롭게** |
| **Translator** | `place_translation` 행이 없는 (장소, 언어) 조합에 기계 번역 생성 | Translation API | 행 존재 시 스킵 |

- **staging 분리의 근거는 호출 한도다.** 오퍼레이션별 일 1,000건에서 3,000건 재수집은 며칠이 걸린다. staging이 없으면 매칭 로직을 고칠 때마다 그 비용을 치른다. 그래서 staging은 임시 테이블이 아니라 **재매칭의 근거**이며 상시 보존한다(언어당 최신 1세트, 이력 누적 안 함).
- **매칭 키 우선순위는 좌표 → 이름**이다. 좌표는 언어 무관이고 이름 표기는 언어마다 다르기 때문. (구 ADR-014의 "이름 → 좌표"에서 역전됐다.)
- **오매칭보다 미매칭을 택한다.** 애매하면 `QUARANTINED`로 보내고 place를 만들지 않는다. 오매칭은 서로 다른 장소를 합쳐 잘못된 정보를 노출하고 탐지가 어렵지만, 미매칭은 장소가 둘로 쪼개질 뿐이고 사후 병합이 가능하다.
- **어느 한 언어에만 존재하는 장소도 버리지 않는다.** 영문 소스에만 있는 장소도 place를 만들고, 없는 언어는 번역으로 채운다. (구 구조가 국문 목록만 순회해 이걸 구조적으로 누락시킨 것이 3단 재설계의 발단이다.)
- **부분 실패는 언어 단위로 격리**: 일부 언어 수집이 실패해도 성공한 언어만으로 Resolve·Merge를 진행한다. 단 **실패한 언어의 번역은 보류**한다 — 공식 번역이 다음 주기에 올 수 있는데 지금 기계 번역하면 곧 덮어쓸 것에 무료 한도를 쓰게 된다.
- **트랜잭션은 항목 단위**: 수집 루프에 `@Transactional`을 걸지 말 것. 지역 전체를 트랜잭션 하나로 묶으면 외부 HTTP 대기 중 커넥션 점유 + 중간 DB 오류 1건이 rollback-only로 지역 전체를 날린다. 같은 클래스 내 메서드 분리는 self-invocation이라 AOP가 안 먹는다 — **반드시 별도 빈으로**.
- **무료 로컬 연산은 매번 재계산, 유료/쿼터 외부 호출은 존재 여부로 캐싱**: 태그 부여는 unchanged여도 매번 재실행(규칙 변경이 자동 반영), 번역/공식 텍스트는 `place_translation`에 해당 언어 행이 있으면 건너뜀. 새 외부 연동(detailImage2 등)도 이 구분을 따를 것.
- 항목 실패는 per-item try/catch로 격리 — 워터마크가 저장 안 되므로 다음 수집에서 자동 재시도된다.
- 관광공사 신분류체계 코드(`lcls_systm1/2/3`)는 목록 응답에 함께 오므로 수집 시 적재한다. **`lcls_systm1`(대분류)이 곧 `place_type`이다**(ADR-024) — 선택 항목이 아니라 place 생성의 전제이며, 비어 있으면 place를 만들 수 없다. 결측 시엔 1:1 대응이 명확한 6개 타입만 contentTypeId로 폴백하고, **관광지(12/76)는 대분류가 NA·HS·VE·EX 넷으로 갈라져 폴백하지 않고 QUARANTINED로 보류**한다. 중·소분류는 `staging_place`에만 남긴다.

### 데이터 모델 (ERD v0.10)

- **언어 중립 / 언어 의존을 분리한다.** 좌표·지역·타입 등 언어 무관 속성만 본체 테이블에 두고, **표시 텍스트는 예외 없이 번역 테이블 행으로 간다 — 국문도 포함**. 국문을 특별대우하면 "국문에 없는 장소"를 표현할 수 없다.
- 계층: `staging_place`(원본) → `place`/`region`/`hub`/`place_image`/`festival_detail`(언어 중립) + `place_translation`/`region_translation`/`hub_translation`(언어 의존) + `data_sync_log`(운영). 테이블 10개다 — `place_tag`는 ADR-024로 폐기됐다.
- **소스 식별자는 소스 계층에.** TourAPI contentId·contentTypeId 같은 외부 식별자는 `staging_place`와 `place_translation`에만 둔다. `place`는 내부 식별자만 갖고 **자연키가 없다** — 정체성은 Resolution 결과로만 결정된다.
- **모든 적재는 자연키 기준 upsert**(멱등성). `staging_place`는 `UNIQUE(lang, source_content_id)`, `place_translation`은 `UNIQUE(place_id, lang)`. 배치를 몇 번 돌려도 결과가 같아야 하며, 이게 Resolve·Merge 재실행의 전제다.
- **측정 전 비정규화하지 않는다**(ADR-007/017). 조인 회피용 캐시 컬럼을 선제적으로 두지 않는다 — `has_en`이 이 원칙을 어겼다가 폐기된 사례다. 이 규모(place 약 3,000건)에서는 전체가 메모리에 올라간다.
- `place_type`은 **관광공사 신분류체계 대분류 코드 원본**(AC/EV/EX/FD/HS/LS/NA/SH/VE)이다. 내부 이름으로 재매핑하지 않으므로 매핑표가 없다. `C01`(추천코스)은 여행코스라 제외. **어휘를 소유하지 않는 것이 요점**이니, 편의상 자체 enum 이름을 다시 만들지 말 것(ADR-024).
- contentTypeId 매핑은 **DB가 아니라 설정 파일**에 둔다 — 언어 추가가 마이그레이션을 유발하지 않게 하기 위함(아키텍처 §4.3). 단 이제 이 매핑은 `place_type`을 정하지 않고 **수집 대상 선정 + 결측 폴백**만 담당한다.
- 언어 코드는 **BCP 47**(`ko`, `en`, `ja`, `zh-CN`…). 프론트 `lang` 속성·`Accept-Language`와 그대로 호환된다.
- **언어 추가 시 스키마 변경이나 코드 수정이 발생하면 설계 원칙이 깨진 신호**로 보고 원인을 점검할 것(ADR-018). 구조는 N개 언어 전제, 실데이터는 국·영문 우선.

### 번역 (ADR-013)

- 국문 제목·설명·주소를 Google Cloud Translation v2로 **한 번의 호출에 함께** 번역해 `place_translation`(source=TRANSLATE)에 (장소, 언어)별 1회 캐싱. **Gemini는 향후 챗봇 등 LLM이 실제로 필요한 기능용 예비다 — 번역에 쓰지 말고, 코드·설정도 지우지 말 것**(ADR-006 후속 주석).
- `place_translation.source`는 `OFFICIAL`(해당 언어 서비스 원본)/`TRANSLATE`(기계 번역) 둘뿐. 공식이 확보되면 같은 행을 OFFICIAL로 덮어쓴다 — **TRANSLATE → OFFICIAL은 단방향이고 역방향은 없다.**
- **순서: 수집·매칭 먼저 → 남은 조합만 번역.** 매칭 전에 번역부터 돌리면 곧 대체될 번역에 무료 글자수를 낭비한다.
- 중간 언어를 경유하지 않는다(국문 → 영어 → 일본어 금지). 오역이 누적된다.
- 속도 제한: 연속 호출 시 403 `userRateLimitExceeded`(429 아님, 레거시 v2 특성). 1초 페이싱 + 이 에러만 재시도. 무료 한도는 **월** 50만 자 — 하루 16,000자 페이스로 분할 진행(사용자 결정, 완전 무료 유지). 언어를 늘리면 문자량을 재산정할 것.
- 외부 API 재시도는 `support.Retry.withBackoff` + transient 오류(타임아웃·5xx·TourAPI 05)만 대상. 실패 루프의 상한은 "성공 횟수"가 아니라 **"시도 횟수"**에 걸 것.

### 조회·추천 (catalog)

- 언어: `lang`은 컨트롤러에서 `LangFallback.normalize()`로 정규화 후 서비스에 전달(캐시 키 공간 고정). 제목·설명·주소는 **3단계 폴백** — 요청 언어 행 → `ko` 행 → 존재하는 아무 행. 응답에 실제 사용된 언어와 `source`를 함께 내려 클라이언트가 출처를 표시할 수 있게 한다.
- 번역 행은 페이지 단위로 **일괄 조회**할 것 — 장소별 findById는 N+1이다.
- 캐싱: `@Cacheable` + Caffeine(`CacheConfig`, maximumSize 2000·12h TTL — page 같은 무한 키 인자 방어). **키 공간이 유한한 인자만 캐시 키에 넣을 것** — nearby(좌표)와 keyword 검색이 캐싱에서 빠진 이유. place 데이터를 바꾸는 새 쓰기 경로를 추가하면 반드시 `PlaceDataChangedEvent`를 발행할 것(안 하면 캐시가 stale로 남는다).
- 페이지네이션 조회는 반드시 정렬 키를 고정할 것(현재 PK ASC) — ORDER BY 없는 페이지는 페이지 간 중복/누락이 난다.
- **분류축은 `place_type` 하나다.** 관심사(`interests`)도 같은 어휘를 받으며, 별도 `interest` enum이나 태그 축을 만들지 말 것(ADR-024). 표시 라벨은 `GET /api/meta/options`가 요청 언어로 해석해 내려보낸다.
- 추천 엔진(`catalog.course`)은 추천 알고리즘 명세서를 구현한다. 점수는 **코스 단위**로 4항목(관심사 35 / 접근성 30 / 지역분산 20 / 데이터 완성도 15). 관심사 적합도의 근거 데이터는 `place.place_type`이며, 장소당 분류가 하나라 코스가 커버하는 관심사 수가 장소 수를 넘지 못한다(추천 §4.1). 가중치·코스 제약은 **설정으로 외부화**하고 기동 시 합계 100을 검증한다(아키텍처 §4.3).
- **추정치에 점수를 부여하지 않는다.** 실제 수집 데이터로 계산할 수 없는 항목은 가중치 점수가 아니라 명시적 제약으로 표현한다 — 체류 적합도가 이 이유로 점수에서 제외됐다(ADR-021). 기간 제약은 룩업 표가 아니라 공식으로: `days = nights+1`, `장소 수 ≈ 3.5×days`, `거리 상한 = base(difficulty)×days`, `필요 숙박 = nights`.
- **계절 반영은 축제 날짜 겹침 하나뿐이다.** 계절성 태그("벚꽃·단풍")는 자체 어휘를 다시 소유하는 일이라 폐기됐고(ADR-024, ADR-020 후속 주석), `festival_detail` 기간이 수집 데이터로 계산되는 유일한 계절 근거다. 여행 기간에 안 열리는 축제는 **점수를 깎는 게 아니라 후보에서 제외한다** — 닫힌 축제를 일정에 넣는 건 취향이 아니라 틀린 정보다. 겹치면 사전점수에 가산(`course.festival.pre-score-bonus`)한다. 점수 항목이 아니므로 가중치 합계 100에 들어가지 않는다. **날짜가 없는 행사는 판단하지 않고 일반 장소처럼 다룬다** — 결측을 "안 열린다"로 읽으면 콘텐츠를 근거 없이 잃는다.
- 코스는 **저장하지 않는다**(ADR-003/009). 공유 URL은 조건을 파라미터로 인코딩해 백엔드가 재계산한다(무상태).

### 프론트 (frontend/src)

- API 레이어는 `api/client.ts`+`api/types.ts`(백엔드 DTO와 필드명 일치 유지).
- `placeType` 등 enum 코드의 표시 라벨은 `GET /api/meta/options`가 요청 언어로 해석해 내려준다(API 명세서 §2.3). 프론트가 자체 라벨 사전을 갖지 않게 해서 언어 추가 시 백엔드 한 곳만 고치게 한다.
- 디자인은 한지/쪽빛/단청 토큰(Tailwind `@theme`). 반응형은 `Container` 컴포넌트 기준 sm/md/lg.
- **PWA를 쓰지 않는다**(ADR-022). 서비스워커·매니페스트를 두지 말 것. 그 결과로 **자체 뒤로가기 버튼도 전 화면에서 두지 않는다** — 일반 반응형 웹이라 브라우저 뒤로가기가 항상 있고, 로고가 이미 "홈으로"를 맡는다.

## 함정·제약 (실측으로 확인된 것)

- **Flyway**: `ddl-auto: validate`. 엔티티를 바꾸면 새 V파일부터 작성. **이미 적용된 V파일 수정 금지**(체크섬 실패). 현재 `V1__init_schema.sql`(ERD v0.9 전체) + `V2__seed_hub_region.sql` 둘뿐이며, 다음 변경은 **V3부터**다. 구 V1~V11은 2026-08-12에 스쿼시로 대체됐다(ADR-023) — 배포 전 1회 한정 예외이므로 앞으로는 다시 증분으로 쌓는다.
- **@MapsId 연관관계(FestivalDetail)**: ID를 직접 세팅하지 말 것 — 연관 엔티티만 세팅하면 자동으로 채워진다. 직접 세팅하면 merge가 update를 시도하다 낙관적 락 예외. (`place_translation`은 이 함정을 피하려 @MapsId 대신 place_id 스칼라 + surrogate PK를 쓴다.)
- **delete 후 같은 트랜잭션에서 같은 유니크 키 재삽입**(구 태그 재부여에서 겪었고, place_image 재적재 등 자식 컬렉션 갱신에 그대로 적용): delete 직후 `flush()` 필수 — 안 하면 Hibernate가 insert를 먼저 내보내 유니크 위반.
- **TourAPI**: serviceKey에 `+`가 있으면 UriComponentsBuilder 기본 인코딩이 그대로 통과시켜 401 — `URLEncoder`로 직접 인코딩(TourApiClient.get 참고). detailCommon2(v2)는 v1의 YN 플래그를 안 받는다. contentTypeId는 국문(12/14/15/28/32/38/39)과 영문(76/78/85/75/80/79/82)이 다르다. 여행코스(국문25)는 다중 스팟 루트라 place 모델과 안 맞아 영구 제외(ADR-003/009), 교통(영문77)은 대응 국문 타입이 없어 제외.
- **국문/영문 contentId 공간 분리**: KorService2와 EngService2는 같은 장소라도 contentId가 다르다(실측: 영문 "Andong Dam" contentId를 KorService2 detailCommon2로 조회 시 NO_DATA). **각 언어 서비스는 자기 areaBasedSyncList2로 독자 수집할 것 — 다른 언어의 contentId 재사용 금지.** 이건 영문만의 특성이 아니라 모든 언어에 해당한다고 전제한다.
- **호출 한도는 오퍼레이션별**로 각각 일 1,000건(사용자 확인). areaBasedSyncList2와 detailCommon2가 별도 카운트다. 그래도 무한정은 아니니 상세 조회는 `src_content_id`+`src_modified_at`으로 캐싱해 재호출을 막는다.
- **`/internal/*` 엔드포인트**(수집·백필 트리거)는 인증 없는 로컬 전용이며 `@Profile("local")` 빈이다. 프로파일 미지정 실행은 local로 동작(`spring.profiles.default`), **운영 배포는 반드시 `SPRING_PROFILES_ACTIVE=prod`** — 그러면 이 엔드포인트가 아예 안 뜨고 show-sql도 꺼진다.
- **공모전 규정**: 서비스 화면·카피에 "한국관광공사"/"KTO" 명칭·로고 사용 금지.
- **통합 테스트(*IT)**: 테스트 데이터는 전용 접두사로 격리하고 컬럼 길이 제약을 먼저 확인. 정리는 저장 시 받은 엔티티를 기본 CRUD `delete(entity)`로 — 파생 삭제 쿼리는 앰비언트 트랜잭션이 없는 teardown에서 실패한다. `TestRestTemplate`는 별도 커넥션이라 테스트 `@Transactional` 롤백 패턴을 쓰지 말 것.
- **Kakao Maps/Share**: JS SDK는 반드시 "JavaScript 키"(`VITE_KAKAO_MAP_KEY`)를 쓸 것 — REST API 키를 쓰면 에러. 카카오맵은 2024-12-01 이후 신규 앱이면 카카오 디벨로퍼스 콘솔의 앱 관리 > [카카오맵] > [사용 설정]을 ON으로 켜야 API 호출이 동작함(비즈 앱 전환은 불필요, 콘솔 설정이라 Claude가 대신 못 함 — 사용자 확인 필요). 카카오톡 공유는 별도 사용 설정 토글이 없고 앱 생성만으로 기본 제공되지만, 대신 공유 메시지의 webUrl/mobileWebUrl 도메인을 [앱] > [제품 링크 관리] > [웹 도메인]에 등록해야 함(카카오맵이 쓰는 [플랫폼] Web 도메인 목록과는 다른 목록). 두 제품 다 같은 앱의 JS 키를 공유해서 쓴다.
