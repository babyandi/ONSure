#!/usr/bin/env python3
"""Restart-safe, fail-closed runner for ONSure's local VS Code workflow."""

from __future__ import annotations

import argparse
import datetime as dt
import fcntl
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tempfile
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_CONTRACT = ROOT / "contracts/unattended-autopilot.v1.json"
ALLOWED_TERMINAL_STATES = {"WAITING_HUMAN_GATE", "MERGE_AUTHORIZED_READY"}
FORBIDDEN_TOKENS = {
    "merge", "push", "approve", "reset", "stash", "deploy", "finallock",
    "production", "commercial", "secret", "credential",
}


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def read_json(path: pathlib.Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def atomic_json(path: pathlib.Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def git(*arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments], cwd=ROOT, text=True, capture_output=True, check=False
    )
    if result.returncode:
        raise RuntimeError(f"GIT_FAILED: {' '.join(arguments)}: {result.stderr.strip()}")
    return result.stdout.strip()


def source_snapshot(require_clean: bool) -> dict[str, Any]:
    head = git("rev-parse", "HEAD")
    status = git("status", "--porcelain", "--untracked-files=no")
    if require_clean and status:
        raise RuntimeError("SOURCE_DIRTY")
    tracked = git("ls-files", "-s")
    return {
        "head": head,
        "tracked_index_sha256": digest_bytes(tracked.encode()),
        "clean": not bool(status),
    }


def validate_contract(contract: dict[str, Any]) -> None:
    if contract.get("contract") != "ONSURE_UNATTENDED_AUTOPILOT_V1":
        raise RuntimeError("CONTRACT_ID_INVALID")
    terminal_state = contract.get("terminal_gate", {}).get("state")
    if terminal_state not in ALLOWED_TERMINAL_STATES:
        raise RuntimeError("TERMINAL_GATE_INVALID")
    if terminal_state == "MERGE_AUTHORIZED_READY":
        authorization = contract.get("merge_authorization", {})
        if not authorization.get("authorized") or not authorization.get("authority"):
            raise RuntimeError("MERGE_AUTHORIZATION_MISSING")
    ids: set[str] = set()
    for stage in contract.get("stages", []):
        stage_id = stage.get("id")
        command = stage.get("command")
        if not stage_id or stage_id in ids or not isinstance(command, list) or not command:
            raise RuntimeError("STAGE_INVALID")
        ids.add(stage_id)
        flattened = " ".join(str(item).lower() for item in command)
        if any(token in flattened for token in FORBIDDEN_TOKENS):
            raise RuntimeError(f"FORBIDDEN_STAGE_COMMAND:{stage_id}")
    for stage in contract["stages"]:
        if not set(stage.get("depends_on", [])).issubset(ids):
            raise RuntimeError(f"UNKNOWN_STAGE_DEPENDENCY:{stage['id']}")


def initial_state(contract: dict[str, Any], snapshot: dict[str, Any]) -> dict[str, Any]:
    return {
        "contract": contract["contract"],
        "contract_sha256": digest_bytes(canonical(contract)),
        "source": snapshot,
        "state": "RUNNING",
        "stages": {
            stage["id"]: {"state": "PENDING", "attempts": 0}
            for stage in contract["stages"]
        },
        "updated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
    }


def write_receipt(directory: pathlib.Path, stage: dict[str, Any], attempt: int,
                  snapshot: dict[str, Any], result: subprocess.CompletedProcess[str]) -> str:
    body = {
        "contract": "ONSURE_AUTOPILOT_STAGE_RECEIPT_V1",
        "stage_id": stage["id"],
        "attempt": attempt,
        "source": snapshot,
        "command": stage["command"],
        "exit_code": result.returncode,
        "stdout_sha256": digest_bytes(result.stdout.encode()),
        "stderr_sha256": digest_bytes(result.stderr.encode()),
        "required_marker": stage["required_marker"],
        "marker_present": stage["required_marker"] in result.stdout,
        "finished_at": dt.datetime.now(dt.timezone.utc).isoformat(),
    }
    body["receipt_sha256"] = digest_bytes(canonical(body))
    path = directory / "receipts" / f"{stage['id']}-attempt-{attempt}.json"
    atomic_json(path, body)
    (path.with_suffix(".stdout")).write_text(result.stdout, encoding="utf-8")
    (path.with_suffix(".stderr")).write_text(result.stderr, encoding="utf-8")
    return str(path.relative_to(ROOT))


def execute(contract: dict[str, Any], state_path: pathlib.Path) -> int:
    checkpoint_dir = state_path.parent
    checkpoint_dir.mkdir(parents=True, exist_ok=True)
    lock_path = checkpoint_dir / "run.lock"
    with lock_path.open("w", encoding="utf-8") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print("ONSURE_AUTOPILOT_BLOCKED ALREADY_RUNNING", file=sys.stderr)
            return 73

        snapshot = source_snapshot(bool(contract.get("source_must_be_clean", True)))
        if state_path.exists():
            state = read_json(state_path)
            if state.get("contract_sha256") != digest_bytes(canonical(contract)):
                raise RuntimeError("CHECKPOINT_CONTRACT_CHANGED")
            if state.get("source") != snapshot:
                raise RuntimeError("CHECKPOINT_SOURCE_CHANGED")
        else:
            state = initial_state(contract, snapshot)
            atomic_json(state_path, state)

        maximum = int(contract.get("maximum_stage_attempts", 1))
        for stage in contract["stages"]:
            entry = state["stages"][stage["id"]]
            if entry["state"] == "PASS":
                continue
            dependencies = [state["stages"][item]["state"] for item in stage["depends_on"]]
            if any(item != "PASS" for item in dependencies):
                raise RuntimeError(f"DEPENDENCY_NOT_PASS:{stage['id']}")
            while entry["attempts"] < maximum:
                entry["state"] = "RUNNING"
                entry["attempts"] += 1
                state["updated_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
                atomic_json(state_path, state)
                environment = os.environ.copy()
                environment["PATH"] = str(ROOT / "scripts/toolchain") + os.pathsep + environment["PATH"]
                result = subprocess.run(
                    stage["command"], cwd=ROOT, env=environment,
                    text=True, capture_output=True, check=False
                )
                receipt = write_receipt(checkpoint_dir, stage, entry["attempts"], snapshot, result)
                entry["last_receipt"] = receipt
                if result.returncode == 0 and stage["required_marker"] in result.stdout:
                    entry["state"] = "PASS"
                    break
                entry["state"] = "RCA_REQUIRED"
                entry["failure"] = {
                    "exit_code": result.returncode,
                    "marker_present": stage["required_marker"] in result.stdout,
                    "classification": "COMMAND_OR_EVIDENCE_FAILURE",
                }
                atomic_json(state_path, state)
            if entry["state"] != "PASS":
                state["state"] = "BLOCKED_RCA_REQUIRED"
                atomic_json(state_path, state)
                print(f"ONSURE_AUTOPILOT_BLOCKED {stage['id']} RCA_REQUIRED", file=sys.stderr)
                return 74
            atomic_json(state_path, state)

        terminal_state = contract["terminal_gate"]["state"]
        state["state"] = terminal_state
        state["terminal_gate"] = contract["terminal_gate"]
        state["updated_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
        state["checkpoint_sha256"] = digest_bytes(canonical({
            key: value for key, value in state.items() if key != "checkpoint_sha256"
        }))
        atomic_json(state_path, state)
        print(f"ONSURE_AUTOPILOT_READY {terminal_state} {state_path}")
        return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("run", "resume", "status", "reset-failed"))
    parser.add_argument("--contract", type=pathlib.Path, default=DEFAULT_CONTRACT)
    args = parser.parse_args()
    contract = read_json(args.contract.resolve())
    validate_contract(contract)
    state_path = ROOT / contract["checkpoint_dir"] / "checkpoint.json"
    if args.action == "status":
        print(json.dumps(read_json(state_path), indent=2) if state_path.exists()
              else "ONSURE_AUTOPILOT_NOT_STARTED")
        return 0
    if args.action == "reset-failed":
        if not state_path.exists():
            print("ONSURE_AUTOPILOT_NOT_STARTED")
            return 0
        state = read_json(state_path)
        for entry in state["stages"].values():
            if entry["state"] == "RCA_REQUIRED":
                entry["state"] = "PENDING"
                entry["attempts"] = 0
        state["state"] = "RUNNING"
        atomic_json(state_path, state)
        print("ONSURE_AUTOPILOT_FAILED_STAGES_RESET")
        return 0
    return execute(contract, state_path)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"ONSURE_AUTOPILOT_FAIL_CLOSED {error}", file=sys.stderr)
        raise SystemExit(72)
