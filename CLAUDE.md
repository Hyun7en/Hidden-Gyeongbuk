# CLAUDE.md

Claude Code가 이 저장소에서 작업할 때의 규칙. **작업 규칙만 둔다** — 설계 내용·현황 수치는
정본이 따로 있고(아래), 여기에 사본을 만들면 갈라진다.

## 프로젝트

2026 관광데이터 활용 공모전 출품작(마감 2026-09-21). 외국인 관광객에게 경북 인구감소지역
15곳의 관광지 탐색과 Hub 기반 코스 추천을 제공하는 웹 서비스. Spring Boot 3.5(Java 17) +
React(Vite·TS) 모노리포, MySQL. 실행 방법은 [README.md](README.md).

## 정본이 어디 있는가

| 무엇 | 정본 | 규칙 |
|---|---|---|
| 결정의 근거 | 저장소 `docs/adr/` | 코드 작업 중엔 Notion에 가지 말 것 |
| 설계 문서 | Notion "Hidden Gyeongbuk Wiki" | 직책 페이지 아래 Owner 기준 배치 |
| 현재 상태·수치·잔량 | Notion `📋 Project Status` | 건수·버전·진행률을 여기에 적지 말 것 |
| 미해결 이슈 | Notion QA Engineer ▸ 이슈 로그 | 결정과 섞지 말 것 |
| 화면 치수·배치 | Claude Design 프로토타입 | 화면 설계서와 어긋나면 프로토타입이 이김 |
| 자바 패키지 구조 | **이 파일** | 어느 설계 문서도 규정하지 않는다 |

- **설계 문서는 스스로 "TO-BE를 기술한다. 코드는 여기 맞춰 변경한다"고 명시한다.** 코드와
  문서가 다르면 **코드가 틀린 것**이다. 코드를 근거로 설계를 추론하지 말 것.
- **문서를 인용하기 전에 본문 말미의 보강 절을 먼저 볼 것.** 헤더 버전만 보면 최신 결정을
  놓친다 — Notion 표 편집이 문서 구조를 깨뜨린 적이 있어 보강을 말미에 쌓아 둔 문서가 있다.
- **ADR은 추가만 하고 수정하지 않는다.** 결정이 바뀌면 기존 것을 Superseded로 두고 새로 쓴다.
  그래서 **번호가 큰 쪽이 이긴다** — 인용 전에 뒤 번호가 뒤집지 않았는지 확인할 것.
  [`docs/adr/README.md`](docs/adr/README.md)의 결정 요약표와 대체 관계 사슬을 본문보다 먼저 본다.
- **기존 결정과 다른 방향으로 가야 하면 코드를 쓰기 전에 알릴 것.** 새 기능·스택 변경·외부 API
  연동 방식 변경은 관련 ADR을 먼저 읽는다.

## 명령어

```bash
# 백엔드 (backend/ 에서)
./gradlew test                                  # 단위·슬라이스 (*IT 제외, DB 불필요)
./gradlew test --tests "*CourseScorerTest"      # 단일 클래스
./gradlew integrationTest                       # *IT — 실제 MySQL(+일부 TourAPI) 필요
./gradlew bootRun                               # 서버 실행 (MySQL 필요)

# MySQL (루트에서; WSL이라 systemd 없음 — 도커 데몬 수동 기동)
sudo service docker start && docker compose up -d mysql

# 프론트 (frontend/ 에서)
npm run dev · npm run lint · npx tsc --noEmit · npm run build
```

- **DB·외부 API가 필요한 실행(integrationTest, bootRun, 수집·백필 트리거)은 직접 하지 말고
  명령어를 사용자에게 제시할 것.** 외부 API 한도는 실제 비용이다.
- 루트 `.env`는 `backend/build.gradle`이 bootRun/test/integrationTest에 자동 주입한다.
  **단 `java -jar`에는 안 들어간다** — Gradle의 `JavaExec`·`Test` 태스크에만 걸려 있어서 jar를
  직접 띄우면 `application.yml` 기본값으로 DB에 붙다가 Flyway에서 죽는다. 배포 검증 때는
  `set -a; source ../.env; set +a` 후 실행.
- 커밋 메시지는 Conventional Commits.

## 백엔드 패키지 규칙 (`com.hiddengyeongbuk.backend`)

- `domain.{hub,region,place,sync}` — JPA 엔티티+레포지토리
- `ingest` — TourAPI 수집 파이프라인. `ingest.client`(TourAPI 통신), `ingest.translate`(번역),
  `ingest.translate.title`(제목 localization), `ingest.admin`(수동 트리거)
- `catalog` — 프론트향 조회·추천. `catalog.course`(추천 엔진), `catalog.dto`
- `support` — 도메인 무관 공용(Retry, GeoUtils, LanguageRegistry, PlaceDataChangedEvent).
  `support.llm`(Gemini)

