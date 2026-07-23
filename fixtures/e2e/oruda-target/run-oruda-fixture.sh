#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  runtime-emits-oaudit-pass) printf 'BLOCK\n' ;;
  producer-approves-own-artifact) printf 'ALLOW\n' ;;
  *) printf 'BLOCK\n' ;;
esac
