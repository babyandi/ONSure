#!/usr/bin/env python3
"""Record Ubuntu-host offline security analysis for the shared systemd units."""

from __future__ import annotations

import sys

from onsure_systemd_security import run


if __name__ == "__main__":
    try:
        raise SystemExit(run("ubuntu"))
    except (OSError, ValueError) as error:
        print("ONSURE_UBUNTU_SYSTEMD_SECURITY_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