새 기능도 이 분류를 따른다: 외부 API 통신은 `client`(또는 기능 전용 서브패키지), 운영자용
HTTP는 `admin`, 프론트향 조회는 `catalog`. **ingest→catalog 직접 의존은 금지** —
`PlaceDataChangedEvent`로만 통신한다.

> 아키텍처 문서가 규정하는 것은 **컴포넌트 4단**(Collector/Resolver/Merger/Translator)과
> `data_sync_log.phase` 값이다. `ingest` → `ingestion` 같은 리네임은 근거가 없다.

## 코드를 쓸 때 지킬 것

**수집(ingest)과 조회(catalog)는 완전히 분리된다.** catalog는 요청 시점에 외부 API를 절대
호출하지 않고 MySQL만 읽는다. 외부 호출은 수집 배치와 백필에서만 일어난다.

- **트랜잭션은 항목 단위.** 수집 루프에 `@Transactional`을 걸지 말 것 — 외부 HTTP 대기 중
  커넥션을 점유하고, 중간 오류 1건이 rollback-only로 전체를 날린다. 같은 클래스 내 메서드
  분리는 self-invocation이라 AOP가 안 먹는다. **반드시 별도 빈으로.**
- **항목 실패는 per-item try/catch로 격리하고, 실패한 건에는 완료 표시를 남기지 말 것.**
  표시가 없어야 다음 실행이 자동으로 다시 시도한다. 성공 표시를 미리 찍으면 실패가 영구화된다.
- **무료 로컬 연산은 매번 재계산, 유료·쿼터 외부 호출은 "이미 물어봤는가"로 캐싱.** 판단
  기준은 **받은 개수가 아니라 조회 표시**다 — 개수로 보면 결과가 빈 항목을 영원히 다시 묻는다.
- **오매칭보다 미매칭을 택한다.** 애매하면 보류하고 만들지 않는다. 오매칭은 탐지가 어렵고,
  미매칭은 사후 병합이 가능하다.
- **측정 전 비정규화하지 않는다**(ADR-007/017). 조인 회피용 캐시 컬럼을 선제적으로 두지 말 것.
- **분류축은 `place_type` 하나다**(ADR-024). 관심사도 같은 어휘를 쓴다 — 별도 enum이나 태그
  축을 만들지 말 것. **어휘를 소유하지 않는 것이 요점**이라 자체 이름으로 재매핑하지 않는다.
- **추정치에 점수를 부여하지 않는다**(ADR-021). 수집 데이터로 계산할 수 없는 항목은 가중치가
  아니라 명시적 제약으로 표현한다.
- **번역 순서: 수집·매칭 먼저 → 남은 조합만 번역.** 매칭 전에 번역하면 곧 대체될 것에 한도를
  쓴다. 중간 언어를 경유하지 않는다(국문 → 영어 → 일본어 금지).
- **제목만 Gemini, 설명글·주소는 Google 번역**(ADR-026). 제목은 평균 7.8자라 문맥이 없어
  번역 API가 고유명사를 뜻으로 푼다. **설명글을 Gemini로 번역하지 말 것** — ADR-013이 유효하다.
- **언어 추가 시 스키마 변경이나 코드 수정이 발생하면 설계 원칙이 깨진 신호**다(ADR-018).
- 조회: **모든 호출에 `lang`을 실을 것**(백엔드가 `Accept-Language`를 읽지 않고 미지정을 `ko`로
  떨어뜨린다). 번역 행은 페이지 단위 **일괄 조회**(장소별 findById는 N+1). 페이지네이션은
  **정렬 키를 고정**(ORDER BY 없으면 페이지 간 중복·누락). 캐시 키에는 **키 공간이 유한한
  인자만** 넣는다. place 데이터를 바꾸는 쓰기 경로에는 반드시 `PlaceDataChangedEvent` 발행.
- 프론트: 선택지 목록과 enum 라벨은 **`GET /api/meta/options` 하나에서** 온다 — i18n에 라벨
  사전을 만들지 말 것. 폭은 `Container` 하나로 통일. **컴포넌트 스타일을 손으로 재현하지 말 것**
  (`styles/ds.css`가 프로토타입 CSS의 이전본이며, 안 옮긴 것은 그 파일 주석에 있다).
- **PWA를 쓰지 않는다**(ADR-022). 서비스워커·매니페스트를 두지 말고, **자체 뒤로가기 버튼도
  두지 않는다** — 브라우저 뒤로가기가 항상 있고 로고가 "홈으로"를 맡는다.

## 함정 (실측으로 확인된 것)

