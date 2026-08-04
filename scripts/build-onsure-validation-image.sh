#!/usr/bin/env bash
set -euo pipefail
PATH='/usr/sbin:/usr/bin:/sbin:/bin'
export PATH

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
BASE_REFERENCE="${ONSURE_VALIDATION_BASE_IMAGE:-onsure-goal-validator:node20}"
OUTPUT_REFERENCE="${ONSURE_VALIDATION_OCI_IMAGE:-onsure-validation-runtime:java17-node20-v1}"

for command_name in docker awk; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "ONSURE_VALIDATION_IMAGE_BUILD_FAIL MISSING_COMMAND_$command_name" >&2
    exit 69
  }
done

BASE_ID="$(docker image inspect --format '{{.Id}}' "$BASE_REFERENCE" 2>/dev/null || true)"
[[ "$BASE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || {
  echo 'ONSURE_VALIDATION_IMAGE_BUILD_FAIL BASE_IMAGE_NOT_LOCAL_IMMUTABLE' >&2
  exit 69
}
BASE_TAG="onsure-validation-base-local:${BASE_ID#sha256:}"
docker tag "$BASE_ID" "$BASE_TAG"
cleanup() {
  docker image rm "$BASE_TAG" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker build --pull=false --network=default \
  --build-arg "BASE_IMAGE=$BASE_TAG" \
  --tag "$OUTPUT_REFERENCE" \
  --file "$ROOT/deploy/validation/Dockerfile" \
  "$ROOT/deploy/validation"

OUTPUT_ID="$(docker image inspect --format '{{.Id}}' "$OUTPUT_REFERENCE")"
[[ "$OUTPUT_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || {
  echo 'ONSURE_VALIDATION_IMAGE_BUILD_FAIL OUTPUT_IMAGE_NOT_IMMUTABLE' >&2
  exit 70
}
docker run --rm --pull never --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges:true --entrypoint python3 "$OUTPUT_ID" \
  -c 'import jsonschema, yaml'
printf 'ONSURE_VALIDATION_IMAGE_BUILD_PASS %s %s\n' "$OUTPUT_REFERENCE" "$OUTPUT_ID"
