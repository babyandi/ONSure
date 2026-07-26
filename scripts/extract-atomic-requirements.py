#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
from collections import defaultdict
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
NORMATIVE = re.compile(
    r"(?:해야 합니다|해야 한다|하여야|금지|필수|수용 기준|완료 조건|MUST|SHALL|REQUIRED|PROHIBITED|MUST NOT)",
    re.IGNORECASE,
)
BACKTICK = re.compile(r"`([^`]+)`")
JAVA_PACKAGE = re.compile(r"^package\s+([A-Za-z0-9_.]+);", re.MULTILINE)
JAVA_TYPE = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_$][A-Za-z0-9_$]*)")
JAVA_METHOD = re.compile(
    r"^(?:\s*)(?:public|protected|private|static|final|synchronized|abstract|default|native|strictfp|\s)+"
    r"[A-Za-z0-9_$.<>?,\[\]\s]+\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(",
    re.MULTILINE,
)
PYTHON_TYPE = re.compile(r"^(?:class|def|async\s+def)\s+([A-Za-z_][A-Za-z0-9_]*)", re.MULTILINE)
SHELL_FUNCTION = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)\s*\(\)\s*\{", re.MULTILINE)


def git_tracked() -> list[pathlib.Path]:
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(ROOT / item.decode("utf-8") for item in result.stdout.split(b"\0") if item)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: pathlib.Path) -> str:
    return sha256_bytes(path.read_bytes())


def classify(path: str, text: str) -> str:
    value = (path + " " + text).lower()
    if any(token in value for token in ("security", "보안", "권한", "secret", "인증")):
        return "SECURITY"
    if any(token in value for token in ("test", "시험", "수용", "검증", "oracle", "fixture")):
        return "TEST_ACCEPTANCE"
    if any(token in value for token in ("ui", "ux", "화면", "접근성", "vscode")):
        return "UI_UX"
    if any(token in value for token in ("license", "commerce", "가격", "결제", "olicense")):
        return "COMMERCIAL"
    if any(token in value for token in ("운영", "배포", "복구", "retention", "sla")):
        return "OPERATIONS"
    if any(token in value for token in ("policy", "승인", "감사", "책임", "분리")):
        return "GOVERNANCE"
    if any(token in value for token in ("성능", "가용성", "확장", "비기능")):
        return "NON_FUNCTIONAL"
    return "FUNCTIONAL"


def priority(text: str) -> str:
    upper = text.upper()
    for value in ("P0", "P1", "P2", "P3"):
        if value in upper:
            return value
    if any(token in text for token in ("금지", "반드시", "필수", "MUST", "SHALL")):
        return "P0"
    return "P1"


def normalized_requirement(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^0-9A-Za-z가-힣]+", " ", text)).strip().lower()


def requirement_id(relative: str, line: int, text: str) -> str:
    token = sha256_bytes(f"{relative}:{line}:{normalized_requirement(text)}".encode())[:16].upper()
    return f"ONS-ATOM-{token}"


def symbol_index(files: list[pathlib.Path]) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    symbols: dict[str, list[str]] = defaultdict(list)
    tests: dict[str, list[str]] = defaultdict(list)
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        if path.suffix not in {".java", ".py", ".sh"}:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if path.suffix == ".java":
            package_match = JAVA_PACKAGE.search(text)
            package = package_match.group(1) if package_match else ""
            type_names = JAVA_TYPE.findall(text)
            owner = type_names[0] if type_names else path.stem
            for name in type_names:
                fq = f"{package}.{name}" if package else name
                symbols[name].append(f"{relative}::{fq}")
            test_lines = set()
            lines = text.splitlines()
            for index, line in enumerate(lines):
                if "@Test" in line:
                    test_lines.update(range(index, min(index + 5, len(lines))))
            for match in JAVA_METHOD.finditer(text):
                name = match.group(1)
                line = text.count("\n", 0, match.start())
                target = f"{relative}::{package}.{owner}#{name}" if package else f"{relative}::{owner}#{name}"
                symbols[name].append(target)
                if line in test_lines or relative.startswith("src/test/") or "/src/test/" in relative:
                    tests[name].append(target)
        elif path.suffix == ".py":
            for name in PYTHON_TYPE.findall(text):
                target = f"{relative}::{name}"
                symbols[name].append(target)
                if relative.startswith("tests/") or name.startswith("test_"):
                    tests[name].append(target)
        else:
            for name in SHELL_FUNCTION.findall(text):
                symbols[name].append(f"{relative}::{name}")
    return dict(symbols), dict(tests)


