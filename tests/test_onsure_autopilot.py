import importlib.util
import json
import pathlib
import tempfile
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


if __name__ == "__main__":
    unittest.main()
