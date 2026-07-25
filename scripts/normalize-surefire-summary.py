#!/usr/bin/env python3
from __future__ import annotations

import re
import sys


ELAPSED = re.compile(r"(Time elapsed:\s*)[0-9]+(?:\.[0-9]+)?(\s*s)")


def normalize(text: str) -> str:
    """Surefire의 비결정적 실행시간만 제거하고 판정 필드는 그대로 보존한다."""

    return ELAPSED.sub(r"\1<elapsed>\2", text)


def main() -> int:
    sys.stdout.write(normalize(sys.stdin.read()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
