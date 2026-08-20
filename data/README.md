# data/

제목 localization 작업 산출물(ADR-026). `app.title.work-dir`가 가리키는 디렉터리다.

**여기 있는 것은 사람이 읽고 쓰는 것뿐이다.**

| 파일 | 누가 쓰나 | 무엇 |
|---|---|---|
| `title-review-{lang}.json` | 기계 | 전수 검수 판정(OK/SUSPECT). DB에는 반영되지 않는다 |
| `title-overrides-{lang}.yml` | **사람** | 확정한 교정. 유일하게 손으로 쓰는 파일 |

**"이 제목을 처리했는가"는 여기 없다** — `place_translation.title_localized_at` 컬럼이다.
그건 사람이 읽을 것이 아니라 그 행의 상태이고, 언어가 늘어도 스키마가 그대로다.

교정 파일 형식:

```yaml
titles:
  "126508":            # 국문 source_content_id (place_id 아님 — 재판정 때 바뀐다)
    ko: "숫골"          # 사람이 검토하기 위한 것. 대조에는 쓰지 않는다
    en: "Sutgol"
    reason: "proper_name"
```

## `title-backup-en-before-localize.tsv`

제목 localization을 돌리기 전 영문 제목 스냅샷(`place_id`\t`title`). 되돌리기는 기능이 아니라
운영 절차다 — 결과가 나쁘면 이 파일로 복구한다. 돌리기 전에 다시 받으려면:

```bash
set -a && source .env && set +a && docker exec -e MYSQL_PWD="$DB_PASSWORD" \
  hidden-gyeongbuk-mysql mysql -N --default-character-set=utf8mb4 -u"$DB_USERNAME" hidden_gyeongbuk \
  -e "SELECT place_id, title FROM place_translation WHERE lang='en' AND source='TRANSLATE';" \
  > data/title-backup-en-before-localize.tsv
```
