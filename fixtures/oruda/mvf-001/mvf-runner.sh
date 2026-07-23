#!/usr/bin/env bash
set -euo pipefail

fixture_id="${1:-}"
case "$fixture_id" in
  MVF-POS-001|MVF-POS-002|MVF-POS-003|MVF-HANDOFF-001|MVF-HANDOFF-002|MVF-HANDOFF-003|MVF-EVID-001|MVF-EVID-002|MVF-EVID-003)
    printf '%s\n' 'EXPECTED_PASS'
    ;;
  MVF-NEG-001|MVF-NEG-002|MVF-NEG-003|MVF-NEG-004|MVF-NEG-005)
    printf '%s\n' 'EXPECTED_FAIL_CLOSED'
    ;;
  MVF-QUALITY-001|MVF-QUALITY-002|MVF-QUALITY-003)
    printf '%s\n' 'EXPECTED_FAIL_QUALITY_ORACLE'
    ;;
  *)
    printf 'UNKNOWN_MVF_FIXTURE:%s\n' "$fixture_id" >&2
    exit 64
    ;;
esac
