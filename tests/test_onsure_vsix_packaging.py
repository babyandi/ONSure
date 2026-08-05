from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest
import warnings
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import package_onsure_vsix as packaging  # noqa: E402


class ONSureVsixPackagingTest(unittest.TestCase):
    def test_canonical_package_is_byte_reproducible(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            first = root / "first.vsix"
            second = root / "second.vsix"
            content_types_first = b'''<?xml version="1.0" encoding="utf-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension=".z" ContentType="text/z"/><Default Extension=".a" ContentType="text/a"/></Types>
'''
            content_types_second = b'''<?xml version="1.0" encoding="utf-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default ContentType="text/a" Extension=".a"/><Default ContentType="text/z" Extension=".z"/></Types>
'''
            with zipfile.ZipFile(first, "w") as archive:
                archive.writestr("[Content_Types].xml", content_types_first)
                archive.writestr("extension/z.txt", "z")
                archive.writestr("extension/a.txt", "a")
            with zipfile.ZipFile(second, "w") as archive:
                archive.writestr("[Content_Types].xml", content_types_second)
                old = zipfile.ZipInfo("extension/a.txt", (2001, 2, 3, 4, 5, 6))
                archive.writestr(old, "a")
                archive.writestr("extension/z.txt", "z")
            first_report = packaging.canonicalize_vsix(first)
            second_report = packaging.canonicalize_vsix(second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(first_report["package_sha256"], second_report["package_sha256"])
            self.assertEqual(first_report["content_sha256"], second_report["content_sha256"])
            self.assertEqual("1980-01-01T00:00:00Z", first_report["zip_timestamp"])
            self.assertFalse(first_report["final_claim_allowed"])

    def test_invalid_or_duplicate_package_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            with self.assertRaisesRegex(ValueError, "VSIX_PACKAGE_FILE_INVALID"):
                packaging.canonicalize_vsix(root / "missing.vsix")
            duplicate = root / "duplicate.vsix"
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(duplicate, "w") as archive:
                    archive.writestr("same", "one")
                    archive.writestr("same", "two")
            with self.assertRaisesRegex(ValueError, "VSIX_DUPLICATE_ENTRY"):
                packaging.canonicalize_vsix(duplicate)


if __name__ == "__main__":
    unittest.main()