def explicit_refs(text: str, tracked: set[str], symbols: dict[str, list[str]], tests: dict[str, list[str]]) -> tuple[list[str], list[str], list[str]]:
    contract_refs: set[str] = set()
    code_symbols: set[str] = set()
    test_methods: set[str] = set()
    for token in BACKTICK.findall(text):
        clean = token.strip().split("#", 1)[0]
        if clean in tracked:
            if clean.startswith(("contracts/", "schemas/")) or clean.endswith((".json", ".yaml", ".yml")):
                contract_refs.add(clean)
        simple = token.rsplit(".", 1)[-1].split("#", 1)[-1].split("(", 1)[0]
        for value in symbols.get(simple, []):
            code_symbols.add(value)
        for value in tests.get(simple, []):
            test_methods.add(value)
    return sorted(contract_refs), sorted(code_symbols), sorted(test_methods)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()

    tracked_files = git_tracked()
    tracked_relative = {path.relative_to(ROOT).as_posix() for path in tracked_files}
    documents = [path for path in tracked_files if path.suffix.lower() == ".md"]
    symbols, tests = symbol_index(tracked_files)

    records: list[dict[str, Any]] = []
    document_counts: dict[str, int] = {}
    duplicate_groups: dict[str, list[str]] = defaultdict(list)
    for document in documents:
        relative = document.relative_to(ROOT).as_posix()
        heading = "ROOT"
        count = 0
        document_sha = sha256_file(document)
        for line_number, raw in enumerate(document.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            stripped = raw.strip()
            if stripped.startswith("#"):
                heading = stripped.lstrip("#").strip() or heading
                continue
            if not stripped or not NORMATIVE.search(stripped):
                continue
            text = re.sub(r"^[-*+|\d.)\s]+", "", stripped).strip().strip("|").strip()
            if len(text) < 8:
                continue
            req_id = requirement_id(relative, line_number, text)
            norm = normalized_requirement(text)
            duplicate_groups[norm].append(req_id)
            contracts, code_symbols, test_methods = explicit_refs(
                text, tracked_relative, symbols, tests
            )
            records.append({
                "requirement_id": req_id,
                "source_document": relative,
                "source_document_sha256": document_sha,
                "source_locator": {"heading": heading, "line": line_number},
                "source_line_sha256": sha256_bytes(raw.encode()),
                "normative_text": text,
                "normalized_text_sha256": sha256_bytes(norm.encode()),
                "requirement_type": classify(relative, text),
                "priority": priority(text),
                "acceptance_criteria": [{
                    "criterion_id": f"{req_id}-AC-1",
                    "text": f"Prove this normative statement with a focused oracle and source-bound evidence: {text}",
                    "oracle": "REQUIREMENT_SPECIFIC_ORACLE_PENDING",
                    "required_evidence_types": ["SOURCE", "TEST", "REGRESSION"]
                }],
                "design_refs": [f"{relative}:L{line_number}"],
                "contract_refs": contracts,
                "code_symbols": code_symbols,
                "test_methods": test_methods,
                "evidence_refs": [],
                "implementation_status": "DESIGN_ONLY" if not code_symbols else "PARTIAL",
                "verification_status": "NOT_RUN",
                "notes": ["AUTO_EXTRACTED_CANDIDATE_REQUIRES_RECONCILIATION"]
            })
            count += 1
        document_counts[relative] = count

    duplicate_map = {
        sha256_bytes(key.encode())[:16]: values
        for key, values in duplicate_groups.items() if len(values) > 1
    }
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    body = {
        "contract": "ONSURE_ATOMIC_REQUIREMENT_CANDIDATE_SET_V2",
        "source_scope": "ALL_GIT_TRACKED_MARKDOWN",
        "candidate_count": len(records),
        "document_count": len(documents),
        "document_candidate_counts": document_counts,
        "documents_without_normative_candidates": sorted(
            key for key, value in document_counts.items() if value == 0
        ),
        "symbol_index": {
            "code_symbol_name_count": len(symbols),
            "test_symbol_name_count": len(tests),
        },
        "duplicate_groups": duplicate_map,
        "records": records,
        "promotion_allowed": False,
        "implementation_status": "CANDIDATE_EXTRACTION_IMPLEMENTED",
        "verification_status": "NOT_RUN",
        "limitations": [
            "NATURAL_LANGUAGE_EXTRACTION_REQUIRES_RECONCILIATION",
            "IMPLICIT_REQUIREMENTS_WITHOUT_NORMATIVE_TOKENS_MAY_BE_MISSED",
            "CODE_AND_TEST_LINKS_REQUIRE_EXPLICIT_BACKTICK_SYMBOLS_OR_LATER_RECONCILIATION",
        ],
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
