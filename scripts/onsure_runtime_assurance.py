#!/usr/bin/env python3
"""Bounded local performance, failure, recovery and observability evidence tools."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
from typing import Any, Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
CONTRACT = "ONSURE_RUNTIME_ASSURANCE_EVIDENCE_V1"


def digest(path: pathlib.Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def artifact_reference(path: pathlib.Path) -> str:
    """Return a portable evidence reference without binding a host workspace path."""
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return resolved.name


def product_state_path(path: pathlib.Path, error: str) -> pathlib.Path:
    original = path.absolute()
    resolved = original.resolve()
    state_root = (ROOT / ".onsure").resolve()
    if state_root not in resolved.parents or original.is_symlink():
        raise ValueError(error)
    return resolved


def temporary_root() -> pathlib.Path:
    root = (ROOT / ".onsure/tmp").resolve()
    root.mkdir(parents=True, exist_ok=True)
    return root


def atomic_json(path: pathlib.Path, body: dict[str, Any]) -> None:
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(body, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def percentile(values: list[float], percentage: float) -> float:
    if not values:
        raise ValueError("EMPTY_PERCENTILE_INPUT")
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int((len(ordered) - 1) * percentage)))
    return ordered[index]


def benchmark(command: list[str], iterations: int, timeout: float) -> dict[str, Any]:
    if not command or iterations < 1 or iterations > 100 or timeout <= 0 or timeout > 3600:
        raise ValueError("BENCHMARK_BOUNDS_INVALID")
    observations: list[dict[str, Any]] = []
    for iteration in range(1, iterations + 1):
        started = time.monotonic_ns()
        try:
            result = subprocess.run(
                command, cwd=ROOT, capture_output=True, timeout=timeout, check=False
            )
            elapsed = (time.monotonic_ns() - started) / 1_000_000
            observations.append({
                "iteration": iteration,
                "elapsed_ms": round(elapsed, 3),
                "exit_code": result.returncode,
                "stdout_sha256": hashlib.sha256(result.stdout).hexdigest(),
                "stderr_sha256": hashlib.sha256(result.stderr).hexdigest(),
                "stdout_bytes": len(result.stdout),
                "stderr_bytes": len(result.stderr),
                "timed_out": False,
            })
        except subprocess.TimeoutExpired as error:
            elapsed = (time.monotonic_ns() - started) / 1_000_000
            observations.append({
                "iteration": iteration,
                "elapsed_ms": round(elapsed, 3),
                "exit_code": "TIMEOUT",
                "stdout_sha256": hashlib.sha256(error.stdout or b"").hexdigest(),
                "stderr_sha256": hashlib.sha256(error.stderr or b"").hexdigest(),
                "stdout_bytes": len(error.stdout or b""),
                "stderr_bytes": len(error.stderr or b""),
                "timed_out": True,
            })
    elapsed_values = [float(item["elapsed_ms"]) for item in observations]
    success = all(item["exit_code"] == 0 for item in observations)
    return {
        "contract": CONTRACT,
        "evidence_type": "BOUNDED_COMMAND_BENCHMARK",
        "command": command,
        "iterations": iterations,
        "timeout_seconds": timeout,
        "observations": observations,
        "metrics": {
            "minimum_ms": min(elapsed_values),
            "median_ms": percentile(elapsed_values, 0.5),
            "p95_ms": percentile(elapsed_values, 0.95),
            "maximum_ms": max(elapsed_values),
            "successful_iterations": sum(item["exit_code"] == 0 for item in observations),
        },
        "decision": "PASS_NONFINAL" if success else "FAIL",
        "performance_slo_asserted": False,
        "final_claim_allowed": False,
    }


def benchmark_against_baseline(
    command: list[str], iterations: int, timeout: float,
    baseline: dict[str, Any], maximum_p95_regression_percent: float,
) -> dict[str, Any]:
    if maximum_p95_regression_percent < 0 or maximum_p95_regression_percent > 500:
        raise ValueError("BENCHMARK_REGRESSION_BOUND_INVALID")
    expected = float(baseline.get("metrics", {}).get("p95_ms", 0))
    if expected <= 0:
        raise ValueError("BENCHMARK_BASELINE_P95_INVALID")
    result = benchmark(command, iterations, timeout)
    actual = float(result["metrics"]["p95_ms"])
    threshold = expected * (1 + maximum_p95_regression_percent / 100)
    within = actual <= threshold and result["decision"] == "PASS_NONFINAL"
    result.update({
        "evidence_type": "BOUNDED_COMMAND_BENCHMARK_COMPARISON",
        "baseline_p95_ms": expected,
        "maximum_p95_regression_percent": maximum_p95_regression_percent,
        "maximum_allowed_p95_ms": round(threshold, 3),
        "baseline_within_threshold": within,
        "performance_slo_asserted": True,
        "decision": "PASS_NONFINAL" if within else "FAIL",
    })
    return result


def soak(command: list[str], duration_seconds: float, interval_seconds: float, timeout: float) -> dict[str, Any]:
    if not command or duration_seconds <= 0 or duration_seconds > 3600 \
            or interval_seconds < 0 or interval_seconds > 60 or timeout <= 0 or timeout > 300:
        raise ValueError("SOAK_BOUNDS_INVALID")
    started = time.monotonic()
    iterations: list[dict[str, Any]] = []
    while time.monotonic() - started < duration_seconds and len(iterations) < 10_000:
        result = benchmark(command, 1, timeout)
        observation = dict(result["observations"][0])
        observation["soak_iteration"] = len(iterations) + 1
        iterations.append(observation)
        if observation["exit_code"] != 0:
            break
        if interval_seconds:
            time.sleep(min(interval_seconds, max(0, duration_seconds - (time.monotonic() - started))))
    success = bool(iterations) and all(item["exit_code"] == 0 for item in iterations)
    return {
        "contract": CONTRACT,
        "evidence_type": "BOUNDED_SOAK_TEST",
        "command": command,
        "requested_duration_seconds": duration_seconds,
        "actual_duration_seconds": round(time.monotonic() - started, 3),
        "iteration_count": len(iterations),
        "observations": iterations,
        "decision": "PASS_NONFINAL" if success else "FAIL",
        "production_capacity_claimed": False,
        "final_claim_allowed": False,
    }


def fault_probe(mode: str, timeout: float = 0.2) -> dict[str, Any]:
    scenarios = {
        "nonzero_exit": [sys.executable, "-c", "raise SystemExit(23)"],
        "timeout": [sys.executable, "-c", "import time; time.sleep(5)"],
        "synthetic_enospc": [sys.executable, "-c", "import errno; raise OSError(errno.ENOSPC, 'synthetic')"],
    }
    if mode not in scenarios:
        raise ValueError("FAULT_MODE_INVALID")
    evidence = benchmark(scenarios[mode], 1, timeout if mode == "timeout" else 5.0)
    contained = evidence["observations"][0]["exit_code"] in (1, 23, "TIMEOUT")
    evidence.update({
        "evidence_type": "FAULT_CONTAINMENT_PROBE",
        "fault_mode": mode,
        "fault_contained": contained,
        "decision": "PASS_NONFINAL" if contained else "FAIL",
    })
    return evidence


def safe_files(source: pathlib.Path) -> list[pathlib.Path]:
    source = product_state_path(source, "RECOVERY_SOURCE_MUST_BE_ONSURE_DIRECTORY")
    if not source.is_dir():
        raise ValueError("RECOVERY_SOURCE_MUST_BE_ONSURE_DIRECTORY")
    files: list[pathlib.Path] = []
    for path in sorted(source.rglob("*")):
        if path.is_symlink():
            raise ValueError("RECOVERY_SOURCE_SYMLINK_FORBIDDEN")
        if path.is_file():
            files.append(path)
    return files


def backup(source: pathlib.Path, archive: pathlib.Path) -> dict[str, Any]:
    source = source.resolve()
    files = safe_files(source)
    archive = product_state_path(archive, "RECOVERY_ARCHIVE_MUST_BE_ONSURE_DIRECTORY")
    if archive.exists():
        raise ValueError("RECOVERY_ARCHIVE_ALREADY_EXISTS")
    archive.parent.mkdir(parents=True, exist_ok=True)
    manifest = {path.relative_to(source).as_posix(): digest(path) for path in files}
    with tarfile.open(archive, "x") as output:
        for path in files:
            info = output.gettarinfo(str(path), path.relative_to(source).as_posix())
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            info.mtime = 0
            with path.open("rb") as stream:
                output.addfile(info, stream)
        encoded = (json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n").encode()
        info = tarfile.TarInfo("RECOVERY-MANIFEST.json")
        info.size = len(encoded)
        info.mode = 0o600
        info.mtime = 0
        output.addfile(info, __import__("io").BytesIO(encoded))
    return {
        "contract": CONTRACT,
        "evidence_type": "RECOVERY_BACKUP",
        "archive": artifact_reference(archive),
        "archive_sha256": digest(archive),
        "file_count": len(files),
        "customer_data_classification": "NOT_INSPECTED",
        "restore_verified": False,
        "decision": "HOLD_NONFINAL",
        "final_claim_allowed": False,
    }


def verify_restore(archive: pathlib.Path) -> dict[str, Any]:
    archive = product_state_path(archive, "RECOVERY_ARCHIVE_MUST_BE_ONSURE_DIRECTORY")
    if not archive.is_file() or archive.is_symlink():
        raise ValueError("RECOVERY_ARCHIVE_INVALID")
    with tempfile.TemporaryDirectory(prefix="onsure-recovery-verify-", dir=temporary_root()) as directory:
        root = pathlib.Path(directory)
        with tarfile.open(archive, "r") as source:
            members = source.getmembers()
            if any(member.issym() or member.islnk() or pathlib.PurePosixPath(member.name).is_absolute()
                   or ".." in pathlib.PurePosixPath(member.name).parts for member in members):
                raise ValueError("RECOVERY_ARCHIVE_PATH_INVALID")
            source.extractall(root, filter="data")
        manifest_path = root / "RECOVERY-MANIFEST.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        actual = {
            path.relative_to(root).as_posix(): digest(path)
            for path in sorted(root.rglob("*"))
            if path.is_file() and path != manifest_path
        }
        if actual != manifest:
            raise ValueError("RECOVERY_ARCHIVE_DIGEST_MISMATCH")
    return {
        "contract": CONTRACT,
        "evidence_type": "RECOVERY_RESTORE_VERIFICATION",
        "archive_sha256": digest(archive),
        "file_count": len(manifest),
        "restore_verified": True,
        "decision": "PASS_NONFINAL",
        "final_claim_allowed": False,
    }


def dr_rehearsal(source: pathlib.Path, archive: pathlib.Path) -> dict[str, Any]:
    created = backup(source, archive)
    verified = verify_restore(archive)
    with tempfile.TemporaryDirectory(prefix="onsure-dr-corruption-", dir=temporary_root()) as directory:
        corrupted = pathlib.Path(directory) / "corrupted.tar"
        shutil.copyfile(archive, corrupted)
        data = bytearray(corrupted.read_bytes())
        if not data:
            raise ValueError("DR_ARCHIVE_EMPTY")
        data[min(len(data) - 1, 1024)] ^= 0x01
        corrupted.write_bytes(data)
        corruption_rejected = False
        try:
            verify_restore(corrupted)
        except (ValueError, tarfile.TarError, json.JSONDecodeError, OSError):
            corruption_rejected = True
    with tempfile.TemporaryDirectory(prefix="onsure-dr-adversarial-", dir=temporary_root()) as directory:
        adversarial_root = pathlib.Path(directory)
        traversal = adversarial_root / "traversal.tar"
        with tarfile.open(traversal, "w") as output:
            payload = b"escape"
            member = tarfile.TarInfo("../escape.txt")
            member.size = len(payload)
            output.addfile(member, __import__("io").BytesIO(payload))
        path_traversal_rejected = False
        try:
            verify_restore(traversal)
        except ValueError as error:
            path_traversal_rejected = "RECOVERY_ARCHIVE_PATH_INVALID" in str(error)

        symlink_source = adversarial_root / "symlink-source"
        symlink_source.mkdir()
        (symlink_source / "outside-link").symlink_to(ROOT / "README.md")
        symlink_source_rejected = False
        try:
            safe_files(symlink_source)
        except ValueError as error:
            symlink_source_rejected = "RECOVERY_SOURCE_SYMLINK_FORBIDDEN" in str(error)

    decision = (verified["restore_verified"] and corruption_rejected
                and path_traversal_rejected and symlink_source_rejected)
    return {
        "contract": CONTRACT,
        "evidence_type": "SYNTHETIC_DR_REHEARSAL",
        "archive": artifact_reference(archive),
        "archive_sha256": created["archive_sha256"],
        "file_count": created["file_count"],
        "isolated_restore_verified": verified["restore_verified"],
        "corrupted_archive_rejected": corruption_rejected,
        "path_traversal_archive_rejected": path_traversal_rejected,
        "symlink_source_rejected": symlink_source_rejected,
        "source_mutated": False,
        "decision": "PASS_NONFINAL" if decision else "FAIL",
        "production_dr_claimed": False,
        "final_claim_allowed": False,
    }


def health() -> dict[str, Any]:
    usage = shutil.disk_usage(ROOT)
    return {
        "contract": CONTRACT,
        "evidence_type": "LOCAL_OBSERVABILITY_HEALTH",
        "python_version": sys.version.split()[0],
        "cpu_count": os.cpu_count(),
        "workspace_free_bytes": usage.free,
        "workspace_total_bytes": usage.total,
        "maven_available": shutil.which("mvn") is not None,
        "git_available": shutil.which("git") is not None,
        "network_probe": "NOT_RUN",
        "customer_data_probe": "NOT_RUN",
        "decision": "PASS_NONFINAL",
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=(
        "benchmark", "benchmark-compare", "soak", "fault", "backup", "restore-verify", "dr-rehearsal", "health"))
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--iterations", type=int, default=3)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--mode", choices=("nonzero_exit", "timeout", "synthetic_enospc"))
    parser.add_argument("--baseline", type=pathlib.Path)
    parser.add_argument("--maximum-p95-regression-percent", type=float, default=20.0)
    parser.add_argument("--duration", type=float, default=10.0)
    parser.add_argument("--interval", type=float, default=0.1)
    parser.add_argument("--source", type=pathlib.Path)
    parser.add_argument("--archive", type=pathlib.Path)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    if args.action == "benchmark":
        command = args.command[1:] if args.command[:1] == ["--"] else args.command
        result = benchmark(command, args.iterations, args.timeout)
    elif args.action == "benchmark-compare":
        if not args.baseline:
            raise ValueError("BENCHMARK_BASELINE_REQUIRED")
        command = args.command[1:] if args.command[:1] == ["--"] else args.command
        result = benchmark_against_baseline(
            command, args.iterations, args.timeout,
            json.loads(args.baseline.read_text(encoding="utf-8")),
            args.maximum_p95_regression_percent,
        )
    elif args.action == "soak":
        command = args.command[1:] if args.command[:1] == ["--"] else args.command
        result = soak(command, args.duration, args.interval, args.timeout)
    elif args.action == "fault":
        result = fault_probe(args.mode or "nonzero_exit", args.timeout)
    elif args.action == "backup":
        if not args.source or not args.archive:
            raise ValueError("BACKUP_SOURCE_AND_ARCHIVE_REQUIRED")
        result = backup(args.source, args.archive)
    elif args.action == "restore-verify":
        if not args.archive:
            raise ValueError("RESTORE_ARCHIVE_REQUIRED")
        result = verify_restore(args.archive)
    elif args.action == "dr-rehearsal":
        if not args.source or not args.archive:
            raise ValueError("DR_SOURCE_AND_ARCHIVE_REQUIRED")
        result = dr_rehearsal(args.source, args.archive)
    else:
        result = health()
    if args.output:
        atomic_json(args.output, result)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] != "FAIL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, tarfile.TarError) as error:
        print(f"ONSURE_RUNTIME_ASSURANCE_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
