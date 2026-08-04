from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_vscode_extension", ROOT / "scripts" / "validate-vscode-extension.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class VscodeExtensionValidatorTest(unittest.TestCase):
    def test_isolated_gate_packaging_copies_the_canonical_packager(self) -> None:
        required = (
            'cp "$ROOT/scripts/package_onsure_vsix.py" '
            '"$OUT/scripts/package_onsure_vsix.py"'
        )
        for relative in ("scripts/onsure-local-gate.sh", "scripts/onsure-final-stage.sh"):
            self.assertIn(required, (ROOT / relative).read_text(encoding="utf-8"), relative)
        packager = (ROOT / "scripts/package_onsure_vsix.py").read_text(encoding="utf-8")
        self.assertIn('parser.add_argument("--out"', packager)
        self.assertIn('"--out", str(output)', packager)

    def test_valid_vsix_is_digest_bound_and_excludes_test_sources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            extension = pathlib.Path(directory)
            package = {"name": "onsure", "version": "0.2.0"}
            archive_path = extension / "onsure-0.2.0.vsix"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("extension/package.json", json.dumps(package))
                for name in (
                    "extension/extension.js", "extension/extension-core.js",
                    "extension/readme.md", "extension/media/onsure.svg",
                ):
                    archive.writestr(name, "fixture")
            errors: list[str] = []
            state, evidence = MODULE.validate_vsix(
                package, errors, True, extension=extension, root=extension)
            self.assertEqual("PASS", state)
            self.assertEqual([], errors)
            self.assertEqual(64, len(evidence["vsix_sha256"]))
            self.assertGreater(evidence["vsix_size_bytes"], 0)

    def test_missing_or_incomplete_vsix_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            extension = pathlib.Path(directory)
            errors: list[str] = []
            state, _ = MODULE.validate_vsix(
                {"name": "onsure", "version": "0.2.0"}, errors, True,
                extension=extension, root=extension)
            self.assertEqual("FAIL", state)
            self.assertIn("VSIX_PACKAGE_REQUIRED_NOT_FOUND", errors)

            with zipfile.ZipFile(extension / "broken.vsix", "w") as archive:
                archive.writestr("extension/package.json", "{}")
            errors = []
            state, _ = MODULE.validate_vsix(
                {"name": "onsure", "version": "0.2.0"}, errors, True,
                extension=extension, root=extension)
            self.assertEqual("FAIL", state)
            self.assertTrue(any(value.startswith("VSIX_REQUIRED_ENTRY_MISSING") for value in errors))
            self.assertIn("VSIX_MANIFEST_IDENTITY_MISMATCH", errors)


if __name__ == "__main__":
    unittest.main()
