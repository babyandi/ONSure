#!/usr/bin/env python3
"""Extract atomic normative requirements and bind reviewed code/test/evidence mappings."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "contracts/atomic-requirements-extraction.v1.json"
OVERRIDES = ROOT / "contracts/atomic-requirement-overrides.v1.json"
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
LIST_ITEM = re.compile(r"^\s*(?:[-*+]\s+|\d+[.)]\s+)(.+?)\s*$")
BACKTICK = re.compile(r"`([^`]+)`")
JAVA_TYPE = re.compile(r"\b(?:class|interface|record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)")
JAVA_METHOD = re.compile(
    r"\b(?:public|protected|private|static|final|synchronized|abstract|native|default|\s)+"
    r"[A-Za-z0-9_<>,.?\[\] ]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*\("
)
PYTHON_DEF = re.compile(r"^\s*(?:async\s+)?def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", re.MULTILINE)


@dataclass(frozen=True)
class Symbol:
    identifier: str
    file: str
    line: int
    kind: str


def load(path: pathlib.Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def git_files() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False
    )
    if result.returncode:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        (ROOT / raw.decode("utf-8")).resolve()
        for raw in result.stdout.split(b"\0")
        if raw
    )


def rel(path: pathlib.Path) -> str:
    return path.relative_to(ROOT).as_posix()


def matches_glob(path: str, globs: list[str]) -> bool:
    candidate = pathlib.PurePosixPath(path)
    return any(candidate.match(pattern) for pattern in globs)


def normalize_text(value: str) -> str:
    value = re.sub(r"<!--.*?-->", "", value)
    value = re.sub(r"\s+", " ", value).strip()
    return value


def requirement_id(document: str, line: int, text: str) -> str:
    digest = hashlib.sha256(f"{document}|{line}|{text}".encode()).hexdigest()[:16]
    return f"REQ-{digest.upper()}"


def requirement_type(text: str, acceptance_context: bool) -> str:
    lowered = text.lower()
    if any(token in lowered for token in ("금지", "차단", "must not", "prohibited")):
        return "PROHIBITION"
    if acceptance_context:
        return "ACCEPTANCE_CRITERION"
    return "REQUIREMENT"


def capability_group(document: str, override_rules: list[dict[str, Any]]) -> str:
    for rule in override_rules:
        match = rule.get("match", {})
        if match.get("document") == document:
            return rule.get("capability_group", "UNCLASSIFIED")
        prefix = match.get("document_prefix")
        if prefix and document.startswith(prefix):
            return rule.get("capability_group", "UNCLASSIFIED")
    if document == "README.md":
        return "PRODUCT_BASELINE"
    if document.startswith("docs/architecture/"):
        return "ARCHITECTURE"
    if document.startswith("docs/verification/"):
        return "VERIFICATION"
    return "GENERAL_DESIGN"


def is_normative(text: str, markers: list[str], acceptance_context: bool) -> bool:
    lowered = text.lower()
    return acceptance_context or any(marker.lower() in lowered for marker in markers)


def extract_requirements(
    documents: list[pathlib.Path], config: dict[str, Any], rules: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    requirements: list[dict[str, Any]] = []
    markers = config["normative_markers"]
    acceptance_markers = [value.lower() for value in config["acceptance_section_markers"]]
    for document in documents:
        document_rel = rel(document)
        headings: list[tuple[int, str]] = []
        acceptance_level: int | None = None
        in_fence = False
        for line_number, raw in enumerate(
            document.read_text(encoding="utf-8", errors="strict").splitlines(), start=1
        ):
            stripped = raw.strip()
            if stripped.startswith("```") or stripped.startswith("~~~"):
                in_fence = not in_fence
                continue
            if in_fence or not stripped:
                continue
            heading = HEADING.match(stripped)
            if heading:
                level = len(heading.group(1))
                title = normalize_text(heading.group(2))
                headings = [item for item in headings if item[0] < level]
                headings.append((level, title))
                if any(marker in title.lower() for marker in acceptance_markers):
                    acceptance_level = level
                elif acceptance_level is not None and level <= acceptance_level:
                    acceptance_level = None
                continue

            item = LIST_ITEM.match(raw)
            text = normalize_text(item.group(1) if item else stripped)
            if len(text) < 4 or text.startswith(("http://", "https://")):
                continue
            acceptance_context = acceptance_level is not None
            if not is_normative(text, markers, acceptance_context):
                continue
            req_id = requirement_id(document_rel, line_number, text)
            requirements.append(
                {
                    "requirement_id": req_id,
                    "document": document_rel,
                    "line": line_number,
                    "heading_path": [title for _, title in headings],
                    "text": text,
                    "requirement_type": requirement_type(text, acceptance_context),
                    "capability_group": capability_group(document_rel, rules),
                    "implementation_status": config["default_implementation_status"],
                    "verification_status": config["default_verification_status"],
                    "code_symbols": [],
                    "test_symbols": [],
                    "evidence_refs": [],
                    "mapping_state": "UNMAPPED",
                    "review_state": "PENDING_REVIEW",
                    "source_digest": hashlib.sha256(text.encode()).hexdigest(),
                }
            )
    requirements.sort(key=lambda value: (value["document"], value["line"], value["requirement_id"]))
    return requirements


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def index_symbols(files: list[pathlib.Path]) -> dict[str, Symbol]:
    symbols: dict[str, Symbol] = {}
    for path in files:
        relative = rel(path)
        if relative.endswith(".java"):
            content = path.read_text(encoding="utf-8", errors="strict")
            package_match = re.search(r"^package\s+([A-Za-z0-9_.]+);", content, re.MULTILINE)
            package = package_match.group(1) if package_match else ""
            for match in JAVA_TYPE.finditer(content):
                name = match.group(1)
                identifier = f"{package}.{name}" if package else name
                symbols[identifier] = Symbol(identifier, relative, line_number(content, match.start()), "JAVA_TYPE")
            for match in JAVA_METHOD.finditer(content):
                name = match.group(1)
                if name in {"if", "for", "while", "switch", "catch", "return", "new"}:
                    continue
                identifier = f"{relative}#{name}"
                symbols[identifier] = Symbol(identifier, relative, line_number(content, match.start()), "JAVA_METHOD")
        elif relative.endswith(".py"):
            content = path.read_text(encoding="utf-8", errors="strict")
            for match in PYTHON_DEF.finditer(content):
                name = match.group(1)
                identifier = f"{relative}#{name}"
                kind = "PYTHON_TEST" if name.startswith("test_") else "PYTHON_FUNCTION"
                symbols[identifier] = Symbol(identifier, relative, line_number(content, match.start()), kind)
    return symbols


def apply_overrides(
    requirements: list[dict[str, Any]], overrides: dict[str, Any], symbols: dict[str, Symbol]
) -> None:
    explicit = overrides.get("requirement_overrides", {})
    for requirement in requirements:
        value = explicit.get(requirement["requirement_id"])
        if not value:
            continue
        for key in (
            "implementation_status",
            "verification_status",
            "evidence_refs",
            "mapping_state",
            "review_state",
        ):
            if key in value:
                requirement[key] = value[key]
        for target_key in ("code_symbols", "test_symbols"):
            resolved: list[dict[str, Any]] = []
            for identifier in value.get(target_key, []):
                symbol = symbols.get(identifier)
                if symbol is None:
                    raise ValueError(
                        f"UNKNOWN_SYMBOL_OVERRIDE:{requirement['requirement_id']}:{identifier}"
                    )
                resolved.append(
                    {
                        "symbol": symbol.identifier,
                        "file": symbol.file,
                        "line": symbol.line,
                        "kind": symbol.kind,
                    }
                )
            if resolved:
                requirement[target_key] = resolved
        if requirement["verification_status"] == "PASS" and not requirement["evidence_refs"]:
            raise ValueError(f"PASS_WITHOUT_EVIDENCE:{requirement['requirement_id']}")
        if requirement["implementation_status"] == "IMPLEMENTED" and not requirement["code_symbols"]:
            raise ValueError(f"IMPLEMENTED_WITHOUT_CODE_SYMBOL:{requirement['requirement_id']}")


def validate(requirements: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    ids: set[str] = set()
    for requirement in requirements:
        req_id = requirement["requirement_id"]
        if req_id in ids:
            errors.append(f"DUPLICATE_REQUIREMENT_ID:{req_id}")
        ids.add(req_id)
        if requirement["verification_status"] == "PASS" and not requirement["evidence_refs"]:
            errors.append(f"PASS_WITHOUT_EVIDENCE:{req_id}")
        if requirement["mapping_state"] == "MAPPED" and not (
            requirement["code_symbols"] or requirement["test_symbols"]
        ):
            errors.append(f"MAPPED_WITHOUT_SYMBOL:{req_id}")
    if not requirements:
        errors.append("ATOMIC_REQUIREMENT_SET_EMPTY")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    config = load(CONFIG)
    overrides = load(OVERRIDES)
    files = git_files()
    documents = [
        path
        for path in files
        if matches_glob(rel(path), config["document_globs"])
        and not matches_glob(rel(path), config["excluded_globs"])
    ]
    requirements = extract_requirements(documents, config, overrides.get("rules", []))
    symbols = index_symbols(files)
    apply_overrides(requirements, overrides, symbols)
    errors = validate(requirements)

    counts: dict[str, int] = {}
    for requirement in requirements:
        state = requirement["mapping_state"]
        counts[state] = counts.get(state, 0) + 1
    unmapped = counts.get("UNMAPPED", 0) + counts.get("PARTIALLY_MAPPED", 0)
    decision = "FAIL" if errors else ("BLOCKED" if unmapped else "PASS_NONFINAL")

    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    register = {
        "contract": "ONSURE_ATOMIC_REQUIREMENTS_REGISTER_V1",
        "source_contract": config["contract"],
        "coverage": "ALL_EXTRACTED_NORMATIVE_CLAUSES",
        "requirements": requirements,
        "errors": errors,
        "final_claim_allowed": False,
    }
    summary = {
        "contract": "ONSURE_ATOMIC_TRACEABILITY_SUMMARY_V1",
        "decision": decision,
        "document_count": len(documents),
        "requirement_count": len(requirements),
        "symbol_count": len(symbols),
        "mapping_counts": dict(sorted(counts.items())),
        "unmapped_or_partial_count": unmapped,
        "errors": errors,
        "runtime_verification": "NOT_RUN",
        "final_claim_allowed": False,
    }
    (output / "atomic-requirements.v1.json").write_text(
        json.dumps(register, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (output / "atomic-traceability-summary.v1.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_ATOMIC_TRACEABILITY_FAIL", file=sys.stderr)
        return 1
    if args.strict and unmapped:
        print("ONSURE_ATOMIC_TRACEABILITY_BLOCKED_UNMAPPED", file=sys.stderr)
        return 2
    print("ONSURE_ATOMIC_TRACEABILITY_BUILT_NONFINAL")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
