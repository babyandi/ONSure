from __future__ import annotations

import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import validate_onsure_rhel_package as package_validation  # noqa: E402


class ONSureUbuntuPackageValidationTest(unittest.TestCase):
    def test_ubuntu_source_set_uses_shared_units_and_ubuntu_readme(self):
        paths = {
            path.relative_to(ROOT).as_posix()
            for path in package_validation.source_files("ubuntu")
        }
        self.assertIn("scripts/package_onsure_systemd.sh", paths)
        self.assertIn("deploy/rhel/onsure.service", paths)
        self.assertIn("deploy/ubuntu/README.md", paths)
        self.assertNotIn("deploy/rhel/README.md", paths)

    def test_unknown_platform_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "PACKAGE_PLATFORM_UNSUPPORTED"):
            package_validation.source_files("debian")


if __name__ == "__main__":
    unittest.main()
