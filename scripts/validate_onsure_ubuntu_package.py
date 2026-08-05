#!/usr/bin/env python3
"""Validate and record the Ubuntu standalone package candidate."""

from __future__ import annotations

import sys
import tarfile
import zipfile

from validate_onsure_rhel_package import run


if __name__ == "__main__":
    try:
        raise SystemExit(run("ubuntu"))
    except (OSError, ValueError, tarfile.TarError, zipfile.BadZipFile) as error:
        print("ONSURE_UBUNTU_PACKAGE_VALIDATION_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
