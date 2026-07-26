#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROFILE="core"
if [[ $# -gt 0 ]]; then
  [[ "${1:-}" == "--profile" && -n "${2:-}" && $# -eq 2 ]] || {
    echo "usage: bash scripts/onsure-final-stage.sh [--profile core|oruda]" >&2
    exit 64
  }
  PROFILE="$2"
fi
[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL INVALID_PROFILE_$PROFILE" >&2
  exit 64
}

for command in git bash python3 java javac mvn sha256sum cmp; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "ONSURE_FINAL_STAGE_FAIL MISSING_COMMAND_$command" >&2
    exit 69
  }
done

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL JDK17_REQUIRED" >&2
  exit 70
}
[[ -z "$(git status --porcelain)" ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL WORKTREE_DIRTY_OR_UNTRACKED" >&2
  exit 72
}

STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="${ONSURE_FINAL_STAGE_OUTPUT:-$ROOT/.onsure/final-stage/$STAMP}"
mkdir -p "$OUT"

python3 scripts/create-source-snapshot.py --output "$OUT/source-start.json"
python3 scripts/validate-codespace-free-remediation.py | tee "$OUT/codespace-free-static-gate.log"
python3 scripts/check-module-boundaries.py | tee "$OUT/module-boundary.log"
python3 scripts/extract-atomic-requirements.py --output "$OUT/atomic-requirement-candidates.json"

set +e
bash scripts/run-core-modular-twice.sh | tee "$OUT/core-modular.log"
CORE_EXIT=${PIPESTATUS[0]}
set -e
[[ $CORE_EXIT -eq 0 ]] || {
  echo "ONSURE_FINAL_STAGE_FAIL CORE_MODULAR_EXIT_$CORE_EXIT $OUT" >&2
  exit "$CORE_EXIT"
}

ORUDA_MODULE="NOT_RUN"
if [[ "$PROFILE" == "oruda" ]]; then
  mvn -B -ntp -f pom-modular.xml -pl modules/onsure-adapter-oruda -am test \
    | tee "$OUT/oruda-module.log"
  ORUDA_MODULE="PASS_NONFINAL"
fi

set +e
bash scripts/onsure-one-shot.sh --profile "$PROFILE" | tee "$OUT/one-shot.log"
ONE_SHOT_EXIT=${PIPESTATUS[0]}
set -e

python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
cmp "$OUT/source-start.json" "$OUT/source-end.json" >/dev/null || {
  echo "ONSURE_FINAL_STAGE_FAIL SOURCE_DRIFT" >&2
  exit 73
}

python3 - "$OUT/final-stage-result.json" "$PROFILE" "$ORUDA_MODULE" "$ONE_SHOT_EXIT" <<'PY'
import json, pathlib, sys
path, profile, oruda, one_shot = sys.argv[1:]
body = {
    "contract": "ONSURE_FINAL_STAGE_RESULT_V1",
    "profile": profile,
    "codespace_free_static_gate": "PASS",
    "core_modular_two_run": "PASS_NONFINAL",
    "oruda_module": oruda,
    "one_shot_exit": int(one_shot),
    "decision": "BLOCKED" if int(one_shot) == 75 else ("NON_FINAL" if int(one_shot) == 0 else "FAIL"),
    "independent_otester": "NOT_RUN",
    "independent_oaudit": "NOT_RUN",
    "vscode_full_chain": "NOT_RUN",
    "web_full_chain": "NOT_RUN",
    "final_lock_allowed": False,
    "production_go": False,
    "commercial_go": False,
}
pathlib.Path(path).write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

find "$OUT" -maxdepth 1 -type f ! -name evidence.sha256 -print0 \
  | sort -z | xargs -0 sha256sum > "$OUT/evidence.sha256"

if [[ $ONE_SHOT_EXIT -eq 0 ]]; then
  echo "ONSURE_FINAL_STAGE_SELF_VALIDATION_NONFINAL $OUT"
  exit 0
fi
if [[ $ONE_SHOT_EXIT -eq 75 ]]; then
  echo "ONSURE_FINAL_STAGE_BLOCKED_NONFINAL $OUT" >&2
  exit 75
fi

echo "ONSURE_FINAL_STAGE_FAIL ONE_SHOT_EXIT_$ONE_SHOT_EXIT $OUT" >&2
exit "$ONE_SHOT_EXIT"
