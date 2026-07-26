#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OPERATOR_1="${1:-internal-operator-1}"
OPERATOR_2="${2:-internal-operator-2}"
ENVIRONMENT_LABEL="${3:-local-jdk17-self-validation}"
[[ "$OPERATOR_1" != "$OPERATOR_2" ]] || {
  echo "ONSURE_TWO_RUN_BLOCKED OPERATOR_IDENTITIES_MUST_DIFFER" >&2
  exit 78
}
STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="$ROOT/receipts/universal-v1/two-run-$STAMP"
RUNS="$OUT/runs"
mkdir -p "$RUNS"

bash "$ROOT/scripts/run-universal-harness.sh" "$OPERATOR_1" "$ENVIRONMENT_LABEL" \
  "$ROOT/fixtures/universal-v1/sample-target/fixtures.v1.json" "$RUNS" \
  | tee "$OUT/run-1.log"
RUN_1="$(awk '/^ONSURE_UNIVERSAL_HARNESS_PASS / {print $2}' "$OUT/run-1.log" | tail -n 1)"
[[ -n "$RUN_1" && -d "$RUN_1" ]] || {
  echo "ONSURE_TWO_RUN_BLOCKED RUN_1_MISSING" >&2; exit 78;
}

bash "$ROOT/scripts/run-universal-harness.sh" "$OPERATOR_2" "$ENVIRONMENT_LABEL" \
  "$ROOT/fixtures/universal-v1/sample-target/fixtures.v1.json" "$RUNS" \
  | tee "$OUT/run-2.log"
RUN_2="$(awk '/^ONSURE_UNIVERSAL_HARNESS_PASS / {print $2}' "$OUT/run-2.log" | tail -n 1)"
[[ -n "$RUN_2" && -d "$RUN_2" ]] || {
  echo "ONSURE_TWO_RUN_BLOCKED RUN_2_MISSING" >&2; exit 78;
}

mvn -B -ntp -pl modules/onsure-core -am -DskipTests package >/dev/null
mvn -B -ntp -pl modules/onsure-core dependency:build-classpath \
  -Dmdep.outputFile="$ROOT/modules/onsure-core/target/harness-classpath.txt" >/dev/null
CP="$ROOT/modules/onsure-core/target/classes:$(cat "$ROOT/modules/onsure-core/target/harness-classpath.txt")"
java -cp "$CP" io.onsure.harness.HarnessCli verify "$RUN_1"
java -cp "$CP" io.onsure.harness.HarnessCli verify "$RUN_2"
java -cp "$CP" io.onsure.harness.HarnessCli candidate \
  "$RUN_1" "$RUN_2" "$OUT/final-candidate.json"

cat > "$OUT/two-run-result.txt" <<EOF
contract=ONSURE_UNIVERSAL_TWO_RUN_RESULT_V2
run_1=$RUN_1
run_2=$RUN_2
operator_1=$OPERATOR_1
operator_2=$OPERATOR_2
environment_label=$ENVIRONMENT_LABEL
authority_class=INTERNAL_SELF_VALIDATION
assurance_class=SELF_VALIDATION_NONFINAL
independent_otester=NOT_RUN
independent_oaudit=NOT_RUN
candidate=$OUT/final-candidate.json
final_lock_allowed=false
production_go=false
commercial_go=false
EOF
sha256sum "$OUT/run-1.log" "$OUT/run-2.log" "$OUT/final-candidate.json" \
  "$OUT/two-run-result.txt" > "$OUT/two-run-evidence.sha256"
printf 'ONSURE_UNIVERSAL_TWO_RUN_PASS_NONFINAL %s\n' "$OUT"
