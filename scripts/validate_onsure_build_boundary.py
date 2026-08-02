#!/usr/bin/env python3
"""Validate ONSure build authority, logical source ownership and dependency direction."""

from __future__ import annotations

import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
CONTRACT_PATH = ROOT / "contracts/onsure-build-boundary.v1.json"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def text(root: ET.Element, xpath: str) -> str | None:
    node = root.find(xpath, NS)
    return node.text.strip() if node is not None and node.text else None


def texts(root: ET.Element, xpath: str) -> list[str]:
    return [
        node.text.strip()
        for node in root.findall(xpath, NS)
        if node.text and node.text.strip()
    ]


def matches(pattern: str, relative: str) -> bool:
    if pattern.endswith("/**"):
        return relative.startswith(pattern[:-2])
    return relative == pattern


def package_name(source: pathlib.Path) -> str:
    value = source.read_text(encoding="utf-8")
    match = re.search(r"^package\s+([\w.]+);", value, re.MULTILINE)
    if not match:
        raise ValueError(f"JAVA_PACKAGE_MISSING:{source.relative_to(ROOT)}")
    return match.group(1)


def imports(source: pathlib.Path) -> set[str]:
    value = source.read_text(encoding="utf-8")
    return set(re.findall(r"^import\s+([\w.]+);", value, re.MULTILINE))


def module_state(contract: dict[str, object]) -> tuple[dict[str, set[str]], dict[str, dict[str, object]]]:
    locations = contract["module_artifacts"]
    artifact_to_module = {artifact: artifact for artifact in locations}
    dependency_graph: dict[str, set[str]] = {}
    descriptors: dict[str, dict[str, object]] = {}
    for artifact, location in locations.items():
        pom = ROOT / location / "pom.xml"
        body = ET.parse(pom).getroot()
        actual_artifact = text(body, "m:artifactId")
        if actual_artifact != artifact:
            raise ValueError(f"MODULE_ARTIFACT_DRIFT:{location}:{actual_artifact}")
        internal = {
            dependency
            for dependency in texts(body, "m:dependencies/m:dependency/m:artifactId")
            if dependency in artifact_to_module
        }
        dependency_graph[artifact] = internal
        sources = texts(body, ".//m:sources/m:source")
        descriptors[artifact] = {
            "shared_source": any("../../src/main/java" in source for source in sources),
            "includes": texts(body, ".//m:includes/m:include"),
            "excludes": texts(body, ".//m:excludes/m:exclude"),
        }
    return dependency_graph, descriptors


def graph_cycles(graph: dict[str, set[str]]) -> list[list[str]]:
    cycles: set[tuple[str, ...]] = set()

    def visit(node: str, path: list[str]) -> None:
        if node in path:
            cycle = path[path.index(node):]
            rotations = [tuple(cycle[index:] + cycle[:index]) for index in range(len(cycle))]
            cycles.add(min(rotations))
            return
        for dependency in graph.get(node, set()):
            visit(dependency, path + [node])

    for node in graph:
        visit(node, [])
    return [list(cycle) for cycle in sorted(cycles)]


def source_ownership(descriptors: dict[str, dict[str, object]]) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    owners: dict[str, list[str]] = {}
    package_owners: dict[str, set[str]] = defaultdict(set)
    for source in sorted((ROOT / "src/main/java").rglob("*.java")):
        relative = source.relative_to(ROOT / "src/main/java").as_posix()
        selected: list[str] = []
        for module, descriptor in descriptors.items():
            if not descriptor["shared_source"]:
                continue
            includes = descriptor["includes"]
            excludes = descriptor["excludes"]
            included = not includes or any(matches(pattern, relative) for pattern in includes)
            excluded = any(matches(pattern, relative) for pattern in excludes)
            if included and not excluded:
                selected.append(module)
        owners[relative] = sorted(selected)
        for module in selected:
            package_owners[package_name(source)].add(module)
    return owners, {key: sorted(value) for key, value in package_owners.items()}


