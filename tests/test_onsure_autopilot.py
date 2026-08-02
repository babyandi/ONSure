import importlib.util
import json
import os
import pathlib
import sys
import tempfile
import threading
import time
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "onsure_autopilot", ROOT / "scripts/onsure-autopilot.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader
SPEC.loader.exec_module(MODULE)


class AutopilotContractTest(unittest.TestCase):
    def setUp(self):
        self.contract = json.loads(
            (ROOT / "contracts/unattended-autopilot.v1.json").read_text()
        )

    def test_repository_contract_is_valid(self):
        MODULE.validate_contract(self.contract)

    def test_merge_command_is_rejected(self):
        invalid = json.loads(json.dumps(self.contract))
        invalid["stages"][0]["command"] = ["git", "merge", "main"]
        with self.assertRaisesRegex(RuntimeError, "FORBIDDEN_STAGE_COMMAND"):
            MODULE.validate_contract(invalid)

    def test_control_and_restart_contract_cannot_be_weakened(self):
        invalid = json.loads(json.dumps(self.contract))
        invalid["supported_controls"] = ["PAUSE", "RESUME"]
        with self.assertRaisesRegex(RuntimeError, "CONTROL_CONTRACT_INVALID"):
            MODULE.validate_contract(invalid)
        invalid = json.loads(json.dumps(self.contract))
        invalid["restart_recovery"]["orphan_process_present"] = "RETRY"
        with self.assertRaisesRegex(RuntimeError, "RESTART_RECOVERY_CONTRACT_INVALID"):
            MODULE.validate_contract(invalid)

    def test_terminal_gate_cannot_be_removed(self):
        invalid = json.loads(json.dumps(self.contract))
        invalid["terminal_gate"]["state"] = "MERGED"
        with self.assertRaisesRegex(RuntimeError, "TERMINAL_GATE"):
            MODULE.validate_contract(invalid)

    def test_merge_ready_requires_explicit_authority(self):
        invalid = json.loads(json.dumps(self.contract))
        invalid["terminal_gate"]["state"] = "MERGE_AUTHORIZED_READY"
        invalid["merge_authorization"]["authorized"] = True
        invalid["merge_authorization"]["authority"] = ""
        with self.assertRaisesRegex(RuntimeError, "MERGE_AUTHORIZATION_MISSING"):
            MODULE.validate_contract(invalid)

    def test_waiting_gate_rejects_premature_merge_authority(self):
        invalid = json.loads(json.dumps(self.contract))
        invalid["merge_authorization"]["authorized"] = True
        invalid["merge_authorization"]["authority"] = "unapproved-actor"
        with self.assertRaisesRegex(RuntimeError, "MERGE_AUTHORIZATION_MISSING"):
            MODULE.validate_contract(invalid)

    def test_unknown_dependency_is_rejected(self):
        invalid = json.loads(json.dumps(self.contract))
        invalid["stages"][0]["depends_on"] = ["not-real"]
        with self.assertRaisesRegex(RuntimeError, "UNKNOWN_STAGE_DEPENDENCY"):
            MODULE.validate_contract(invalid)

    def test_atomic_checkpoint_round_trip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "checkpoint.json"
            MODULE.atomic_json(path, {"state": "RUNNING"})
            self.assertEqual({"state": "RUNNING"}, MODULE.read_json(path))

    def test_forbidden_actions_remain_declared(self):
        forbidden = set(self.contract["forbidden_actions"])
        self.assertTrue({
            "FINAL_PASS", "FINAL_LOCK", "PRODUCTION_GO",
            "COMMERCIAL_GO", "FORCE_PUSH", "HARD_RESET", "IMPLICIT_STASH",
        }.issubset(forbidden))

    def test_control_requests_are_contract_bound_and_restart_recovery_is_explicit(self):
        with tempfile.TemporaryDirectory() as directory:
            state_path = pathlib.Path(directory) / "checkpoint.json"
            state = MODULE.initial_state(self.contract, {"head": "a" * 40, "clean": True})
            MODULE.atomic_json(state_path, state)
            MODULE.request_control(self.contract, state_path, "PAUSED")
            self.assertEqual("PAUSED", MODULE.desired_control(self.contract, state_path))
            MODULE.request_control(self.contract, state_path, "RUNNING")
            self.assertEqual("RUNNING", MODULE.desired_control(self.contract, state_path))
            self.assertFalse(MODULE.controller_active(state_path))

            entry = next(iter(state["stages"].values()))
            entry.update({"state": "RUNNING", "attempts": 1})
            self.assertTrue(MODULE.recover_interrupted_state(state))
            self.assertEqual("RECOVERING", state["state"])
            self.assertEqual("PENDING", entry["state"])
            self.assertEqual(0, entry["attempts"])
            self.assertEqual(1, entry["recoveries"])

    def test_controlled_stage_can_pause_resume_and_cancel_process_group(self):
        with tempfile.TemporaryDirectory() as directory:
            original_root = MODULE.ROOT
            MODULE.ROOT = pathlib.Path(directory)
            try:
                state_path = MODULE.ROOT / "checkpoint.json"
                contract = json.loads(json.dumps(self.contract))
                stage = contract["stages"][0]
                stage["command"] = [
                    sys.executable, "-c",
                    "import time; time.sleep(0.8); print('CONTROLLED_PASS')",
                ]
                stage["required_marker"] = "CONTROLLED_PASS"
                state = MODULE.initial_state(contract, {"head": "b" * 40, "clean": True})
                entry = state["stages"][stage["id"]]
                entry.update({"state": "RUNNING", "attempts": 1})
                MODULE.atomic_json(state_path, state)
                MODULE.request_control(contract, state_path, "PAUSED")

                def resume():
                    time.sleep(0.45)
                    MODULE.request_control(contract, state_path, "RUNNING")

                thread = threading.Thread(target=resume)
                thread.start()
                result, outcome = MODULE.run_controlled_stage(
                    contract, stage, state, entry, state_path, os.environ.copy())
                thread.join()
                self.assertEqual("COMPLETED", outcome)
                self.assertEqual(0, result.returncode)
                self.assertIn("CONTROLLED_PASS", result.stdout)

                cancel_state = MODULE.initial_state(contract, {"head": "c" * 40, "clean": True})
                cancel_entry = cancel_state["stages"][stage["id"]]
                cancel_entry.update({"state": "RUNNING", "attempts": 1})
                MODULE.atomic_json(state_path, cancel_state)
                MODULE.request_control(contract, state_path, "CANCELLED")
                result, outcome = MODULE.run_controlled_stage(
                    contract, stage, cancel_state, cancel_entry, state_path, os.environ.copy())
                self.assertEqual("CANCELLED", outcome)
                self.assertNotEqual(0, result.returncode)
            finally:
                MODULE.ROOT = original_root


if __name__ == "__main__":
    unittest.main()
