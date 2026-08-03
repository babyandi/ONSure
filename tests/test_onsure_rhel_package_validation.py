from __future__ import annotations

import io
import pathlib
import sys
import tarfile
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import validate_onsure_rhel_package as package_validation  # noqa: E402


class ONSureRhelPackageValidationTest(unittest.TestCase):
    def test_normalized_path_rejects_escape(self):
        with self.assertRaisesRegex(ValueError, "PACKAGE_PATH_ESCAPE"):
            package_validation.normalized("../../etc/passwd")

    def test_validator_rejects_nonregular_archive_entry(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temporary:
            package = pathlib.Path(temporary) / "bad.tar.gz"
            with tarfile.open(package, "w:gz") as archive:
                entry = tarfile.TarInfo("./unsafe")
                entry.type = tarfile.SYMTYPE
                entry.linkname = "/etc/passwd"
                archive.addfile(entry)
            with self.assertRaisesRegex(ValueError, "PACKAGE_NONREGULAR_ENTRY"):
                package_validation.validate(package)

    def test_validator_rejects_nonroot_archive_owner(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temporary:
            package = pathlib.Path(temporary) / "bad-owner.tar.gz"
            with tarfile.open(package, "w:gz") as archive:
                content = b"unsafe"
                entry = tarfile.TarInfo("./unsafe")
                entry.uid = 1000
                entry.gid = 1000
                entry.size = len(content)
                archive.addfile(entry, io.BytesIO(content))
            with self.assertRaisesRegex(ValueError, "PACKAGE_NONROOT_OWNERSHIP"):
                package_validation.validate(package)


if __name__ == "__main__":
    unittest.main()
