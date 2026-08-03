#!/usr/bin/env python3
"""Validate the standalone RHEL tar without extracting or installing it."""

from __future__ import annotations

import hashlib
import io
import json
import pathlib
import re
import sys
import tarfile
import zipfile

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
PACKAGE = ROOT / "target/onsure-rhel-candidate.tar.gz"
OUTPUT = ROOT / "assurance/runtime/onsure-rhel-package-validation.v1.json"
SOURCE_FILES = (
    ROOT / "scripts/package_onsure_rhel.sh",
    ROOT / "deploy/rhel/onsure.service",
    ROOT / "deploy/rhel/onsure-migrate.service",
    ROOT / "deploy/rhel/onsure.env.example",
    ROOT / "deploy/rhel/onsure.sysusers.conf",
    ROOT / "deploy/rhel/onsure.tmpfiles.conf",
    ROOT / "deploy/rhel/README.md",
)
REQUIRED_FILES = {
    "etc/onsure/onsure.env.example",
    "opt/onsure/README.md",
    "opt/onsure/app/onsure-core-0.1.0-SNAPSHOT.jar",
    "opt/onsure/app/onsure-local-api-0.1.0-SNAPSHOT.jar",
    "opt/onsure/app/onsure-provider-openai-0.1.0-SNAPSHOT.jar",
    "opt/onsure/app/onsure-provider-spi-0.1.0-SNAPSHOT.jar",
    "opt/onsure/migration/onsure-migration-postgresql-0.1.0-SNAPSHOT.jar",
    "usr/lib/systemd/system/onsure.service",
    "usr/lib/systemd/system/onsure-migrate.service",
    "usr/lib/sysusers.d/onsure.sysusers.conf",
    "usr/lib/tmpfiles.d/onsure.tmpfiles.conf",
    "SHA256SUMS",
}
JAR_CLASSES = {
    "opt/onsure/app/onsure-local-api-0.1.0-SNAPSHOT.jar": "io/onsure/localapi/LocalApiMain.class",
    "opt/onsure/app/onsure-provider-openai-0.1.0-SNAPSHOT.jar": "io/onsure/provider/openai/OpenAiProviderMain.class",
    "opt/onsure/migration/onsure-migration-postgresql-0.1.0-SNAPSHOT.jar": (
        "io/onsure/migration/postgresql/PostgresqlMigrationMain.class"
    ),
}
MAXIMUM_JAR_UNCOMPRESSED_BYTES = 128 * 1024 * 1024


def normalized(name: str) -> str:
    value = name.removeprefix("./")
    path = pathlib.PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError("PACKAGE_PATH_ESCAPE:" + name)
    return path.as_posix()


def validate(package: pathlib.Path = PACKAGE) -> dict[str, object]:
    package = package.resolve()
    if not package.is_file() or ROOT not in package.parents:
        raise ValueError("RHEL_PACKAGE_MISSING_OR_OUTSIDE_PRODUCT_ROOT")
    contents: dict[str, bytes] = {}
    modes: dict[str, int] = {}
    with tarfile.open(package, "r:gz") as archive:
        members = archive.getmembers()
        for member in members:
            name = normalized(member.name)
            if not name or member.isdir():
                continue
            if not member.isfile():
                raise ValueError("PACKAGE_NONREGULAR_ENTRY:" + name)
            if name in contents:
                raise ValueError("PACKAGE_DUPLICATE_ENTRY:" + name)
            if member.uid != 0 or member.gid != 0:
                raise ValueError("PACKAGE_NONROOT_OWNERSHIP:" + name)
            if member.mode & 0o7000:
                raise ValueError("PACKAGE_SPECIAL_PERMISSION:" + name)
            if member.mode & 0o022:
                raise ValueError("PACKAGE_WRITABLE_BY_GROUP_OR_WORLD:" + name)
            extracted = archive.extractfile(member)
            if extracted is None:
                raise ValueError("PACKAGE_FILE_UNREADABLE:" + name)
            contents[name] = extracted.read()
            modes[name] = member.mode & 0o777
    missing = sorted(REQUIRED_FILES - set(contents))
    if missing:
        raise ValueError("PACKAGE_REQUIRED_FILE_MISSING:" + ",".join(missing))
    unexpected = sorted(
        name for name in contents
        if name not in REQUIRED_FILES
        and not re.fullmatch(r"opt/onsure/lib/[A-Za-z0-9_.-]+\.jar", name)
    )
    if unexpected:
        raise ValueError("PACKAGE_UNEXPECTED_FILE:" + ",".join(unexpected))
    if modes["etc/onsure/onsure.env.example"] != 0o640:
        raise ValueError("PACKAGE_ENVIRONMENT_MODE")

    checksums: dict[str, str] = {}
    for line in contents["SHA256SUMS"].decode("utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  \./(.+)", line)
        if not match:
            raise ValueError("PACKAGE_CHECKSUM_FORMAT")
        checksums[normalized(match.group(2))] = match.group(1)
    expected_files = set(contents) - {"SHA256SUMS"}
    if set(checksums) != expected_files:
        raise ValueError("PACKAGE_CHECKSUM_FILE_SET")
    for name in sorted(expected_files):
        if hashlib.sha256(contents[name]).hexdigest() != checksums[name]:
            raise ValueError("PACKAGE_CHECKSUM_MISMATCH:" + name)

    environment = contents["etc/onsure/onsure.env.example"].decode("utf-8")
    for secret in ("OPENAI_API_KEY", "ONSURE_DB_PASSWORD", "ONSURE_LOCAL_API_TOKEN"):
        if any(line.startswith(secret + "=") for line in environment.splitlines()):
            raise ValueError("PACKAGE_SECRET_SLOT_ACTIVE:" + secret)
    for jar, expected_class in JAR_CLASSES.items():
        with zipfile.ZipFile(io.BytesIO(contents[jar])) as archive:
            entries = archive.infolist()
            if any(pathlib.PurePosixPath(entry.filename).is_absolute()
                   or ".." in pathlib.PurePosixPath(entry.filename).parts for entry in entries):
                raise ValueError("PACKAGE_JAR_PATH_ESCAPE:" + jar)
            if sum(entry.file_size for entry in entries) > MAXIMUM_JAR_UNCOMPRESSED_BYTES:
                raise ValueError("PACKAGE_JAR_UNCOMPRESSED_SIZE:" + jar)
            if expected_class not in {entry.filename for entry in entries}:
                raise ValueError("PACKAGE_MAIN_CLASS_MISSING:" + jar)

    return {
        "contract": "ONSURE_RHEL_PACKAGE_VALIDATION_V1",
        "decision": "PASS_NONFINAL",
        "package": package.relative_to(ROOT).as_posix(),
        "package_sha256": hashlib.sha256(package.read_bytes()).hexdigest(),
        "package_size_bytes": package.stat().st_size,
        "regular_file_count": len(contents),
        "internal_checksum_count": len(checksums),
        "root_owned_file_count": len(contents),
        "secret_values_present": False,
        "path_escape_or_nonregular_entry_count": 0,
        "source_bindings": {
            path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in SOURCE_FILES
        },
        "install_execution": "NOT_RUN",
        "rhel_runtime": "NOT_RUN_HOST_IS_NOT_RHEL",
        "final_claim_allowed": False,
    }


def main() -> int:
    result = validate()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, tarfile.TarError, zipfile.BadZipFile) as error:
        print("ONSURE_RHEL_PACKAGE_VALIDATION_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
