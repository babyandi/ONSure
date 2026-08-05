#!/usr/bin/env python3
"""Generate or validate the compiled ONSure public Java API descriptor baseline."""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
DEFAULT_CLASSES = ROOT / "target/classes"
DEFAULT_BASELINE = ROOT / "contracts/java-public-api-baseline.v1.json"


def class_names(classes: pathlib.Path) -> list[str]:
    if not classes.is_dir():
        raise ValueError("JAVA_API_CLASSES_NOT_BUILT")
    return sorted(
        path.relative_to(classes).with_suffix("").as_posix().replace("/", ".")
        for path in classes.rglob("*.class")
        if path.name not in {"module-info.class", "package-info.class"}
    )


def normalized_javap(output: str) -> list[str]:
    return [
        line.rstrip()
        for line in output.replace("\r\n", "\n").splitlines()
        if line.strip() and not line.startswith("Compiled from ")
    ]


def public_descriptors(classes: pathlib.Path, names: list[str]) -> dict[str, list[str]]:
    process = subprocess.run(
        ["javap", "-classpath", str(classes), "-public", "-s", *names],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if process.returncode != 0:
        raise ValueError(f"JAVAP_FAILED:{process.stderr.strip()}")
    descriptors: dict[str, list[str]] = {}
    for block in re.split(r"(?=Compiled from )", process.stdout):
        lines = normalized_javap(block)
        declaration = next(
            (line.strip() for line in lines if line.rstrip().endswith("{")), ""
        )
        if not declaration.startswith("public "):
            continue
        match = re.search(r"\b(?:class|interface)\s+(io\.onsure[\w.$]*)", declaration)
        if not match:
            raise ValueError(f"JAVAP_PUBLIC_DECLARATION_UNPARSEABLE:{declaration}")
        descriptors[match.group(1)] = lines
    return descriptors


def build_baseline(classes: pathlib.Path) -> dict[str, object]:
    names = class_names(classes)
    descriptors = public_descriptors(classes, names)
    return {
        "contract": "ONSURE_JAVA_PUBLIC_API_BASELINE_V1",
        "java_release": 17,
        "scope": "ALL_COMPILED_PUBLIC_CLASSES_AND_PUBLIC_BINARY_DESCRIPTORS",
        "class_count": len(descriptors),
        "classes": descriptors,
        "compatibility_rule": "EXACT_UNTIL_EXPLICIT_VERSIONED_BASELINE_UPDATE",
        "final_claim_allowed": False,
    }


def write_baseline(body: dict[str, object], output: pathlib.Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(body, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def validate(classes: pathlib.Path, baseline: pathlib.Path) -> dict[str, object]:
    if not baseline.is_file():
        raise ValueError("JAVA_API_BASELINE_MISSING")
    expected = json.loads(baseline.read_text(encoding="utf-8"))
    actual = build_baseline(classes)
    expected_classes = expected.get("classes", {})
    actual_classes = actual["classes"]
    removed = sorted(set(expected_classes) - set(actual_classes))
    added = sorted(set(actual_classes) - set(expected_classes))
    changed = sorted(
        name
        for name in set(expected_classes) & set(actual_classes)
        if expected_classes[name] != actual_classes[name]
    )
    violations = []
    if removed:
        violations.append("PUBLIC_CLASSES_REMOVED")
    if added:
        violations.append("PUBLIC_CLASSES_ADDED_WITHOUT_BASELINE_UPDATE")
    if changed:
        violations.append("PUBLIC_BINARY_DESCRIPTORS_CHANGED")
    return {
        "contract": "ONSURE_JAVA_PUBLIC_API_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "expected_public_class_count": len(expected_classes),
        "actual_public_class_count": len(actual_classes),
        "removed_classes": removed,
        "added_classes": added,
        "changed_classes": changed,
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("generate", "validate"))
    parser.add_argument("--classes", type=pathlib.Path, default=DEFAULT_CLASSES)
    parser.add_argument("--baseline", type=pathlib.Path, default=DEFAULT_BASELINE)
    args = parser.parse_args(argv)
    classes = args.classes if args.classes.is_absolute() else ROOT / args.classes
    baseline = args.baseline if args.baseline.is_absolute() else ROOT / args.baseline
    if args.mode == "generate":
        body = build_baseline(classes)
        write_baseline(body, baseline)
        print(
            f"ONSURE_JAVA_API_BASELINE_GENERATED classes={body['class_count']} "
            f"output={baseline.relative_to(ROOT)}"
        )
        return 0
    result = validate(classes, baseline)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ONSURE_JAVA_API_BASELINE_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
