import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import onsure_monorepo_manifest as manifest  # noqa: E402
import validate_monorepo_migration_readiness as readiness  # noqa: E402


class MonorepoMigrationManifestTest(unittest.TestCase):
    def test_future_path_preserves_repository_relative_path(self):
        self.assertEqual(
            "products/onsure/src/main/java/io/onsure/platform/ONSureCli.java",
            manifest.future_path("src/main/java/io/onsure/platform/ONSureCli.java"),
        )

    def test_secret_scan_reports_reason_without_secret_value(self):
        raw = b"token=" + b"ghp_" + b"abcdefghijklmnopqrstuvwxyz1234567890AB"
        result = manifest.sensitivity(pathlib.Path("fixture.txt"), raw)
        self.assertEqual("HIGH_RISK_PATTERN_MATCH", result["status"])
        self.assertEqual(["GITHUB_TOKEN"], result["reasons"])
        self.assertNotIn("abcdefghijklmnopqrstuvwxyz", str(result))

    def test_license_defaults_to_undeclared_and_honors_spdx(self):
        self.assertEqual("UNDECLARED", manifest.detect_license(b"plain source"))
        self.assertEqual(
            "Apache-2.0",
            manifest.detect_license(b"// SPDX-License-Identifier: Apache-2.0\n"),
        )

    def test_absolute_workspace_detection_avoids_escaped_message_false_positive(self):
        self.assertTrue(
            readiness.contains_absolute_workspace("/" + "workspace/ONSure/pom.xml")
        )
        self.assertTrue(
            readiness.contains_absolute_workspace("/" + "home/user/src/file.java")
        )
        windows_path = r"C:" + "\\\\" + r"Users\\user\\repo"
        self.assertTrue(readiness.contains_absolute_workspace(windows_path))
        self.assertFalse(readiness.contains_absolute_workspace(r"error:\\n"))


if __name__ == "__main__":
    unittest.main()
