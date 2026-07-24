#!/usr/bin/env python3
"""Run ONSure cause-aware verification from JSON input."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from onsure_core.cause_aware_verification import (
    build_sample_oruda_report_profile,
    build_sample_run,
    digest,
    verify_program_run,
)


def _load_json(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def _stable_projection(result: dict) -> dict:
    return {
        "decision": result["decision"],
        "finding_codes": [item["code"] for item in result["findings"]],
        "remediation_targets": result["remediation_targets"],
        "memory_evidence_hashes": [item["evidence_hash"] for item in result["memory_candidates"]],
    }


def _verify_loop(profile: dict, run: dict, loop_count: int) -> dict:
    if loop_count < 1:
        raise ValueError("loop_count must be >= 1")

    iterations = []
    baseline_projection = None
    stable = True
    for index in range(loop_count):
        result = verify_program_run(profile, run)
        projection = _stable_projection(result)
        if baseline_projection is None:
            baseline_projection = projection
        elif projection != baseline_projection:
            stable = False
        iterations.append(
            {
                "iteration": index + 1,
                "decision": result["decision"],
                "finding_count": result["finding_count"],
                "result_hash": digest(projection),
            }
        )

    final_result = verify_program_run(profile, run)
    final_result["loop"] = {
        "requested_iterations": loop_count,
        "stable": stable,
        "iterations": iterations,
        "stable_projection_hash": digest(baseline_projection),
    }
    return final_result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", help="Target program profile JSON")
    parser.add_argument("--run", help="Target program run receipt JSON")
    parser.add_argument("--loop", type=int, default=1, help="Repeat verification and require stable cause-aware output.")
    parser.add_argument(
        "--sample-oruda",
        action="store_true",
        help="Verify the built-in ORUDA report-chain sample.",
    )
    args = parser.parse_args()

    if args.sample_oruda:
        profile = build_sample_oruda_report_profile()
        run = build_sample_run()
    elif args.profile and args.run:
        profile = _load_json(args.profile)
        run = _load_json(args.run)
    else:
        parser.error("provide --sample-oruda or both --profile and --run")

    result = _verify_loop(profile, run, args.loop)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["decision"] == "ALLOW" and result["loop"]["stable"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
