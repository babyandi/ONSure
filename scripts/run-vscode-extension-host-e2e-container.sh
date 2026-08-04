#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
image="onsure-vscode-extension-host-e2e:1.95.3-node22"
network_mode="bridge"

if [[ "${1:-}" == "--inside" ]]; then
  cd "$repo_root/vscode-extension"
  test "$(id -u)" -ne 0
  if [[ "${ONSURE_E2E_OFFLINE:-false}" == "true" ]]; then
    test -d node_modules/@vscode/test-electron
    test -d .vscode-test
  else
    npm ci --ignore-scripts --no-audit --no-fund
  fi
  exec npm run test:e2e
fi

if [[ "${1:-}" == "--offline" ]]; then
  network_mode="none"
fi

docker build --pull=false --tag "$image" --file \
  "$repo_root/vscode-extension/test/extension-host/Dockerfile" "$repo_root"
docker run --rm \
  --user "$(id -u):$(id -g)" \
  --env HOME=/tmp/onsure-home \
  --env "ONSURE_E2E_OFFLINE=$([[ "$network_mode" == "none" ]] && echo true || echo false)" \
  --network "$network_mode" \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  --pids-limit 512 \
  --memory 2g \
  --cpus 2 \
  --tmpfs /tmp:rw,noexec,nosuid,size=1g \
  --volume "$repo_root:/onsure-product:rw" \
  --workdir /onsure-product \
  "$image"
