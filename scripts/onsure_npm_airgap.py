#!/usr/bin/env python3
"""Build and verify a deterministic npm cache archive with an actual offline npm-ci rehearsal."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import pathlib
import shutil
import subprocess
import tarfile
import tempfile

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
EXTENSION = ROOT / "vscode-extension"


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(args: list[str], cwd: pathlib.Path) -> None:
    result = subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=False)
    if result.returncode:
        raise ValueError("NPM_AIRGAP_COMMAND_FAILED:" + (result.stderr or result.stdout)[-2000:])


def safe_members(archive: tarfile.TarFile) -> list[tarfile.TarInfo]:
    members = archive.getmembers()
    if any(member.issym() or member.islnk() or pathlib.PurePosixPath(member.name).is_absolute()
           or ".." in pathlib.PurePosixPath(member.name).parts for member in members):
        raise ValueError("NPM_AIRGAP_ARCHIVE_PATH_INVALID")
    return members


def build(output: pathlib.Path) -> dict[str, object]:
    output = output.resolve()
    if output.exists():
        raise ValueError("NPM_AIRGAP_OUTPUT_ALREADY_EXISTS")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary_root = ROOT / ".onsure/tmp"
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="onsure-npm-airgap-", dir=temporary_root) as directory:
        root = pathlib.Path(directory)
        project, cache = root / "project", root / "cache"
        project.mkdir()
        for name in ("package.json", "package-lock.json"):
            shutil.copy2(EXTENSION / name, project / name)
        command(["npm", "ci", "--ignore-scripts", "--engine-strict", "--no-audit", "--no-fund",
                 "--cache", str(cache)], project)
        shutil.rmtree(project / "node_modules", ignore_errors=True)
        files = sorted(path for path in cache.rglob("*") if path.is_file() and not path.is_symlink())
        manifest = {
            "contract": "ONSURE_NPM_AIRGAP_CACHE_MANIFEST_V1",
            "package_lock_sha256": sha256(EXTENSION / "package-lock.json"),
            "cache_file_count": len(files),
            "cache_files": {path.relative_to(cache).as_posix(): sha256(path) for path in files},
            "npm_network_used_during_cache_population": True,
            "offline_rehearsal_required": True,
            "final_claim_allowed": False,
        }
        descriptor, temporary_name = tempfile.mkstemp(prefix=output.name + ".", dir=output.parent)
        os.close(descriptor)
        os.unlink(temporary_name)
        temporary = pathlib.Path(temporary_name)
        try:
            with tarfile.open(temporary, "x", format=tarfile.PAX_FORMAT) as archive:
                entries = [(EXTENSION / "package.json", "project/package.json"),
                           (EXTENSION / "package-lock.json", "project/package-lock.json")]
                entries += [(path, "cache/" + path.relative_to(cache).as_posix()) for path in files]
                for source, target in entries:
                    info = archive.gettarinfo(str(source), target)
                    info.uid = info.gid = 0
                    info.uname = info.gname = ""
                    info.mtime = 0
                    info.mode = 0o644
                    with source.open("rb") as stream:
                        archive.addfile(info, stream)
                encoded = (json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n").encode()
                info = tarfile.TarInfo("NPM-AIRGAP-MANIFEST.json")
                info.size, info.mode, info.mtime = len(encoded), 0o644, 0
                archive.addfile(info, io.BytesIO(encoded))
            os.replace(temporary, output)
        finally:
            temporary.unlink(missing_ok=True)
    return verify(output)


def verify(archive_path: pathlib.Path) -> dict[str, object]:
    archive_path = archive_path.resolve()
    temporary_root = ROOT / ".onsure/tmp"
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="onsure-npm-airgap-verify-", dir=temporary_root) as directory:
        root = pathlib.Path(directory)
        with tarfile.open(archive_path, "r") as archive:
            safe_members(archive)
            archive.extractall(root, filter="data")
        manifest = json.loads((root / "NPM-AIRGAP-MANIFEST.json").read_text(encoding="utf-8"))
        actual = {path.relative_to(root / "cache").as_posix(): sha256(path)
                  for path in sorted((root / "cache").rglob("*")) if path.is_file()}
        if actual != manifest["cache_files"]:
            raise ValueError("NPM_AIRGAP_CACHE_DIGEST_MISMATCH")
        if sha256(root / "project/package-lock.json") != manifest["package_lock_sha256"]:
            raise ValueError("NPM_AIRGAP_LOCK_DIGEST_MISMATCH")
        if manifest["package_lock_sha256"] != sha256(EXTENSION / "package-lock.json"):
            raise ValueError("NPM_AIRGAP_SOURCE_LOCK_DRIFT")
        command(["npm", "ci", "--offline", "--ignore-scripts", "--engine-strict", "--no-audit",
                 "--no-fund", "--cache", str(root / "cache")], root / "project")
    return {
        "contract": "ONSURE_NPM_AIRGAP_CACHE_VERIFICATION_V1",
        "decision": "PASS_NONFINAL",
        "archive_sha256": sha256(archive_path),
        "cache_file_count": len(actual),
        "offline_npm_ci": "PASS",
        "network_used_during_verification": False,
        "final_claim_allowed": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("build", "verify"))
    parser.add_argument("--archive", type=pathlib.Path, required=True)
    args = parser.parse_args()
    result = build(args.archive) if args.action == "build" else verify(args.archive)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, tarfile.TarError) as error:
        print("ONSURE_NPM_AIRGAP_FAIL " + str(error), file=__import__("sys").stderr)
        raise SystemExit(1)