- **Flyway**: `ddl-auto: validate`. 엔티티를 바꾸면 새 V파일부터. **이미 적용된 V파일 수정
  금지**(체크섬 실패). **다음 번호는 `ls`로 확인할 것** — 목록을 문서에 적어 두면 뒤처져
  중복 번호를 만든다(2026-08-18에 실제로 V3을 중복 생성했다).
- **`@MapsId` 연관관계**: ID를 직접 세팅하지 말 것 — 연관 엔티티만 넣으면 자동으로 채워진다.
  직접 넣으면 merge가 update를 시도하다 낙관적 락 예외.
- **delete 후 같은 트랜잭션에서 같은 유니크 키 재삽입**: delete 직후 `flush()` 필수 — 안 하면
  Hibernate가 insert를 먼저 내보내 유니크 위반.
- **TourAPI**: serviceKey에 `+`가 있으면 UriComponentsBuilder 기본 인코딩이 통과시켜 401 —
  `URLEncoder`로 직접 인코딩. **언어별로 contentId 공간이 분리돼 있어** 다른 언어의 contentId를
  재사용하면 NO_DATA다. **호출 한도는 (언어 서비스 × 오퍼레이션)별**로 각각 걸린다.
- **상세 조회 실패 시 워터마크(`staging_place.source_modified_at`)를 되돌릴 것.** upsert가 먼저
  커밋되므로 되돌리지 않으면 다음 수집에서 unchanged로 판정돼 **overview가 영구히 빈 채로
  남는다.** 단 **응답은 왔는데 비어 있는 경우는 지우지 말 것** — 정상 결과이며 무한 재시도가 된다.
- **`overview` 보유율은 타입마다 다르다** — 없는 것이 우리 버그로 보이면 먼저 타입을 확인할 것.
- **`tel`에 전화번호만 오지 않는다.** 안내문이 통째로 들어오는 경우가 있어 폭이 좁으면 적재가
  실패하고 per-item catch가 삼킨다. 화면에 나가는 값은 Merge가 태그를 걷어낸다.
- **`/internal/*`는 인증 없는 로컬 전용**이며 `@Profile("local")` 빈이다. **운영 배포는 반드시
  `SPRING_PROFILES_ACTIVE=prod`** — 미지정이면 `spring.profiles.default: local`이라 열린다.
- **Tailwind v4 레이어**: `ds.css`는 반드시 `layer(components)`로 import할 것. 레이어 없이
  넣으면 CSS 캐스케이드 규칙 때문에 Tailwind 유틸리티를 전부 이겨, `hg-*`에 주는 크기·여백이
  **조용히 무시된다.** 선언 순서를 바꿔도 해결되지 않는다.
- **배포에서만 깨지는 것 셋** (2026-08-19에 전부 발견·수정. 로컬에서는 구조적으로 안 드러나니
  되돌리지 말 것):
  1. **소스 이미지 URL이 http·https로 섞여 온다** — 그대로 두면 HTTPS 배포에서 mixed content로
     막힌다. 수집 단계에서 https로 올린다.
  2. **웹폰트 `@import`를 `index.css`에 두면 프로덕션 번들에서 사라진다** — Tailwind 전개 뒤로
     밀려 "@import는 모든 규칙보다 앞" 규칙에 걸린다. `index.html`의 `<link>`로 받는다.
  3. **SPA 폴백이 없으면 공유 URL이 전부 404다** — `SpaForwardingConfig`가 index.html로
     폴백하되 `/api`·`/internal`과 확장자 있는 경로는 제외한다.
- **정적 번들은 백엔드가 서빙한다**(ADR-002). `bootJar`가 `frontend/dist`를 jar에 넣는다.
  `test`·`bootRun`에는 걸리지 않는다 — 개발은 Vite dev 서버가 `/api`를 프록시한다.
- **통합 테스트(*IT)**: 테스트 데이터는 전용 접두사로 격리하고 컬럼 길이 제약을 먼저 확인.
  정리는 저장 시 받은 엔티티를 기본 CRUD `delete(entity)`로 — 파생 삭제 쿼리는 앰비언트
  트랜잭션이 없는 teardown에서 실패한다. `TestRestTemplate`는 별도 커넥션이라 테스트
  `@Transactional` 롤백 패턴을 쓰지 말 것.
- **Kakao**: JS SDK는 반드시 "JavaScript 키"를 쓸 것(REST 키는 에러). 도메인 등록은 **호스트
  문자열 단위**라 `localhost`와 `127.0.0.1`이 서로 다르게 취급된다 — 개발 중 지도가 안 보이면
  주소창부터 확인. 지도와 공유는 **등록하는 도메인 목록이 서로 다르다.**
- **공모전 규정**: 서비스 화면·카피에 "한국관광공사"/"KTO" 명칭·로고 사용 금지.
