#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ROOT="${1:-}"

if [[ -z "$RUN_ROOT" ]]; then
  echo "usage: $0 <run-root>" >&2
  exit 64
fi

for command in java javac mvn; do
  command -v "$command" >/dev/null 2>&1 || { echo "missing command: $command" >&2; exit 69; }
done
JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
JAVAC_MAJOR="$(javac -version 2>&1 | awk '{split($2,v,"."); print v[1]}')"
[[ "$JAVA_MAJOR" == "17" && "$JAVAC_MAJOR" == "17" ]] \
  || { echo "JDK 17 required" >&2; exit 70; }
[[ -d "$RUN_ROOT" ]] || { echo "run root not found: $RUN_ROOT" >&2; exit 71; }
RUN_ROOT="$(cd "$RUN_ROOT" && pwd)"

cd "$ROOT"
mvn -B -ntp -pl modules/onsure-core -am -DskipTests package >/dev/null
mvn -B -ntp -pl modules/onsure-core dependency:build-classpath \
  -Dmdep.outputFile="$ROOT/modules/onsure-core/target/reverify-classpath.txt" >/dev/null
CP="$ROOT/modules/onsure-core/target/classes:$(cat "$ROOT/modules/onsure-core/target/reverify-classpath.txt")"
java -cp "$CP" io.onsure.assurance.LocalVerifyMain "$RUN_ROOT" "$ROOT"
