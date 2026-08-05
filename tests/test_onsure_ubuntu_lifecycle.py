from __future__ import annotations

import io
import pathlib
import sys
import tarfile
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_ubuntu_lifecycle as lifecycle  # noqa: E402


class ONSureUbuntuLifecycleTest(unittest.TestCase):
    def package(self, root: pathlib.Path, unsafe: bool = False) -> pathlib.Path:
        package = root / "candidate.tar.gz"
        payload = b"candidate"
        name = "../escape" if unsafe else "opt/onsure/app/candidate.jar"
        checksum = __import__("hashlib").sha256(payload).hexdigest()
        sums = f"{checksum}  ./{name}\n".encode()
        with tarfile.open(package, "w:gz") as archive:
            for member_name, raw in ((name, payload), ("SHA256SUMS", sums)):
                info = tarfile.TarInfo(member_name)
                info.size = len(raw)
                info.mode = 0o644
                archive.addfile(info, io.BytesIO(raw))
        return package

    def test_install_upgrade_and_rollback_are_digest_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            result = lifecycle.rehearse(
                self.package(root), root / "state", allow_external_state=True
            )
            self.assertEqual("PASS_NONFINAL", result["decision"])
            self.assertTrue(result["idempotent_reinstall"])
            self.assertEqual(result["install_release"], result["rollback_restored_release"])
            self.assertFalse(result["host_filesystem_modified"])

    def test_archive_path_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            with self.assertRaisesRegex(ValueError, "PACKAGE_PATH_ESCAPE"):
                lifecycle.package_contents(self.package(root, unsafe=True))

    def test_external_state_root_is_rejected_by_default(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            with self.assertRaisesRegex(ValueError, "OUTSIDE_PRODUCT_STATE"):
                lifecycle.rehearse(self.package(root), root / "state")


if __name__ == "__main__":
    unittest.main()
