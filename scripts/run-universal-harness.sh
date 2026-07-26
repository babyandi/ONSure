#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OPERATOR_ID="${1:-internal-operator-local-1}"
ENVIRONMENT_LABEL="${2:-local-jdk17-self-validation}"
FIXTURE_FILE="${3:-$ROOT/fixtures/universal-v1/sample-target/fixtures.v1.json}"
OUTPUT_ROOT="${4:-$ROOT/receipts/universal-v1/runs}"

bash "$ROOT/scripts/preflight-universal-harness.sh"
mkdir -p "$OUTPUT_ROOT"
mvn -B -ntp -pl modules/onsure-core -am -DskipTests package >/dev/null
mvn -B -ntp -pl modules/onsure-core dependency:build-classpath \
  -Dmdep.outputFile="$ROOT/modules/onsure-core/target/harness-classpath.txt" >/dev/null
CP="$ROOT/modules/onsure-core/target/classes:$(cat "$ROOT/modules/onsure-core/target/harness-classpath.txt")"
exec java -cp "$CP" io.onsure.harness.HarnessCli run \
  "$ROOT" "$FIXTURE_FILE" "$OUTPUT_ROOT" "$OPERATOR_ID" "$ENVIRONMENT_LABEL"
