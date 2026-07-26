#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { echo "ASSURANCE_ENVIRONMENT_FAIL $1" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "MISSING_COMMAND_$1"; }

for command in java javac mvn git python3 sha256sum cmp; do require "$command"; done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] \
  || fail "JDK_17_REQUIRED_FOUND_java_${JAVA_MAJOR:-unknown}_javac_${JAVAC_MAJOR:-unknown}"

PYTHON_VERSION="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"
python3 - <<'PY' || fail PYTHON_3_12_OR_NEWER_REQUIRED
import sys
raise SystemExit(0 if sys.version_info >= (3, 12) else 1)
PY

MAVEN_VERSION="$(mvn -version 2>/dev/null | head -n 1)"
[[ -n "$MAVEN_VERSION" ]] || fail MAVEN_VERSION_UNAVAILABLE
COMMIT="$(git rev-parse HEAD 2>/dev/null)" || fail NOT_A_GIT_REPOSITORY
[[ "$COMMIT" =~ ^[0-9a-f]{40}$|^[0-9a-f]{64}$ ]] || fail INVALID_COMMIT_SHA
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail WORKTREE_DIRTY_OR_UNTRACKED

VENV="$ROOT/.onsure/validation-venv"
python3 -m venv "$VENV"
"$VENV/bin/python" -m pip install --disable-pip-version-check -r requirements-validation.txt
"$VENV/bin/python" - <<'PY' || fail PYTHON_VALIDATION_DEPENDENCY_IMPORT_FAILED
import jsonschema
import yaml
print(jsonschema.__version__ if hasattr(jsonschema, "__version__") else "jsonschema")
print(yaml.__version__)
PY

mvn -B -ntp -DskipTests validate >/dev/null || fail MAVEN_REACTOR_VALIDATE_FAILED
"$VENV/bin/python" scripts/validate-structured-contracts.py >/dev/null \
  || fail STRUCTURED_CONTRACT_VALIDATION_FAILED
python3 scripts/validate-core-isolation.py >/dev/null \
  || fail CORE_ISOLATION_STATIC_VALIDATION_FAILED

printf 'ONSURE_ASSURANCE_ENVIRONMENT_READY commit=%s java=%s python=%s maven=%s\n' \
  "$COMMIT" "$JAVA_MAJOR" "$PYTHON_VERSION" "$MAVEN_VERSION"
printf 'NEXT_COMMAND bash scripts/onsure-one-shot.sh --profile core\n'
