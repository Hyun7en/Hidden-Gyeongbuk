#!/usr/bin/env bash
# SessionStart 훅 — HANDOFF.md를 세션 컨텍스트에 주입한다.
# 파일이 없거나 비어 있으면 조용히 아무것도 하지 않는다(훅은 세션을 막지 않는다).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
handoff="$root/HANDOFF.md"

[ -s "$handoff" ] || exit 0

# additionalContext와 systemMessage를 함께 낸다 — 문서 판본에 따라 SessionStart가 어느 쪽을
# 받는지 엇갈려서, 모르는 필드는 무시된다는 점을 이용해 양쪽 모두에 싣는다.
python3 - "$handoff" <<'PY'
import json, sys

with open(sys.argv[1], encoding="utf-8") as fh:
    body = fh.read()

payload = "이 저장소의 작업 상태 정본 HANDOFF.md (SessionStart 훅 자동 주입):\n\n" + body

print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": payload,
    },
    "systemMessage": payload,
}, ensure_ascii=False))
PY
