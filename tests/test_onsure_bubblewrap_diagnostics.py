from __future__ import annotations

import pathlib
import sys
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import onsure_bubblewrap_diagnostics as diagnostics  # noqa: E402


class ONSureBubblewrapDiagnosticsTest(unittest.TestCase):
    def test_loopback_permission_error_is_environment_block(self):
        decision, reason = diagnostics.classify_probe(
            1, "bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted"
        )
        self.assertEqual("BLOCKED_ENVIRONMENT", decision)
        self.assertEqual("BWRAP_LOOPBACK_PERMISSION_DENIED", reason)

    def test_success_never_grants_final_authority(self):
        with mock.patch.object(diagnostics, "read_sysctls", return_value={}):
            report = diagnostics.build_report(0, "", "bubblewrap 0.11.0")
        self.assertEqual("PASS_NONFINAL", report["decision"])
        self.assertFalse(report["security_fallback_allowed"])
        self.assertFalse(report["github_actions_required"])
        self.assertFalse(report["final_claim_allowed"])

    def test_missing_binary_has_explicit_reason(self):
        with mock.patch.object(diagnostics, "read_sysctls", return_value={}):
            report = diagnostics.build_report(
                127, "bwrap executable not found", "NOT_INSTALLED"
            )
        self.assertEqual("BLOCKED_ENVIRONMENT", report["decision"])
        self.assertEqual("BWRAP_NOT_INSTALLED", report["reason_code"])


if __name__ == "__main__":
    unittest.main()
