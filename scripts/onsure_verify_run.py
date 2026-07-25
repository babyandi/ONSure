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
    verify_program_run_loop,
)


def _load_json(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


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

    result = verify_program_run_loop(profile, run, args.loop)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["decision"] == "ALLOW" and result["loop"]["stable"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
