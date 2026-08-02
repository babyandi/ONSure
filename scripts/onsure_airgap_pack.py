#!/usr/bin/env python3
"""Plan, build and verify a network-free deterministic Maven dependency pack."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import pathlib
import tarfile
import tempfile
import urllib.parse
from typing import Any, Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
SBOM = ROOT / "assurance/dependencies/onsure.cdx.json"
POLICY = ROOT / "contracts/airgap-dependency-pack.v1.json"
BUILD_DESCRIPTORS = (
    "pom.xml", "pom-modular.xml", "vscode-extension/package.json",
    "vscode-extension/package-lock.json", "assurance/dependencies/onsure.cdx.json",
    "assurance/dependencies/onsure-dependency-license-inventory.v1.json",
)


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def coordinates(purl: str) -> tuple[str, str, str]:
    parsed = urllib.parse.urlparse(purl)
    if parsed.scheme != "pkg" or not parsed.path.startswith("maven/") or "@" not in parsed.path:
        raise ValueError("AIRGAP_MAVEN_PURL_INVALID:" + purl)
    name, version = parsed.path.removeprefix("maven/").rsplit("@", 1)
    group, artifact = name.rsplit("/", 1)
    if not all((group, artifact, version)) or any(".." in value for value in (group, artifact, version)):
        raise ValueError("AIRGAP_MAVEN_COORDINATE_INVALID:" + purl)
    return group, artifact, version


def expected_sha(component: dict[str, Any]) -> str:
    for value in component.get("hashes", []):
        if value.get("alg") == "SHA-256" and len(str(value.get("content", ""))) == 64:
            return str(value["content"])
    raise ValueError("AIRGAP_COMPONENT_SHA256_MISSING:" + str(component.get("purl")))


def plan(maven_repository: pathlib.Path, sbom_path: pathlib.Path = SBOM) -> dict[str, Any]:
    repository = maven_repository.resolve()
    if not repository.is_dir() or repository.is_symlink():
        raise ValueError("AIRGAP_MAVEN_REPOSITORY_INVALID")
    sbom = json.loads(sbom_path.read_text(encoding="utf-8"))
    artifacts: list[dict[str, Any]] = []
    missing: list[str] = []
    mismatched: list[str] = []
    for component in sorted(sbom.get("components", []), key=lambda value: value.get("purl", "")):
        group, artifact, version = coordinates(str(component.get("purl", "")))
        relative = pathlib.Path(*group.split(".")) / artifact / version
        for extension in ("jar", "pom"):
            local = repository / relative / f"{artifact}-{version}.{extension}"
            pack_path = (pathlib.Path("maven2") / relative / local.name).as_posix()
            if not local.is_file() or local.is_symlink():
                missing.append(pack_path)
                continue
            actual = sha256(local)
            if extension == "jar" and actual != expected_sha(component):
                mismatched.append(pack_path)
            artifacts.append({
                "pack_path": pack_path,
                "source_path": str(local),
                "sha256": actual,
                "size_bytes": local.stat().st_size,
                "kind": extension.upper(),
                "purl": component["purl"],
            })
    descriptors: list[dict[str, Any]] = []
    for relative in BUILD_DESCRIPTORS:
        source = ROOT / relative
        if not source.is_file() or source.is_symlink():
            missing.append("source/" + relative)
            continue
        descriptors.append({
            "pack_path": "source/" + relative,
            "source_path": str(source),
            "sha256": sha256(source),
            "size_bytes": source.stat().st_size,
            "kind": "BUILD_DESCRIPTOR",
        })
    complete = not missing and not mismatched
    return {
        "contract": "ONSURE_AIRGAP_DEPENDENCY_PACK_MANIFEST_V1",
        "policy_sha256": sha256(POLICY),
        "source_sbom_sha256": sha256(sbom_path),
        "maven_artifacts": artifacts,
        "build_descriptors": descriptors,
        "missing": missing,
        "digest_mismatches": mismatched,
        "maven_payload_complete": complete,
        "npm_payload": "NOT_RUN_REQUIRES_EXPLICIT_CACHE_EXPORT",
        "network_access_used": False,
        "external_signature": "NOT_RUN",
        "release_authority": False,
        "final_claim_allowed": False,
    }


def canonical_manifest(value: dict[str, Any]) -> bytes:
    public = json.loads(json.dumps(value))
    for collection in ("maven_artifacts", "build_descriptors"):
        for item in public.get(collection, []):
            item.pop("source_path", None)
    return (json.dumps(public, sort_keys=True, separators=(",", ":")) + "\n").encode()


def build(manifest: dict[str, Any], output: pathlib.Path) -> dict[str, Any]:
    if not manifest.get("maven_payload_complete"):
        raise ValueError("AIRGAP_MAVEN_PAYLOAD_INCOMPLETE")
    output = output.resolve()
    if output.exists():
        raise ValueError("AIRGAP_OUTPUT_ALREADY_EXISTS")
    output.parent.mkdir(parents=True, exist_ok=True)
    entries = sorted(
        manifest["maven_artifacts"] + manifest["build_descriptors"],
        key=lambda value: value["pack_path"],
    )
    descriptor, temporary_name = tempfile.mkstemp(prefix=output.name + ".", dir=output.parent)
    os.close(descriptor)
    os.unlink(temporary_name)
    temporary = pathlib.Path(temporary_name)
    try:
        with tarfile.open(temporary, "x", format=tarfile.PAX_FORMAT) as archive:
            for entry in entries:
                source = pathlib.Path(entry["source_path"])
                if sha256(source) != entry["sha256"]:
                    raise ValueError("AIRGAP_SOURCE_DRIFT:" + entry["pack_path"])
                info = archive.gettarinfo(str(source), entry["pack_path"])
                info.uid = info.gid = 0
                info.uname = info.gname = ""
                info.mtime = 0
                info.mode = 0o644
                with source.open("rb") as stream:
                    archive.addfile(info, stream)
            encoded = canonical_manifest(manifest)
            info = tarfile.TarInfo("AIRGAP-MANIFEST.json")
            info.size = len(encoded)
            info.mode = 0o644
            info.mtime = 0
            archive.addfile(info, io.BytesIO(encoded))
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    return verify(output)


def verify(archive_path: pathlib.Path) -> dict[str, Any]:
    archive_path = archive_path.resolve()
    if not archive_path.is_file() or archive_path.is_symlink():
        raise ValueError("AIRGAP_ARCHIVE_INVALID")
    with tarfile.open(archive_path, "r") as archive:
        members = archive.getmembers()
        for member in members:
            path = pathlib.PurePosixPath(member.name)
            if member.issym() or member.islnk() or path.is_absolute() or ".." in path.parts:
                raise ValueError("AIRGAP_ARCHIVE_PATH_INVALID")
        manifest_member = archive.getmember("AIRGAP-MANIFEST.json")
        manifest = json.load(archive.extractfile(manifest_member))
        expected = {
            entry["pack_path"]: entry["sha256"]
            for collection in ("maven_artifacts", "build_descriptors")
            for entry in manifest[collection]
        }
        actual: dict[str, str] = {}
        for member in members:
            if not member.isfile() or member.name == "AIRGAP-MANIFEST.json":
                continue
            stream = archive.extractfile(member)
            actual[member.name] = hashlib.sha256(stream.read()).hexdigest()
        if actual != expected:
            raise ValueError("AIRGAP_ARCHIVE_DIGEST_MISMATCH")
    return {
        "contract": "ONSURE_AIRGAP_DEPENDENCY_PACK_VERIFICATION_V1",
        "decision": "PASS_NONFINAL",
        "archive_sha256": sha256(archive_path),
        "verified_entry_count": len(actual),
        "network_access_used": False,
        "external_signature": manifest.get("external_signature", "NOT_RUN"),
        "release_authority": False,
        "final_claim_allowed": False,
    }


def atomic_json(path: pathlib.Path, value: dict[str, Any]) -> None:
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("plan", "build", "verify"))
    parser.add_argument("--maven-repository", type=pathlib.Path)
    parser.add_argument("--manifest", type=pathlib.Path)
    parser.add_argument("--archive", type=pathlib.Path)
    args = parser.parse_args(argv)
    if args.action == "verify":
        if not args.archive:
            raise ValueError("AIRGAP_ARCHIVE_REQUIRED")
        result = verify(args.archive)
    else:
        if not args.maven_repository:
            raise ValueError("AIRGAP_MAVEN_REPOSITORY_REQUIRED")
        manifest = plan(args.maven_repository)
        if args.manifest:
            atomic_json(args.manifest, manifest)
        result = manifest if args.action == "plan" else build(
            manifest, args.archive or ROOT / ".onsure/airgap/onsure-dependencies.tar")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result.get("decision") != "FAIL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError, tarfile.TarError) as error:
        print(f"ONSURE_AIRGAP_PACK_FAIL {error}", file=__import__("sys").stderr)
        raise SystemExit(1)
