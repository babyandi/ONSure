#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from onsure_core.design_baseline_runtime import verify_design_baseline


def main() -> int:
    try:
        receipt = verify_design_baseline(ROOT)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"ONSURE_DESIGN_BASELINE_RUNTIME_BLOCKED {error}", file=sys.stderr)
        return 69
    print(json.dumps(receipt, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

