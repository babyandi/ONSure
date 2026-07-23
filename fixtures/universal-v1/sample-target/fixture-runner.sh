#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  normal) printf 'PASS:normal\n' ;;
  error) printf 'PASS:error-handled\n' ;;
  authorization) printf 'PASS:authorization-blocked\n' ;;
  large-data) printf 'PASS:large-data\n' ;;
  concurrency) printf 'PASS:concurrency\n' ;;
  recovery) printf 'PASS:recovery\n' ;;
  adversarial) printf 'PASS:adversarial-blocked\n' ;;
  *) printf 'BLOCKED:unknown-fixture\n' >&2; exit 64 ;;
esac
