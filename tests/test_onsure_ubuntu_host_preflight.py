from __future__ import annotations

import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_ubuntu_host_preflight as preflight  # noqa: E402


def ready_observation() -> dict[str, object]:
    return {
        "os_release": {"ID": "ubuntu", "VERSION_ID": "24.04"},
        "services": {
            name: {"active": True, "enabled": True} for name in preflight.SERVICES
        },
        "systemd_exposure_scores": {name: 2.8 for name in preflight.SERVICES},
        "listeners": {47311: ["127.0.0.1"], 47312: ["::ffff:127.0.0.1"], 5432: ["127.0.0.1"]},
        "apparmor": {"module_enabled": True, "profile_status": "NOT_RUN_INSUFFICIENT_PRIVILEGE"},
        "ufw": {"status": "NOT_RUN_INSUFFICIENT_PRIVILEGE"},
        "runtime_config": {"exists": True, "mode": "0600"},
    }


class ONSureUbuntuHostPreflightTest(unittest.TestCase):
    def test_read_only_runtime_passes_nonfinal_with_explicit_policy_blockers(self):
        result = preflight.evaluate(ready_observation())
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertFalse(result["runtime_config"]["secret_values_read"])
        self.assertFalse(result["runtime_config"]["path_disclosed"])
        self.assertIn("UFW_POLICY_NOT_VERIFIED", result["production_blockers"])
        self.assertFalse(result["host_modified"])

    def test_public_listener_fails(self):
        observation = ready_observation()
        observation["listeners"][47311] = ["0.0.0.0"]
        result = preflight.evaluate(observation)
        self.assertEqual("FAIL", result["decision"])
        self.assertIn("NON_LOOPBACK_LISTENER:47311", result["errors"])

    def test_missing_service_and_weak_config_mode_fail(self):
        observation = ready_observation()
        observation["services"]["onsure-runtime.service"]["active"] = False
        observation["runtime_config"]["mode"] = "0644"
        result = preflight.evaluate(observation)
        self.assertEqual("FAIL", result["decision"])
        self.assertIn("SERVICE_NOT_ACTIVE:onsure-runtime.service", result["errors"])
        self.assertIn("RUNTIME_CONFIG_MODE_NOT_0600", result["errors"])

    def test_listener_parser_handles_ipv4_mapped_ipv6(self):
        parsed = preflight.parse_listeners(
            "LISTEN 0 32 [::ffff:127.0.0.1]:47312 *:*\n"
            "LISTEN 0 200 127.0.0.1:5432 0.0.0.0:*\n"
        )
        self.assertEqual(["[::ffff:127.0.0.1]"], parsed[47312])
        self.assertEqual(["127.0.0.1"], parsed[5432])

    def test_user_unit_above_production_exposure_is_explicit_blocker(self):
        observation = ready_observation()
        observation["systemd_exposure_scores"]["onsure-runtime.service"] = 6.7
        result = preflight.evaluate(observation)
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertIn(
            "SYSTEMD_EXPOSURE_ABOVE_PRODUCTION_MAXIMUM:onsure-runtime.service",
            result["production_blockers"],
        )
        self.assertFalse(
            result["systemd_security"]["onsure-runtime.service"][
                "within_production_maximum"
            ]
        )

    def test_systemd_exposure_parser_fails_closed(self):
        self.assertEqual(
            2.8,
            preflight.parse_systemd_exposure(
                "Overall exposure level for onsure.service: 2.8 OK :-)"
            ),
        )
        self.assertIsNone(preflight.parse_systemd_exposure("score unavailable"))


if __name__ == "__main__":
    unittest.main()
