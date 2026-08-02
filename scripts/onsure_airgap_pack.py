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
import subprocess
from typing import Any, Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
SBOM = ROOT / "assurance/dependencies/onsure.cdx.json"
POLICY = ROOT / "contracts/airgap-dependency-pack.v1.json"
BUILD_DESCRIPTORS = (
    "pom.xml", "pom-modular.xml", "vscode-extension/package.json",
    "vscode-extension/package-lock.json", "assurance/dependencies/onsure.cdx.json",
    "assurance/dependencies/onsure-dependency-license-inventory.v1.json",
    "assurance/dependencies/onsure-vscode-dependency-inventory.v1.json",
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
        if group == "io.onsure":
            continue
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


def build_repository_pack(repository: pathlib.Path, output: pathlib.Path) -> dict[str, Any]:
    repository = repository.resolve()
    output = output.resolve()
    if not repository.is_dir() or repository.is_symlink():
        raise ValueError("AIRGAP_OFFLINE_REPOSITORY_INVALID")
    if output.exists():
        raise ValueError("AIRGAP_OUTPUT_ALREADY_EXISTS")
    files = sorted(path for path in repository.rglob("*") if path.is_file() and not path.is_symlink())
    if not files:
        raise ValueError("AIRGAP_OFFLINE_REPOSITORY_EMPTY")
    if any(path.is_symlink() for path in repository.rglob("*")):
        raise ValueError("AIRGAP_OFFLINE_REPOSITORY_SYMLINK_FORBIDDEN")
    manifest = {
        "contract": "ONSURE_MAVEN_OFFLINE_REPOSITORY_MANIFEST_V1",
        "source_sbom_sha256": sha256(SBOM),
        "entry_count": len(files),
        "entries": {path.relative_to(repository).as_posix(): sha256(path) for path in files},
        "network_used_during_bootstrap": True,
        "offline_rehearsal_required": True,
        "release_authority": False,
        "final_claim_allowed": False,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=output.name + ".", dir=output.parent)
    os.close(descriptor)
    os.unlink(temporary_name)
    temporary = pathlib.Path(temporary_name)
    try:
        with tarfile.open(temporary, "x", format=tarfile.PAX_FORMAT) as archive:
            for source in files:
                target = "maven2/" + source.relative_to(repository).as_posix()
                info = archive.gettarinfo(str(source), target)
                info.uid = info.gid = 0
                info.uname = info.gname = ""
                info.mtime = 0
                info.mode = 0o644
                with source.open("rb") as stream:
                    archive.addfile(info, stream)
            encoded = (json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n").encode()
            info = tarfile.TarInfo("MAVEN-OFFLINE-MANIFEST.json")
            info.size, info.mode, info.mtime = len(encoded), 0o644, 0
            archive.addfile(info, io.BytesIO(encoded))
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    return verify_repository_pack(output)


def verify_repository_pack(archive_path: pathlib.Path) -> dict[str, Any]:
    archive_path = archive_path.resolve()
    with tarfile.open(archive_path, "r") as archive:
        members = archive.getmembers()
        if any(member.issym() or member.islnk() or pathlib.PurePosixPath(member.name).is_absolute()
               or ".." in pathlib.PurePosixPath(member.name).parts for member in members):
            raise ValueError("AIRGAP_ARCHIVE_PATH_INVALID")
        manifest = json.load(archive.extractfile("MAVEN-OFFLINE-MANIFEST.json"))
        actual = {}
        for member in members:
            if member.isfile() and member.name.startswith("maven2/"):
                actual[member.name.removeprefix("maven2/")] = hashlib.sha256(
                    archive.extractfile(member).read()).hexdigest()
        if actual != manifest["entries"]:
            raise ValueError("AIRGAP_OFFLINE_REPOSITORY_DIGEST_MISMATCH")
    return {
        "contract": "ONSURE_MAVEN_OFFLINE_REPOSITORY_VERIFICATION_V1",
        "decision": "PASS_NONFINAL",
        "archive_sha256": sha256(archive_path),
        "verified_entry_count": len(actual),
        "source_sbom_sha256": manifest["source_sbom_sha256"],
        "offline_build_rehearsal": "NOT_RUN",
        "final_claim_allowed": False,
    }


def rehearse_repository_pack(archive_path: pathlib.Path) -> dict[str, Any]:
    verified = verify_repository_pack(archive_path)
    temporary_root = ROOT / ".onsure/tmp"
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="onsure-maven-offline-", dir=temporary_root) as directory:
        root = pathlib.Path(directory)
        with tarfile.open(archive_path, "r") as archive:
            members = archive.getmembers()
            if any(member.issym() or member.islnk() or pathlib.PurePosixPath(member.name).is_absolute()
                   or ".." in pathlib.PurePosixPath(member.name).parts for member in members):
                raise ValueError("AIRGAP_ARCHIVE_PATH_INVALID")
            archive.extractall(root, filter="data")
        repository = root / "maven2"
        commands = [
            ["mvn", "-B", "-ntp", "-q", "-o", f"-Dmaven.repo.local={repository}", "clean", "verify"],
            ["mvn", "-B", "-ntp", "-q", "-o", f"-Dmaven.repo.local={repository}",
             "-f", "pom-modular.xml", "clean", "package"],
        ]
        for command in commands:
            environment = dict(os.environ)
            environment["TMPDIR"] = str(root)
            process = subprocess.run(
                command, cwd=ROOT, env=environment, text=True, capture_output=True, check=False)
            if process.returncode:
                raise ValueError("AIRGAP_OFFLINE_BUILD_FAILED:" + process.stderr[-2000:])
    verified.update({
        "contract": "ONSURE_MAVEN_OFFLINE_REHEARSAL_V1",
        "offline_build_rehearsal": "PASS",
        "network_access_used": False,
    })
    return verified


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
    parser.add_argument("action", choices=(
        "plan", "build", "verify", "repository-build", "repository-verify", "repository-rehearse"))
    parser.add_argument("--maven-repository", type=pathlib.Path)
    parser.add_argument("--manifest", type=pathlib.Path)
    parser.add_argument("--archive", type=pathlib.Path)
    parser.add_argument("--offline-repository", type=pathlib.Path)
    args = parser.parse_args(argv)
    if args.action == "repository-build":
        if not args.offline_repository or not args.archive:
            raise ValueError("AIRGAP_OFFLINE_REPOSITORY_AND_ARCHIVE_REQUIRED")
        result = build_repository_pack(args.offline_repository, args.archive)
    elif args.action == "repository-verify":
        if not args.archive:
            raise ValueError("AIRGAP_ARCHIVE_REQUIRED")
        result = verify_repository_pack(args.archive)
    elif args.action == "repository-rehearse":
        if not args.archive:
            raise ValueError("AIRGAP_ARCHIVE_REQUIRED")
        result = rehearse_repository_pack(args.archive)
    elif args.action == "verify":
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
