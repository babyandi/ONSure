#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

bash "$ROOT/scripts/preflight-local-assurance.sh" --profile core

RUN_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RUN_STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="$ROOT/receipts/local/$RUN_STAMP"
REGISTRY="$ROOT/receipts/local/key-registry.json"
REGISTRY_SNAPSHOT="$OUT/key-registry.snapshot.json"
RUN_CONTEXT="$OUT/run-context.json"
FIXTURE_SNAPSHOT="$OUT/adversarial-transition-fixtures.snapshot.json"
SECURITY_SNAPSHOT="$OUT/security-findings.snapshot.json"
mkdir -p "$OUT"

require() { command -v "$1" >/dev/null 2>&1 || { echo "missing command: $1" >&2; exit 69; }; }
for command in java javac mvn python3 sha256sum git cmp; do require "$command"; done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
[[ "$JAVA_MAJOR" == "17" ]] || { echo "JDK 17 required, found $JAVA_MAJOR" >&2; exit 70; }

printf '{"contract":"ONSURE_LOCAL_RUN_CONTEXT_V1","run_id":"%s","started_at":"%s"}\n' \
  "$RUN_STAMP" "$RUN_STARTED_AT" > "$RUN_CONTEXT.tmp"
mv "$RUN_CONTEXT.tmp" "$RUN_CONTEXT"

cleanup_private_keys() {
  rm -f "$OUT/keys/otester-private.key" "$OUT/keys/oaudit-private.key"
}
trap cleanup_private_keys EXIT

mvn -B -ntp -pl modules/onsure-core -am -DskipTests package >/dev/null
mvn -B -ntp -pl modules/onsure-core dependency:build-classpath \
  -Dmdep.outputFile="$OUT/classpath.txt" >/dev/null
CP="$ROOT/modules/onsure-core/target/classes:$(cat "$OUT/classpath.txt")"
java -cp "$CP" io.onsure.assurance.LocalSourceLockMain "$ROOT" "$OUT/source-lock.json"

cp "$ROOT/fixtures/design/adversarial-transition-fixtures.v1.json" "$FIXTURE_SNAPSHOT.tmp"
mv "$FIXTURE_SNAPSHOT.tmp" "$FIXTURE_SNAPSHOT"
cp "$ROOT/findings/security-findings.v1.json" "$SECURITY_SNAPSHOT.tmp"
mv "$SECURITY_SNAPSHOT.tmp" "$SECURITY_SNAPSHOT"
java -cp "$CP" io.onsure.assurance.LocalSecurityGateMain "$SECURITY_SNAPSHOT"

run_once() {
  local run_id="$1"
  local run_dir="$OUT/$run_id"
  mkdir -p "$run_dir"
  mvn -B -ntp -pl modules/onsure-core -am clean test | tee "$run_dir/maven.log"
  grep -h '^Tests run:' modules/onsure-core/target/surefire-reports/*.txt \
    | python3 "$ROOT/scripts/normalize-surefire-summary.py" \
    | LC_ALL=C sort > "$run_dir/test-summary.txt"
  (
    cd modules/onsure-core/target/classes
    find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum
  ) > "$run_dir/classes.sha256"
  java -cp "$CP" io.onsure.assurance.AdversarialFixtureReportMain \
    "$FIXTURE_SNAPSHOT" "$run_dir/adversarial-fixtures.tsv"
  sha256sum "$run_dir/test-summary.txt" "$run_dir/classes.sha256" \
    "$run_dir/adversarial-fixtures.tsv" > "$run_dir/evidence.sha256"
}

run_once regression-1
run_once regression-2
cmp "$OUT/regression-1/test-summary.txt" "$OUT/regression-2/test-summary.txt"
cmp "$OUT/regression-1/classes.sha256" "$OUT/regression-2/classes.sha256"
cmp "$OUT/regression-1/adversarial-fixtures.tsv" "$OUT/regression-2/adversarial-fixtures.tsv"

mvn -B -ntp -pl modules/onsure-core -am -DskipTests package >/dev/null
CP="$ROOT/modules/onsure-core/target/classes:$(cat "$OUT/classpath.txt")"
mkdir -p "$OUT/keys"
java -cp "$CP" io.onsure.assurance.LocalKeyToolMain \
  "$OUT/keys/otester-private.key" "$OUT/keys/otester-public.key"
java -cp "$CP" io.onsure.assurance.LocalKeyToolMain \
  "$OUT/keys/oaudit-private.key" "$OUT/keys/oaudit-public.key"
chmod 600 "$OUT/keys/otester-private.key" "$OUT/keys/oaudit-private.key"
chmod 644 "$OUT/keys/otester-public.key" "$OUT/keys/oaudit-public.key"

OTESTER_KEY_ID="otester-$RUN_STAMP"
OAUDIT_KEY_ID="oaudit-$RUN_STAMP"
java -cp "$CP" io.onsure.assurance.LocalKeyRegistryMain \
  "$REGISTRY" "$OTESTER_KEY_ID" OTESTER "$OUT/keys/otester-public.key" 30
java -cp "$CP" io.onsure.assurance.LocalKeyRegistryMain \
  "$REGISTRY" "$OAUDIT_KEY_ID" OAUDIT "$OUT/keys/oaudit-public.key" 30
cp "$REGISTRY" "$REGISTRY_SNAPSHOT.tmp"
mv "$REGISTRY_SNAPSHOT.tmp" "$REGISTRY_SNAPSHOT"

INPUT_DIGEST="$(sha256sum "$OUT/regression-2/evidence.sha256" | awk '{print $1}')"
java -cp "$CP" io.onsure.assurance.LocalAgentMain OTESTER \
  "otester-$RUN_STAMP" "$INPUT_DIGEST" "$OTESTER_KEY_ID" \
  "$OUT/keys/otester-private.key" "$OUT/otester/receipt.json" \
  ONSURE_OTESTER_POLICY_V1 REGRESSION_RESULTS_AND_COMPILED_ARTIFACTS "$RUN_CONTEXT"
OTESTER_DIGEST="$(sha256sum "$OUT/otester/receipt.json" | awk '{print $1}')"
java -cp "$CP" io.onsure.assurance.LocalAgentMain OAUDIT \
  "oaudit-$RUN_STAMP" "$OTESTER_DIGEST" "$OAUDIT_KEY_ID" \
  "$OUT/keys/oaudit-private.key" "$OUT/oaudit/receipt.json" \
  ONSURE_OAUDIT_POLICY_V1 SIGNED_OTESTER_RECEIPT_AND_PUBLICATION_EVIDENCE "$RUN_CONTEXT"

sha256sum \
  "$RUN_CONTEXT" "$OUT/source-lock.json" "$FIXTURE_SNAPSHOT" "$SECURITY_SNAPSHOT" \
  "$OUT"/regression-*/test-summary.txt "$OUT"/regression-*/classes.sha256 \
  "$OUT"/regression-*/adversarial-fixtures.tsv "$OUT"/regression-*/evidence.sha256 \
  "$OUT"/otester/receipt.json "$OUT"/oaudit/receipt.json \
  "$OUT"/keys/*-public.key "$REGISTRY_SNAPSHOT" > "$OUT/final-lock.sha256"
java -cp "$CP" io.onsure.assurance.LocalFinalizerMain "$OUT"
