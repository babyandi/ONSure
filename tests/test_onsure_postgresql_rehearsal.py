from __future__ import annotations

import io
import pathlib
import shutil
import sys
import tarfile
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import rehearse_onsure_postgresql as rehearsal  # noqa: E402


class ONSurePostgresqlRehearsalTest(unittest.TestCase):
    def test_package_extraction_rejects_path_escape(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            archive = root / "escape.tar.gz"
            with tarfile.open(archive, "w:gz") as body:
                entry = tarfile.TarInfo("../../escape")
                content = b"escape"
                entry.size = len(content)
                body.addfile(entry, io.BytesIO(content))
            with self.assertRaisesRegex(ValueError, "PACKAGE_ARCHIVE_PATH_ESCAPE"):
                rehearsal.extract_package(archive, root / "output")

    def test_package_extraction_rejects_symlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            archive = root / "symlink.tar.gz"
            with tarfile.open(archive, "w:gz") as body:
                entry = tarfile.TarInfo("unsafe-link")
                entry.type = tarfile.SYMTYPE
                entry.linkname = "/etc/passwd"
                body.addfile(entry)
            with self.assertRaisesRegex(ValueError, "PACKAGE_ARCHIVE_NONREGULAR_ENTRY"):
                rehearsal.extract_package(archive, root / "output")

    def test_server_tool_discovery_is_explicit(self):
        installed = any(path.is_file() for path in pathlib.Path("/usr/lib/postgresql").glob("*/bin/postgres"))
        if not installed and shutil.which("postgres") is None:
            self.skipTest("PostgreSQL server binaries are optional for the unit suite")
        path = rehearsal.postgres_bin()
        self.assertTrue((path / "postgres").is_file())
        self.assertTrue((path / "initdb").is_file())


if __name__ == "__main__":
    unittest.main()
