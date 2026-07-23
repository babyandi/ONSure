#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ $# -ge 2 && $# -le 3 ]] || {
  echo "usage: materialize-oruda-documents.sh <source-directory> <output-directory> [catalog-json]" >&2
  exit 64
}
exec bash "$ROOT/scripts/run-oruda-cli.sh" \
  io.onsure.platform.oruda.OrudaDocumentMaterializerMain "$@"
