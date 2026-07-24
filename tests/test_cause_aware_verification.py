import unittest

from onsure_core.cause_aware_verification import (
    build_sample_oruda_report_profile,
    build_sample_run,
    verify_program_run,
    verify_program_run_loop,
)


class CauseAwareVerificationTest(unittest.TestCase):
    def setUp(self):
        self.profile = build_sample_oruda_report_profile()

    def test_baseline_allows_and_creates_no_memory_candidates(self):
        result = verify_program_run(self.profile, build_sample_run())

        self.assertEqual("ALLOW", result["decision"])
        self.assertEqual([], result["memory_candidates"])

    def test_missing_scene_manifest_blocks_with_cause_and_target(self):
        result = verify_program_run(self.profile, build_sample_run(omit_scene_manifest=True))

        self.assertEqual("BLOCK", result["decision"])
        self.assertIn("OUI", result["remediation_targets"])
        self.assertEqual("REQUIRED_OUTPUT_FIELD_MISSING", result["findings"][0]["code"])
        self.assertEqual("failure_memory", result["memory_candidates"][0]["memory_kind"])

    def test_profile_requires_all_four_target_program_routes(self):
        run = build_sample_run()
        run["routes"].pop("odesign_runtime")

        result = verify_program_run(self.profile, run)

        self.assertEqual("BLOCK", result["decision"])
        self.assertTrue(any(item["code"] == "PROGRAM_ROUTE_MISSING" for item in result["findings"]))

    def test_three_loop_verification_is_stable_for_oui_omission(self):
        result = verify_program_run_loop(self.profile, build_sample_run(omit_scene_manifest=True), loops=3)

        self.assertEqual("BLOCK", result["decision"])
        self.assertTrue(result["loop"]["stable"])
        self.assertEqual(3, len(result["loop"]["iterations"]))
        self.assertEqual(["REQUIRED_OUTPUT_FIELD_MISSING"], result["loop"]["iterations"][0]["finding_codes"])
        self.assertEqual(["OUI"], result["loop"]["iterations"][2]["remediation_targets"])

    def test_parent_drift_blocks_middle_fixture_replay(self):
        run = build_sample_run()
        run["stage_outputs"]["design"]["parent_hash"] = "0" * 64

        result = verify_program_run(self.profile, run)

        self.assertEqual("BLOCK", result["decision"])
        self.assertTrue(any(item["code"] == "STAGE_PARENT_HASH_MISSING" for item in result["findings"]))

    def test_body_drift_blocks_regenerated_stage(self):
        run = build_sample_run()
        run["stage_outputs"]["page_spec"]["body"]["intent"] = "changed after hash"

        result = verify_program_run(self.profile, run)

        self.assertEqual("BLOCK", result["decision"])
        self.assertTrue(any(item["code"] == "STAGE_BODY_DRIFT" for item in result["findings"]))

    def test_missing_formal_procedure_blocks_design(self):
        run = build_sample_run()
        run["stage_outputs"]["design"]["procedure"]["steps"].pop()

        result = verify_program_run(self.profile, run)

        self.assertEqual("BLOCK", result["decision"])
        self.assertIn("ODesign", result["remediation_targets"])
        self.assertTrue(any(item["code"] == "FORMAL_PROCEDURE_MISSING" for item in result["findings"]))

    def test_pending_final_gate_blocks_completion(self):
        result = verify_program_run(self.profile, build_sample_run(pending_gate="otester"))

        self.assertEqual("BLOCK", result["decision"])
        self.assertTrue(any(item["code"] == "FINAL_GATE_NOT_PASS" for item in result["findings"]))

    def test_final_binding_drift_blocks_completion(self):
        run = build_sample_run()
        run["final_output"]["binding"]["canonical_run_hash"] = "0" * 64

        result = verify_program_run(self.profile, run)

        self.assertEqual("BLOCK", result["decision"])
        self.assertTrue(any(item["code"] == "RENDER_OR_OUTPUT_BINDING_MISSING" for item in result["findings"]))


if __name__ == "__main__":
    unittest.main()
