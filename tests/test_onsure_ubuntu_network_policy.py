from __future__ import annotations

import copy
import json
import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import validate_onsure_ubuntu_network_policy as network  # noqa: E402


class ONSureUbuntuNetworkPolicyTest(unittest.TestCase):
    def documents(self) -> tuple[dict[str, object], dict[str, object]]:
        return (
            json.loads(network.PLAN.read_text(encoding="utf-8")),
            json.loads(network.OBSERVATION.read_text(encoding="utf-8")),
        )

    def test_exact_remediation_is_ready_but_not_executed(self):
        result = network.validate(*self.documents())
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertTrue(result["remediation_ready"])
        self.assertEqual("NOT_RUN", result["execution_state"])
        self.assertFalse(result["observed_production_ready"])

    def test_ascending_delete_order_is_rejected(self):
        plan, observation = self.documents()
        changed = copy.deepcopy(plan)
        changed["commands_proposed_not_executed"] = [
            "sudo ufw --force delete 3", "sudo ufw --force delete 6"
        ]
        result = network.validate(changed, observation)
        self.assertIn("UFW_DESCENDING_DELETE_SEQUENCE", result["violations"])

    def test_ssh_or_http_mutation_is_rejected(self):
        plan, observation = self.documents()
        changed = copy.deepcopy(plan)
        changed["out_of_scope_rules_must_remain_unchanged"] = ["22/tcp"]
        result = network.validate(changed, observation)
        self.assertIn("UFW_OUT_OF_SCOPE_MUTATION", result["violations"])

    def test_non_loopback_hba_fails_precondition(self):
        plan, observation = self.documents()
        changed = copy.deepcopy(observation)
        changed["postgresql"]["hba_non_loopback_host_rule_present"] = True
        result = network.validate(plan, changed)
        self.assertIn("POSTGRESQL_LOOPBACK_PRECONDITION", result["violations"])


if __name__ == "__main__":
    unittest.main()
