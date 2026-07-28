#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import pathlib

_IMPLEMENTATION = pathlib.Path(__file__).with_name("validate_verification_claims_v2.py")
_SPEC = importlib.util.spec_from_file_location("onsure_validate_verification_claims_v2", _IMPLEMENTATION)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("VERIFICATION_CLAIM_IMPLEMENTATION_LOAD_FAILED")
_MODULE = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(_MODULE)

ROOT = _MODULE.ROOT
COUNT_AUTHORITY = _MODULE.COUNT_AUTHORITY
validate = _MODULE.validate
main = _MODULE.main


if __name__ == "__main__":
    raise SystemExit(main())
