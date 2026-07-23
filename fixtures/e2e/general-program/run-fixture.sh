#!/usr/bin/env bash
set -euo pipefail
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" src/SampleService.java
java -cp "$TMP" sample.SampleService "${1:-}"
