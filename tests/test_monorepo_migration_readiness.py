import pathlib
import stat
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import onsure_monorepo_manifest as manifest  # noqa: E402
import onsure_product_root as product_root  # noqa: E402
import validate_monorepo_migration_readiness as readiness  # noqa: E402


class MonorepoMigrationManifestTest(unittest.TestCase):
    def test_future_path_preserves_repository_relative_path(self):
        self.assertEqual(
            "products/onsure/modules/onsure-core/src/main/java/io/onsure/platform/ONSureCli.java",
            manifest.future_path("modules/onsure-core/src/main/java/io/onsure/platform/ONSureCli.java"),
        )

    def test_secret_scan_reports_reason_without_secret_value(self):
        raw = b"token=" + b"ghp_" + b"abcdefghijklmnopqrstuvwxyz1234567890AB"
        result = manifest.sensitivity(pathlib.Path("fixture.txt"), raw)
        self.assertEqual("HIGH_RISK_PATTERN_MATCH", result["status"])
        self.assertEqual(["GITHUB_TOKEN"], result["reasons"])
        self.assertNotIn("abcdefghijklmnopqrstuvwxyz", str(result))

    def test_license_detector_keeps_raw_undeclared_state_and_honors_spdx(self):
        self.assertEqual("UNDECLARED", manifest.detect_license(b"plain source"))
        self.assertEqual(
            "Apache-2.0",
            manifest.detect_license(b"// SPDX-License-Identifier: Apache-2.0\n"),
        )

    def test_untracked_modes_match_git_index_modes_before_and_after_commit(self):
        self.assertEqual("100644", manifest.normalized_git_mode(stat.S_IFREG | 0o644))
        self.assertEqual("100755", manifest.normalized_git_mode(stat.S_IFREG | 0o755))
        self.assertEqual("120000", manifest.normalized_git_mode(stat.S_IFLNK | 0o777))

    @unittest.skipUnless((ROOT / ".git").exists(), "candidate list needs Git index metadata")
    def test_generated_runtime_and_vscode_outputs_are_not_migration_inputs(self):
        rules = (ROOT / ".gitignore").read_text(encoding="utf-8").splitlines()
        self.assertIn(".onsure/", rules)
        self.assertIn("vscode-extension/node_modules/", rules)
        self.assertIn("vscode-extension/*.vsix", rules)
        candidates = {path.relative_to(ROOT).as_posix() for path in manifest.candidate_paths()}
        self.assertFalse(any(path.startswith(".onsure/") for path in candidates))

    def test_absolute_workspace_detection_avoids_escaped_message_false_positive(self):
        self.assertTrue(
            readiness.contains_absolute_workspace("/" + "workspace/ONSure/pom.xml")
        )
        self.assertTrue(
            readiness.contains_absolute_workspace("/" + "home/user/src/file.java")
        )
        windows_path = r"C:" + "\\\\" + r"Users\\user\\repo"
        self.assertTrue(readiness.contains_absolute_workspace(windows_path))
        self.assertFalse(readiness.contains_absolute_workspace(
            "/var/lib/onsure/workspace/.onsure/llm-evidence"
        ))
        self.assertFalse(readiness.contains_absolute_workspace(r"error:\\n"))

    def test_explicit_product_root_is_absolute_bounded_and_marker_checked(self):
        self.assertEqual(ROOT, product_root.resolve_product_root(ROOT))
        with self.assertRaisesRegex(ValueError, "MUST_BE_ABSOLUTE"):
            product_root.resolve_product_root(pathlib.Path("relative-root"))
        with self.assertRaisesRegex(ValueError, "PATH_ESCAPE"):
            product_root.product_path(ROOT, "../outside")


if __name__ == "__main__":
    unittest.main()
