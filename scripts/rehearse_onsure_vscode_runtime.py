#!/usr/bin/env python3
"""Probe the exact content-free Ubuntu runtime surfaces consumed by the VS Code extension."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import urllib.error
import urllib.parse
import urllib.request
from typing import Iterable


def loopback_base(value: str) -> str:
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"} \
            or parsed.port is None or parsed.path not in {"", "/"} \
            or parsed.username is not None or parsed.password is not None:
        raise ValueError("VSCODE_RUNTIME_LOOPBACK_URL_REQUIRED")
    return value.rstrip("/")


def get_json(base: str, route: str, token: str) -> dict[str, object]:
    if len(token) < 32:
        raise ValueError("VSCODE_RUNTIME_TOKEN_TOO_SHORT")
    request = urllib.request.Request(
        loopback_base(base) + route,
        headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            if response.status != 200:
                raise ValueError("VSCODE_RUNTIME_HTTP_STATUS")
            return json.loads(response.read(1_000_001))
    except urllib.error.URLError as error:
        raise ValueError("VSCODE_RUNTIME_CONNECTION_FAILED") from error


def validate(
    local_health: dict[str, object],
    gateway_health: dict[str, object],
    metrics: dict[str, object],
) -> dict[str, object]:
    errors: list[str] = []
    if local_health.get("contract") != "ONSURE_LOCAL_AUTHENTICATED_API_V1" \
            or local_health.get("state") != "RUNNING":
        errors.append("LOCAL_API_NOT_RUNNING")
    if gateway_health.get("contract") != "ONSURE_LLM_GATEWAY_V1" \
            or gateway_health.get("state") != "RUNNING" \
            or gateway_health.get("provider_health") != "READY":
        errors.append("LLM_GATEWAY_NOT_READY")
    if metrics.get("chain_valid") is not True:
        errors.append("LLM_RECEIPT_CHAIN_INVALID")
    if metrics.get("prompt_or_completion_content_recorded") is not False:
        errors.append("LLM_CONTENT_STORAGE_BOUNDARY_VIOLATION")
    for field in (
        "request_count", "success_count", "failure_count", "total_tokens",
        "actual_cost_micros", "last_sequence",
    ):
        value = metrics.get(field)
        if not isinstance(value, int) or value < 0:
            errors.append("LLM_METRIC_INVALID:" + field)
    return {
        "contract": "ONSURE_VSCODE_UBUNTU_RUNTIME_REHEARSAL_V1",
        "decision": "PASS_NONFINAL" if not errors else "FAIL",
        "errors": errors,
        "local_api_state": local_health.get("state", "UNKNOWN"),
        "llm_gateway_state": gateway_health.get("state", "UNKNOWN"),
        "llm_provider": gateway_health.get("provider", "UNKNOWN"),
        "request_count": metrics.get("request_count", 0),
        "total_tokens": metrics.get("total_tokens", 0),
        "actual_cost_micros": metrics.get("actual_cost_micros", 0),
        "last_sequence": metrics.get("last_sequence", 0),
        "chain_valid": metrics.get("chain_valid", False),
        "content_recorded": metrics.get("prompt_or_completion_content_recorded", True),
        "tokens_disclosed": False,
        "source_mutation": False,
        "production_acceptance": "NOT_RUN",
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--local-api", default="http://127.0.0.1:47311")
    parser.add_argument("--gateway", default="http://127.0.0.1:47312")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args(argv)
    local_token = os.environ.get("ONSURE_LOCAL_API_TOKEN", "")
    gateway_token = os.environ.get("ONSURE_LLM_GATEWAY_TOKEN", "")
    result = validate(
        get_json(args.local_api, "/v1/health", local_token),
        get_json(args.gateway, "/v1/health", gateway_token),
        get_json(args.gateway, "/v1/metrics", gateway_token),
    )
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, json.JSONDecodeError) as error:
        print("ONSURE_VSCODE_RUNTIME_REHEARSAL_FAIL " + str(error))
        raise SystemExit(1)
