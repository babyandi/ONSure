#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for command in java javac mvn git python3 sha256sum cmp; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "ONSURE_CORE_MODULAR_FAIL MISSING_COMMAND_$command" >&2
    exit 69
  }
done

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] || {
  echo "ONSURE_CORE_MODULAR_FAIL JDK17_REQUIRED" >&2
  exit 70
}
[[ -z "$(git status --porcelain)" ]] || {
  echo "ONSURE_CORE_MODULAR_FAIL WORKTREE_DIRTY_OR_UNTRACKED" >&2
  exit 72
}

STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="${ONSURE_CORE_MODULAR_OUTPUT:-$ROOT/.onsure/core-modular/$STAMP}"
AUTHORITY_ROOT="${TMPDIR:-/tmp}/onsure-core-modular-authority/$(git rev-parse HEAD)/$STAMP"
mkdir -p "$OUT/run-1" "$OUT/run-2"

run_once() {
  local run="$1"
  rm -rf modules/onsure-core/target modules/onsure-adapter-oruda/target
  mvn -B -ntp -f pom-modular.xml -pl modules/onsure-core -am \
    -Donsure.approvalAuthorityBase="$AUTHORITY_ROOT/$run" test \
    | tee "$OUT/$run/maven.log"
  find modules/onsure-core/target -type f -name '*.class' -print0 \
    | sort -z | xargs -0 sha256sum > "$OUT/$run/classes.sha256"
  grep -h '^Tests run:' modules/onsure-core/target/surefire-reports/*.txt \
    | python3 "$ROOT/scripts/normalize-surefire-summary.py" \
    | LC_ALL=C sort > "$OUT/$run/test-summary.txt"
}

python3 scripts/create-source-snapshot.py --output "$OUT/source-start.json"
python3 scripts/check-module-boundaries.py > "$OUT/module-boundary.json"
run_once run-1
run_once run-2
cmp "$OUT/run-1/classes.sha256" "$OUT/run-2/classes.sha256"
cmp "$OUT/run-1/test-summary.txt" "$OUT/run-2/test-summary.txt"
python3 scripts/create-source-snapshot.py --output "$OUT/source-end.json"
cmp "$OUT/source-start.json" "$OUT/source-end.json"
sha256sum "$OUT"/source-*.json "$OUT"/module-boundary.json \
  "$OUT"/run-*/classes.sha256 "$OUT"/run-*/test-summary.txt > "$OUT/evidence.sha256"

echo "ONSURE_CORE_MODULAR_TWO_RUN_PASS_NONFINAL $OUT"
