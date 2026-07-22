# API 공통 규칙 (예시)

> 이 문서는 형식 예시다. 실제 규칙은 검토 후 확정한다.

## 기본

| 항목 | 내용 |
| --- | --- |
| Base Path | `/api` |
| 포맷 | JSON (UTF-8) |
| 인증 | (미정) |

## 응답 형식

성공:

```json
{ "placeId": 1, "title": "..." }
```

에러:

```json
{ "error": { "code": "INVALID_PARAM", "message": "duration is required" } }
```

## 상태 코드

| 코드 | 의미 |
| --- | --- |
| 200 | 성공 |
| 400 | 잘못된 파라미터 |
| 404 | 리소스 없음 |
| 500 | 서버 오류 |

## 네이밍

- 경로: 소문자 복수형 (`/api/places`)
- 필드: camelCase (`placeId`, `firstImage`)

---

> 작성 규칙
> - 파일명: 소문자 하이픈 (`api-convention.md`, `error-codes.md`)
> - 엔드포인트별 상세 명세는 여기가 아니라 별도 문서/도구에서 관리한다.
