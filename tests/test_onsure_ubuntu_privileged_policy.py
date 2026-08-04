from __future__ import annotations

import copy
import json
import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import validate_onsure_ubuntu_privileged_policy as policy  # noqa: E402


class ONSureUbuntuPrivilegedPolicyTest(unittest.TestCase):
    def evidence(self) -> dict[str, object]:
        return json.loads(policy.EVIDENCE.read_text(encoding="utf-8"))

    def test_owner_observation_is_truthfully_fail_closed(self):
        result = policy.validate(self.evidence())
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertFalse(result["observed_production_ready"])
        self.assertEqual(2, len(result["remediation_required"]))

    def test_public_postgresql_rule_cannot_be_hidden(self):
        changed = copy.deepcopy(self.evidence())
        changed["ufw"]["postgresql_public_allow"] = False
        result = policy.validate(changed)
        self.assertEqual("FAIL", result["decision"])
        self.assertIn("UFW_POLICY_OBSERVATION", result["violations"])

    def test_dedicated_profile_cannot_be_claimed_without_observation(self):
        changed = copy.deepcopy(self.evidence())
        changed["apparmor"]["dedicated_onsure_profile_enforced"] = True
        result = policy.validate(changed)
        self.assertEqual("FAIL", result["decision"])
        self.assertIn("APPARMOR_ONSURE_GAP_NOT_RECORDED", result["violations"])

    def test_numbered_rule_delete_order_is_bound(self):
        changed = copy.deepcopy(self.evidence())
        changed["ufw"]["postgresql_public_rule_ids_delete_order"] = [3, 6]
        result = policy.validate(changed)
        self.assertEqual("FAIL", result["decision"])
        self.assertIn("UFW_NUMBERED_DELETE_ORDER", result["violations"])


if __name__ == "__main__":
    unittest.main()
