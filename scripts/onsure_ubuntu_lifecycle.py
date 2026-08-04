#!/usr/bin/env python3
"""Rehearse immutable Ubuntu install, upgrade and rollback without touching the host."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import shutil
import tarfile
import tempfile
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
DEFAULT_PACKAGE = ROOT / "target/onsure-ubuntu-candidate.tar.gz"
DEFAULT_STATE = ROOT / ".onsure/ubuntu-lifecycle-rehearsal"
DEFAULT_OUTPUT = ROOT / "assurance/runtime/onsure-ubuntu-lifecycle-rehearsal.v1.json"


def normalized(name: str) -> pathlib.PurePosixPath:
    value = name.removeprefix("./")
    path = pathlib.PurePosixPath(value)
    if not value or path.is_absolute() or ".." in path.parts:
        raise ValueError("LIFECYCLE_PACKAGE_PATH_ESCAPE:" + name)
    return path


def package_contents(package: pathlib.Path) -> dict[str, tuple[bytes, int]]:
    package = package.resolve()
    if not package.is_file():
        raise ValueError("LIFECYCLE_PACKAGE_MISSING")
    contents: dict[str, tuple[bytes, int]] = {}
    total_size = 0
    with tarfile.open(package, "r:gz") as archive:
        for member in archive.getmembers():
            if member.isdir():
                continue
            path = normalized(member.name).as_posix()
            if not member.isfile() or member.uid != 0 or member.gid != 0:
                raise ValueError("LIFECYCLE_PACKAGE_ENTRY_UNSAFE:" + path)
            if member.size > 128 * 1024 * 1024:
                raise ValueError("LIFECYCLE_PACKAGE_ENTRY_TOO_LARGE:" + path)
            total_size += member.size
            if total_size > 512 * 1024 * 1024:
                raise ValueError("LIFECYCLE_PACKAGE_TOO_LARGE")
            if member.mode & 0o7022:
                raise ValueError("LIFECYCLE_PACKAGE_MODE_UNSAFE:" + path)
            extracted = archive.extractfile(member)
            if extracted is None or path in contents:
                raise ValueError("LIFECYCLE_PACKAGE_ENTRY_INVALID:" + path)
            contents[path] = (extracted.read(), member.mode & 0o777)
    checksum_entry = contents.get("SHA256SUMS")
    if checksum_entry is None:
        raise ValueError("LIFECYCLE_CHECKSUMS_MISSING")
    checksums: dict[str, str] = {}
    for line in checksum_entry[0].decode("utf-8").splitlines():
        digest, marker, name = line.partition("  ./")
        if marker != "  ./" or len(digest) != 64:
            raise ValueError("LIFECYCLE_CHECKSUM_FORMAT")
        checksums[normalized(name).as_posix()] = digest
    expected = set(contents) - {"SHA256SUMS"}
    if set(checksums) != expected:
        raise ValueError("LIFECYCLE_CHECKSUM_FILE_SET")
    for name in expected:
        if hashlib.sha256(contents[name][0]).hexdigest() != checksums[name]:
            raise ValueError("LIFECYCLE_CHECKSUM_MISMATCH:" + name)
    return contents


def release_digest(contents: dict[str, tuple[bytes, int]], label: str) -> str:
    digest = hashlib.sha256(label.encode("utf-8"))
    for name in sorted(contents):
        digest.update(name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(hashlib.sha256(contents[name][0]).digest())
    return digest.hexdigest()


def materialize_release(
    state: pathlib.Path,
    contents: dict[str, tuple[bytes, int]],
    label: str,
) -> pathlib.Path:
    releases = state / "releases"
    releases.mkdir(parents=True, exist_ok=True)
    identifier = release_digest(contents, label)
    destination = releases / identifier
    if destination.is_dir():
        return destination
    temporary = pathlib.Path(tempfile.mkdtemp(prefix=".release-", dir=releases))
    try:
        for name, (raw, mode) in contents.items():
            target = temporary.joinpath(*pathlib.PurePosixPath(name).parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
            target.chmod(mode)
        metadata = {
            "contract": "ONSURE_UBUNTU_IMMUTABLE_RELEASE_V1",
            "label": label,
            "release_digest": identifier,
            "file_count": len(contents),
            "final_claim_allowed": False,
        }
        (temporary / ".onsure-release.json").write_text(
            json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        temporary.rename(destination)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)
    return destination


def read_link(state: pathlib.Path, name: str) -> pathlib.Path | None:
    link = state / name
    if not link.is_symlink():
        return None
    target = (state / os.readlink(link)).resolve()
    releases = (state / "releases").resolve()
    if releases not in target.parents:
        raise ValueError("LIFECYCLE_LINK_ESCAPE:" + name)
    return target


def replace_link(state: pathlib.Path, name: str, target: pathlib.Path) -> None:
    relative = target.relative_to(state)
    temporary = state / ("." + name + ".new")
    temporary.unlink(missing_ok=True)
    temporary.symlink_to(relative)
    temporary.replace(state / name)


def activate(state: pathlib.Path, release: pathlib.Path) -> None:
    current = read_link(state, "current")
    if current == release:
        return
    if current is not None:
        replace_link(state, "previous", current)
    replace_link(state, "current", release)


def rollback(state: pathlib.Path) -> None:
    current = read_link(state, "current")
    previous = read_link(state, "previous")
    if current is None or previous is None:
        raise ValueError("LIFECYCLE_ROLLBACK_TARGET_MISSING")
    replace_link(state, "current", previous)
    replace_link(state, "previous", current)


def rehearse(
    package: pathlib.Path,
    state: pathlib.Path,
    allow_external_state: bool = False,
) -> dict[str, object]:
    state = state.resolve()
    approved_root = (ROOT / ".onsure").resolve()
    if not allow_external_state and approved_root not in state.parents:
        raise ValueError("LIFECYCLE_STATE_OUTSIDE_PRODUCT_STATE")
    if state == pathlib.Path("/") or state == pathlib.Path.home().resolve():
        raise ValueError("LIFECYCLE_STATE_ROOT_UNSAFE")
    if state.exists():
        shutil.rmtree(state)
    state.mkdir(parents=True, mode=0o700)
    contents = package_contents(package)
    first = materialize_release(state, contents, "candidate-a")
    activate(state, first)
    idempotent = materialize_release(state, contents, "candidate-a")
    activate(state, idempotent)
    second = materialize_release(state, contents, "candidate-b")
    activate(state, second)
    rollback(state)
    current = read_link(state, "current")
    previous = read_link(state, "previous")
    passed = current == first and previous == second and idempotent == first
    return {
        "contract": "ONSURE_UBUNTU_LIFECYCLE_REHEARSAL_V1",
        "decision": "PASS_NONFINAL" if passed else "FAIL",
        "package_sha256": hashlib.sha256(package.read_bytes()).hexdigest(),
        "package_file_count": len(contents),
        "install_release": first.name,
        "idempotent_reinstall": idempotent == first,
        "upgrade_release": second.name,
        "rollback_restored_release": current.name if current else "MISSING",
        "rollback_previous_release": previous.name if previous else "MISSING",
        "host_filesystem_modified": False,
        "production_execution": "NOT_RUN",
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("rehearse",))
    parser.add_argument("--package", type=pathlib.Path, default=DEFAULT_PACKAGE)
    parser.add_argument("--state", type=pathlib.Path, default=DEFAULT_STATE)
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)
    result = rehearse(args.package, args.state)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, tarfile.TarError) as error:
        print("ONSURE_UBUNTU_LIFECYCLE_FAIL " + str(error))
        raise SystemExit(1)
