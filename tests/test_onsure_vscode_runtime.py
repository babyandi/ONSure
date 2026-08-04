from __future__ import annotations

import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import rehearse_onsure_vscode_runtime as runtime  # noqa: E402


class ONSureVscodeRuntimeTest(unittest.TestCase):
    def test_content_free_ready_runtime_passes(self):
        result = runtime.validate(
            {"contract": "ONSURE_LOCAL_AUTHENTICATED_API_V1", "state": "RUNNING"},
            {"contract": "ONSURE_LLM_GATEWAY_V1", "state": "RUNNING",
             "provider_health": "READY", "provider": "local-mock"},
            {"request_count": 1, "success_count": 1, "failure_count": 0,
             "total_tokens": 23, "actual_cost_micros": 23, "last_sequence": 1,
             "chain_valid": True, "prompt_or_completion_content_recorded": False},
        )
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertFalse(result["tokens_disclosed"])
        self.assertFalse(result["content_recorded"])

    def test_invalid_chain_or_content_storage_fails(self):
        result = runtime.validate(
            {"contract": "ONSURE_LOCAL_AUTHENTICATED_API_V1", "state": "RUNNING"},
            {"contract": "ONSURE_LLM_GATEWAY_V1", "state": "RUNNING",
             "provider_health": "READY"},
            {"request_count": 0, "success_count": 0, "failure_count": 0,
             "total_tokens": 0, "actual_cost_micros": 0, "last_sequence": 0,
             "chain_valid": False, "prompt_or_completion_content_recorded": True},
        )
        self.assertEqual("FAIL", result["decision"])
        self.assertIn("LLM_RECEIPT_CHAIN_INVALID", result["errors"])
        self.assertIn("LLM_CONTENT_STORAGE_BOUNDARY_VIOLATION", result["errors"])

    def test_non_loopback_url_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "LOOPBACK_URL_REQUIRED"):
            runtime.loopback_base("https://example.invalid:443")


if __name__ == "__main__":
    unittest.main()
