#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
NORMATIVE = re.compile(
    r"(?:해야 합니다|해야 한다|금지|필수|수용 기준|MUST|SHALL|REQUIRED|PROHIBITED)",
    re.IGNORECASE,
)


def tracked_markdown() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", "README.md", "docs/*.md", "docs/**/*.md"],
        cwd=ROOT,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(ROOT / item.decode("utf-8") for item in result.stdout.split(b"\0") if item)


def classify(path: str, text: str) -> str:
    value = (path + " " + text).lower()
    if "security" in value or "보안" in value or "권한" in value:
        return "SECURITY"
    if "test" in value or "시험" in value or "수용" in value:
        return "TEST_ACCEPTANCE"
    if "ui" in value or "ux" in value or "화면" in value:
        return "UI_UX"
    if "license" in value or "commerce" in value or "가격" in value or "결제" in value:
        return "COMMERCIAL"
    if "운영" in value or "배포" in value or "복구" in value:
        return "OPERATIONS"
    if "policy" in value or "승인" in value or "감사" in value:
        return "GOVERNANCE"
    return "FUNCTIONAL"


def requirement_id(relative: str, line: int, text: str) -> str:
    token = hashlib.sha256(f"{relative}:{line}:{text}".encode()).hexdigest()[:12].upper()
    return f"ONS-AUTO-{token}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()

    records: list[dict[str, object]] = []
    for document in tracked_markdown():
        relative = document.relative_to(ROOT).as_posix()
        heading = "ROOT"
        for line_number, raw in enumerate(document.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            stripped = raw.strip()
            if stripped.startswith("#"):
                heading = stripped.lstrip("#").strip() or heading
                continue
            if not stripped or not NORMATIVE.search(stripped):
                continue
            text = re.sub(r"^[-*+\d.)\s]+", "", stripped).strip()
            if len(text) < 8:
                continue
            records.append({
                "requirement_id": requirement_id(relative, line_number, text),
                "source_document": relative,
                "source_locator": f"{heading}#L{line_number}",
                "normative_text": text,
                "requirement_type": classify(relative, text),
                "priority": "P1",
                "acceptance_criteria": [{
                    "criterion_id": "AC-1",
                    "text": "Authoritative implementation, focused test, regression test and source-bound evidence are present.",
                    "oracle": "TRACEABILITY_COMPLETE_AND_EVIDENCE_BOUND",
                    "required_evidence_types": ["SOURCE", "TEST", "REGRESSION"]
                }],
                "design_refs": [f"{relative}:{line_number}"],
                "contract_refs": [],
                "code_symbols": [],
                "test_methods": [],
                "evidence_refs": [],
                "implementation_status": "DESIGN_ONLY",
                "verification_status": "NOT_RUN",
                "notes": ["AUTO_EXTRACTED_CANDIDATE_REQUIRES_HUMAN_OR_POLICY_RECONCILIATION"]
            })

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    body = {
        "contract": "ONSURE_ATOMIC_REQUIREMENT_CANDIDATE_SET_V1",
        "source_scope": "TRACKED_AUTHORITATIVE_MARKDOWN",
        "candidate_count": len(records),
        "records": records,
        "promotion_allowed": False,
        "verification_status": "NOT_RUN"
    }
    output.write_text(json.dumps(body, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"ONSURE_ATOMIC_REQUIREMENTS_EXTRACTED {len(records)} {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"ONSURE_ATOMIC_REQUIREMENTS_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
