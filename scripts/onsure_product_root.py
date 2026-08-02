#!/usr/bin/env python3
"""Resolve the ONSure product root without depending on an enclosing workspace."""

from __future__ import annotations

import os
import pathlib


PRODUCT_ROOT_ENV = "ONSURE_PRODUCT_ROOT"
DEFAULT_PRODUCT_ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIRED_MARKERS = ("pom.xml", "contracts")


def resolve_product_root(explicit: str | pathlib.Path | None = None) -> pathlib.Path:
    raw = explicit if explicit is not None else os.environ.get(PRODUCT_ROOT_ENV)
    if raw is None:
        root = DEFAULT_PRODUCT_ROOT
    else:
        candidate = pathlib.Path(raw).expanduser()
        if not candidate.is_absolute():
            raise ValueError("ONSURE_PRODUCT_ROOT_MUST_BE_ABSOLUTE")
        root = candidate
    resolved = root.resolve()
    missing = [marker for marker in REQUIRED_MARKERS if not (resolved / marker).exists()]
    if missing:
        raise ValueError("ONSURE_PRODUCT_ROOT_MARKERS_MISSING:" + ",".join(missing))
    return resolved


def product_path(root: pathlib.Path, relative: str | pathlib.Path) -> pathlib.Path:
    candidate = (root / relative).resolve()
    if candidate != root and root not in candidate.parents:
        raise ValueError("ONSURE_PRODUCT_PATH_ESCAPE")
    return candidate
