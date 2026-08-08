#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_ROOT="${1:-}"

if [[ -z "$RUN_ROOT" ]]; then
  echo "usage: $0 <run-root>" >&2
  exit 64
fi

command -v java >/dev/null 2>&1 || { echo "missing command: java" >&2; exit 69; }
command -v mvn >/dev/null 2>&1 || { echo "missing command: mvn" >&2; exit 69; }

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
[[ "$JAVA_MAJOR" == "17" ]] || { echo "JDK 17 required, found $JAVA_MAJOR" >&2; exit 70; }

[[ -d "$RUN_ROOT" ]] || { echo "run root not found: $RUN_ROOT" >&2; exit 71; }
RUN_ROOT="$(cd "$RUN_ROOT" && pwd)"

cd "$ROOT"
mvn -B -ntp -DskipTests compile dependency:build-classpath -Dmdep.outputFile="$ROOT/target/reverify-classpath.txt" >/dev/null
CP="$ROOT/target/classes:$(cat "$ROOT/target/reverify-classpath.txt")"
java -cp "$CP" kr.co.oruda.onsure.assurance.LocalVerifyMain "$RUN_ROOT" "$ROOT"
