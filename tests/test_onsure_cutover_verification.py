import hashlib
import pathlib
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import verify_onsure_cutover as cutover  # noqa: E402


class ONSureCutoverVerificationTest(unittest.TestCase):
    def test_digest_mapping_passes_and_tampering_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            source = root / "source"
            candidate = root / "candidate"
            source.mkdir()
            candidate.mkdir()
            raw = b"ONSure cutover fixture\n"
            (source / "file.txt").write_bytes(raw)
            (candidate / "file.txt").write_bytes(raw)
            manifest = {
                "manifest_self_reference": {"path": "manifest.json"},
                "files": [{
                    "current_path": "file.txt",
                    "future_path_candidate": "products/onsure/file.txt",
                    "sha256": hashlib.sha256(raw).hexdigest(),
                    "size_bytes": len(raw),
                    "git_mode": "100644",
                }],
            }
            self.assertEqual(
                "PASS_NONFINAL", cutover.verify_tree(source, candidate, manifest)["decision"]
            )
            (candidate / "file.txt").write_bytes(b"tampered")
            result = cutover.verify_tree(source, candidate, manifest)
            self.assertEqual("FAIL", result["decision"])
            self.assertTrue(
                any(value.startswith("CANDIDATE_DIGEST_MISMATCH") for value in result["violations"])
            )


if __name__ == "__main__":
    unittest.main()
