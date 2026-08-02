from __future__ import annotations

import io
import pathlib
import sys
import tarfile
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_npm_airgap as npm_airgap  # noqa: E402


class ONSureNpmAirgapTest(unittest.TestCase):
    def test_safe_members_rejects_path_traversal_and_links(self):
        for name, link in (("../escape", False), ("cache/link", True)):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                archive_path = pathlib.Path(directory) / "unsafe.tar"
                with tarfile.open(archive_path, "w") as archive:
                    info = tarfile.TarInfo(name)
                    if link:
                        info.type = tarfile.SYMTYPE
                        info.linkname = "target"
                        archive.addfile(info)
                    else:
                        payload = b"unsafe"
                        info.size = len(payload)
                        archive.addfile(info, io.BytesIO(payload))
                with tarfile.open(archive_path, "r") as archive:
                    with self.assertRaisesRegex(ValueError, "PATH_INVALID"):
                        npm_airgap.safe_members(archive)


if __name__ == "__main__":
    unittest.main()
