import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import validate_onsure_build_boundary as boundary  # noqa: E402


class ONSureBuildBoundaryTest(unittest.TestCase):
    def test_repository_build_and_module_boundaries_are_consistent(self):
        result = boundary.validate()
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertEqual(0, result["module_dependency_cycle_count"])
        self.assertEqual(
            result["main_source_file_count"], result["main_source_single_owner_count"]
        )
        self.assertEqual(0, result["forbidden_import_edge_count"])

    def test_cycle_detection_fails_for_reverse_module_dependency(self):
        graph = {
            "onsure-core": {"onsure-cli"},
            "onsure-cli": {"onsure-core"},
        }
        self.assertEqual(
            [["onsure-cli", "onsure-core"]], boundary.graph_cycles(graph)
        )

    def test_source_patterns_do_not_grant_unrelated_ownership(self):
        self.assertTrue(
            boundary.matches(
                "io/onsure/platform/oruda/**",
                "io/onsure/platform/oruda/OrudaEvidenceRegistry.java",
            )
        )
        self.assertFalse(
            boundary.matches(
                "io/onsure/platform/oruda/**", "io/onsure/platform/ValidationEngine.java"
            )
        )


if __name__ == "__main__":
    unittest.main()