def import_edges() -> tuple[set[str], dict[str, set[str]]]:
    edges: set[str] = set()
    graph: dict[str, set[str]] = defaultdict(set)
    for source in (ROOT / "src/main/java").rglob("*.java"):
        owner_package = package_name(source)
        for imported in imports(source):
            if not imported.startswith("io.onsure."):
                continue
            target_package = imported.rsplit(".", 1)[0]
            if target_package == owner_package:
                continue
            edges.add(f"{owner_package}->{target_package}")
            graph[owner_package].add(target_package)
    return edges, graph


def mutual_package_cycles(graph: dict[str, set[str]]) -> list[list[str]]:
    pairs = {
        tuple(sorted((source, target)))
        for source, targets in graph.items()
        for target in targets
        if source in graph.get(target, set()) and source != target
    }
    return [list(pair) for pair in sorted(pairs)]


def validate() -> dict[str, object]:
    contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    violations: list[str] = []
    if contract["canonical_build"]["pom"] != "pom.xml":
        violations.append("CANONICAL_BUILD_AUTHORITY_DRIFT")
    if contract["compatibility_build"]["release_authority"] is not False:
        violations.append("COMPATIBILITY_BUILD_BECAME_RELEASE_AUTHORITY")

    graph, descriptors = module_state(contract)
    expected_graph = {
        module: set(dependencies)
        for module, dependencies in contract["expected_internal_dependencies"].items()
    }
    if graph != expected_graph:
        violations.append(f"MODULE_DEPENDENCY_GRAPH_DRIFT:{graph}")
    cycles = graph_cycles(graph)
    if cycles:
        violations.append(f"MODULE_DEPENDENCY_CYCLE:{cycles}")

    shared = sorted(module for module, value in descriptors.items() if value["shared_source"])
    expected_shared = sorted(
        contract["transitional_source_ownership"]["shared_source_modules"]
    )
    if shared != expected_shared:
        violations.append(f"SHARED_SOURCE_MODULE_BASELINE_DRIFT:{shared}")

    owners, package_owners = source_ownership(descriptors)
    unowned = sorted(path for path, values in owners.items() if not values)
    multiply_owned = {path: values for path, values in owners.items() if len(values) > 1}
    if unowned:
        violations.append(f"UNOWNED_MAIN_SOURCE:{unowned}")
    if multiply_owned:
        violations.append(f"MULTIPLE_MODULE_SOURCE_OWNERS:{multiply_owned}")

    actual_split = {key: value for key, value in package_owners.items() if len(value) > 1}
    allowed_split = contract["transitional_source_ownership"]["allowed_split_packages"]
    if actual_split != allowed_split:
        violations.append(f"SPLIT_PACKAGE_BASELINE_DRIFT:{actual_split}")

    edges, package_graph = import_edges()
    forbidden_edges = set(contract["dependency_boundaries"]["forbidden_main_import_edges"])
    forbidden_present = sorted(edges & forbidden_edges)
    if forbidden_present:
        violations.append(f"FORBIDDEN_IMPORT_EDGE:{forbidden_present}")
    allowed_cycles = sorted(
        sorted(pair) for pair in contract["dependency_boundaries"]["allowed_package_cycles"]
    )
    actual_cycles = mutual_package_cycles(package_graph)
    if actual_cycles != allowed_cycles:
        violations.append(f"PACKAGE_CYCLE_BASELINE_DRIFT:{actual_cycles}")

    return {
        "contract": "ONSURE_BUILD_BOUNDARY_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "canonical_build": contract["canonical_build"]["authority"],
        "compatibility_build": contract["compatibility_build"]["authority"],
        "module_dependency_graph": {key: sorted(value) for key, value in graph.items()},
        "module_dependency_cycle_count": len(cycles),
        "main_source_file_count": len(owners),
        "main_source_single_owner_count": sum(len(value) == 1 for value in owners.values()),
        "shared_source_module_count": len(shared),
        "target_shared_source_module_count": contract["transitional_source_ownership"]["target_shared_source_module_count"],
        "split_packages": actual_split,
        "package_cycles": actual_cycles,
        "forbidden_import_edge_count": len(forbidden_present),
        "physical_split_removal": "BLOCKED_BY_PACKAGE_AND_PATH_FREEZE",
        "final_claim_allowed": False,
    }


def main() -> int:
    result = validate()
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"ONSURE_BUILD_BOUNDARY_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
