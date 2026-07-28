#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"
MODE="full"; PROFILE="core"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="${2:-}"; shift 2 ;;
    --profile) PROFILE="${2:-}"; shift 2 ;;
    *) echo "usage: bash scripts/onsure-local-gate.sh [--mode static|full] [--profile core|oruda]" >&2; exit 64 ;;
  esac
done
[[ "$MODE" == "static" || "$MODE" == "full" ]] || { echo "ONSURE_LOCAL_GATE_FAIL INVALID_MODE_$MODE" >&2; exit 64; }
[[ "$PROFILE" == "core" || "$PROFILE" == "oruda" ]] || { echo "ONSURE_LOCAL_GATE_FAIL INVALID_PROFILE_$PROFILE" >&2; exit 64; }
for command in git bash python3 sha256sum cmp; do command -v "$command" >/dev/null 2>&1 || { echo "ONSURE_LOCAL_GATE_FAIL MISSING_COMMAND_$command" >&2; exit 69; }; done
[[ -z "$(git status --porcelain)" ]] || { echo "ONSURE_LOCAL_GATE_FAIL WORKTREE_DIRTY_OR_UNTRACKED" >&2; exit 72; }
STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"; OUT="${ONSURE_LOCAL_GATE_OUTPUT:-$ROOT/.onsure/local-gate/$STAMP}"
mkdir -p "$OUT/logs" "$OUT/artifacts"
python3 scripts/create-source-snapshot.py --output "$OUT/source-start.json"
VALIDATION_PYTHON="python3"
if ! python3 -c 'import jsonschema, yaml' >/dev/null 2>&1; then
  python3 -m venv "$OUT/validation-venv"
  "$OUT/validation-venv/bin/python" -m pip install --disable-pip-version-check --no-input -r requirements-validation.txt > "$OUT/logs/validation-dependency-install.log" 2>&1
  VALIDATION_PYTHON="$OUT/validation-venv/bin/python"
fi
run_step(){ local name="$1"; shift; "$@" > "$OUT/logs/$name.log" 2>&1 || { echo "ONSURE_LOCAL_GATE_FAIL $name" >&2; tail -n 240 "$OUT/logs/$name.log" >&2 || true; exit 1; }; }
run_step structured-contracts "$VALIDATION_PYTHON" scripts/validate-structured-contracts.py --require-full
run_step design-coverage python3 scripts/validate-design-coverage.py --matrix status/design-capability-coverage.v2.json --root . --self-test
run_step product-subrequirements python3 scripts/validate-product-subrequirements.py --self-test
run_step workflow-surface-parity python3 scripts/validate-workflow-surface-parity.py --self-test
run_step critical-callpaths python3 scripts/validate-critical-callpaths.py --self-test
run_step status-consistency python3 scripts/validate-status-consistency.py
run_step automation-boundary python3 scripts/validate-ci-boundary.py
run_step verification-claims python3 scripts/validate-verification-claims.py
run_step module-boundary python3 scripts/check-module-boundaries.py
run_step repository-contracts python3 scripts/validate-repository-contracts.py
run_step codespace-free-remediation python3 scripts/validate-codespace-free-remediation.py
run_step omission-tests python3 -m unittest tests.test_omission_detection_gates -v
run_step automation-tests python3 -m unittest tests.test_ci_boundary -v
run_step verification-claim-tests python3 -m unittest tests.test_verification_claims -v
run_step shell-syntax bash scripts/check-shell-syntax.sh
if [[ "$MODE" == "full" ]]; then
  for command in java javac mvn bwrap prlimit timeout node npm; do command -v "$command" >/dev/null 2>&1 || { echo "ONSURE_LOCAL_GATE_FAIL MISSING_COMMAND_$command" >&2; exit 69; }; done
  JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"; JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
  [[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || { echo "ONSURE_LOCAL_GATE_FAIL JDK17_REQUIRED" >&2; exit 70; }
  export ONSURE_FIXTURE_SANDBOX_MODE=REQUIRED; unset ONSURE_FIXTURE_SANDBOX_BACKEND || true
  run_step java-root mvn -B -ntp -q test
  run_step java-modular mvn -B -ntp -q -f pom-modular.xml test
  run_step core-without-oruda mvn -B -ntp -q -f pom-modular.xml -pl modules/onsure-core -am test
  run_step sandbox-boundary bash scripts/test-fixture-sandbox-boundary.sh
  run_step generic-ai-e2e mvn -B -ntp -q -Dtest=ValidationPlatformE2ETest test
  if [[ "$PROFILE" == "oruda" ]]; then
    run_step oruda-module mvn -B -ntp -q -f pom-modular.xml -pl modules/onsure-adapter-oruda -am test
    run_step oruda-e2e mvn -B -ntp -q -Dtest=io.onsure.platform.oruda.OrudaMvf001E2ETest test
  fi
  EXT_BUILD="$OUT/vscode-extension-build"; cp -R "$ROOT/vscode-extension" "$EXT_BUILD"
  (cd "$EXT_BUILD"; npm install --ignore-scripts --no-audit --no-fund > "$OUT/logs/vscode-npm-install.log" 2>&1; npm run check > "$OUT/logs/vscode-node-check.log" 2>&1; npm run package -- --out "$OUT/artifacts/onsure.vsix" > "$OUT/logs/vscode-package.log" 2>&1)
  [[ -s "$OUT/artifacts/onsure.vsix" ]] || { echo "ONSURE_LOCAL_GATE_FAIL VSIX_MISSING" >&2; exit 74; }
  sha256sum "$OUT/artifacts/onsure.vsix" > "$OUT/artifacts/onsure.vsix.sha256"
fi
python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
cmp "$OUT/source-start.json" "$OUT/source-end.json" >/dev/null || { echo "ONSURE_LOCAL_GATE_FAIL SOURCE_DRIFT" >&2; exit 73; }
python3 - "$OUT/local-gate-result.json" "$MODE" "$PROFILE" <<'PY'
import json,pathlib,sys
path,mode,profile=sys.argv[1:]
body={"contract":"ONSURE_LOCAL_GATE_RESULT_V5","mode":mode,"profile":profile,"decision":"PASS_NONFINAL","authority_class":"LOCAL_SELF_VALIDATION","product_subrequirements":"PASS_WITH_KNOWN_GAPS","workflow_surface_parity":"PASS","critical_callpaths":"PASS","registered_failure_injections":82,"github_actions":"DISABLED","independent_otester":"NOT_RUN","independent_oaudit":"NOT_RUN","final_lock_allowed":False,"production_go":False,"commercial_go":False}
pathlib.Path(path).write_text(json.dumps(body,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
find "$OUT" -type f ! -name evidence.sha256 -print0 | sort -z | xargs -0 sha256sum > "$OUT/evidence.sha256"
echo "ONSURE_LOCAL_GATE_PASS_NONFINAL $OUT"
