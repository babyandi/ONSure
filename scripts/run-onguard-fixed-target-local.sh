#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---core}"
MANIFEST="$ROOT/fixtures/external/onguard-f9615d10.manifest.json"
ARCHIVE="$ROOT/fixtures/external/onguard-f9615d10.tar.gz"
EXPECTED_SHA256="b3001d512dc3192bbbd288ee4a0087691d23d2ec800d565b33816311c0d6c878"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
OUT="$ROOT/receipts/manual/onguard-fixed-target/$RUN_ID"
WORK="$(mktemp -d)"
COMPOSE_STARTED=false

cleanup() {
  if [[ "$COMPOSE_STARTED" == true ]]; then
    docker compose -f "$WORK/onguard-target/.devcontainer/docker-compose.yml" down -v
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

case "$MODE" in
  --core|--full) ;;
  *)
    echo "usage: $0 [--core|--full]" >&2
    exit 64
    ;;
esac

mkdir -p "$OUT"
printf '%s  %s\n' "$EXPECTED_SHA256" "$ARCHIVE" | sha256sum -c -
cp "$MANIFEST" "$OUT/target-manifest.json"
tar -xzf "$ARCHIVE" -C "$WORK"
TARGET="$WORK/onguard-target"

(
  cd "$TARGET"
  export PYTHONPATH=src
  python -m unittest discover -s tests -v
  python tools/validate_rule_traceability.py
  python tools/check_extended_good_code_rules.py
  python tools/run_good_code_campaign.py
  cp artifacts/good-code-campaign-receipt.json "$WORK/good-code-campaign-run-1.json"
  python tools/run_good_code_campaign.py
  python tools/verify_good_code_reproducibility.py \
    "$WORK/good-code-campaign-run-1.json" \
    artifacts/good-code-campaign-receipt.json
  python tools/verify_good_code_receipt.py artifacts/good-code-campaign-receipt.json
  python tools/rule_execution_engine.py
  python tools/run_internal_separated_e2e.py
  cp artifacts/good-code-campaign-receipt.json "$OUT/"
  cp artifacts/good-code-295-campaign-receipt.json "$OUT/"
  cp artifacts/internal-separated-e2e-receipt.json "$OUT/"
) | tee "$OUT/core.log"

POSTGRES_STATUS=NOT_RUN
NETWORK_STATUS=NOT_RUN

if [[ "$MODE" == "--full" ]]; then
  for command in docker pg_isready; do
    command -v "$command" >/dev/null || {
      echo "MANUAL_FIXED_TARGET_HOLD missing_command=$command" >&2
      exit 69
    }
  done
  : "${ONGUARD_PG_HOST:?ONGUARD_PG_HOST is required}"
  : "${ONGUARD_PG_PORT:?ONGUARD_PG_PORT is required}"
  : "${ONGUARD_PG_DB:?ONGUARD_PG_DB is required}"
  : "${ONGUARD_PG_USER:?ONGUARD_PG_USER is required}"
  : "${ONGUARD_PG_PASSWORD:?ONGUARD_PG_PASSWORD is required}"

  (
    cd "$TARGET"
    python - <<'PY'
import os
import psycopg2

connection = psycopg2.connect(
    host=os.environ["ONGUARD_PG_HOST"],
    port=os.environ["ONGUARD_PG_PORT"],
    dbname=os.environ["ONGUARD_PG_DB"],
    user=os.environ["ONGUARD_PG_USER"],
    password=os.environ["ONGUARD_PG_PASSWORD"],
)
with connection:
    with connection.cursor() as cursor:
        cursor.execute(open("db/onguard_schema.sql", encoding="utf-8").read())
connection.close()
PY
    PYTHONPATH=src python -m unittest discover -s tests_postgres -v
  ) | tee "$OUT/postgres.log"
  POSTGRES_STATUS=PASS

  docker compose -f "$TARGET/.devcontainer/docker-compose.yml" up -d --wait sandbox-probe
  COMPOSE_STARTED=true
  if docker compose -f "$TARGET/.devcontainer/docker-compose.yml" \
    exec -T sandbox-probe \
    python -c 'import socket; socket.create_connection(("1.1.1.1", 53), 3)'; then
    echo "MANUAL_FIXED_TARGET_FAIL external_egress_unexpectedly_succeeded" >&2
    exit 1
  fi
  docker network inspect onguard-net --format '{{json .Internal}}' | grep -qx true
  NETWORK_STATUS=PASS
fi

cat > "$OUT/result.txt" <<EOF
contract=ONSURE_MANUAL_ONGUARD_FIXED_TARGET_V1
execution_mode=$MODE
source_commit=f9615d10411249a347e107ffd992b803a828ce42
archive_sha256=$EXPECTED_SHA256
core_and_rule_harness=PASS
postgres_append_only=$POSTGRES_STATUS
network_egress_isolation=$NETWORK_STATUS
domain_detector_implemented=0
domain_detector_pending=295
independent_otester=NOT_RUN
independent_oaudit=NOT_RUN
assurance_class=SELF_VALIDATION_NONFINAL
final_lock_allowed=false
EOF

(
  cd "$OUT"
  find . -maxdepth 1 -type f ! -name evidence.sha256 -print0 \
    | sort -z \
    | xargs -0 sha256sum > evidence.sha256
)

if [[ "$MODE" == "--full" ]]; then
  printf 'MANUAL_FIXED_TARGET_FULL_PASS %s\n' "$OUT"
else
  printf 'MANUAL_FIXED_TARGET_CORE_PASS_INFRA_NOT_RUN_HOLD %s\n' "$OUT"
fi
