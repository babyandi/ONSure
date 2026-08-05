from __future__ import annotations

import hashlib
import json
import pathlib
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_airgap_pack as airgap  # noqa: E402


class ONSureAirgapPackTest(unittest.TestCase):
    def test_plan_build_and_verify_use_local_digest_bound_artifacts_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            repository = root / "repository"
            artifact_root = repository / "example" / "group" / "sample" / "1.0"
            artifact_root.mkdir(parents=True)
            jar = artifact_root / "sample-1.0.jar"
            pom = artifact_root / "sample-1.0.pom"
            jar.write_bytes(b"synthetic-jar")
            pom.write_text("<project/>\n", encoding="utf-8")
            sbom = root / "sbom.json"
            sbom.write_text(json.dumps({"components": [{
                "purl": "pkg:maven/example.group/sample@1.0?type=jar",
                "hashes": [{"alg": "SHA-256", "content": hashlib.sha256(jar.read_bytes()).hexdigest()}],
            }]}), encoding="utf-8")
            original_descriptors = airgap.BUILD_DESCRIPTORS
            airgap.BUILD_DESCRIPTORS = ()
            try:
                manifest = airgap.plan(repository, sbom)
                self.assertTrue(manifest["maven_payload_complete"])
                self.assertFalse(manifest["network_access_used"])
                archive = root / "dependencies.tar"
                result = airgap.build(manifest, archive)
                self.assertEqual("PASS_NONFINAL", result["decision"])
                self.assertEqual(2, result["verified_entry_count"])
                self.assertFalse(result["release_authority"])
                self.assertEqual(result, airgap.verify(archive))
            finally:
                airgap.BUILD_DESCRIPTORS = original_descriptors

    def test_missing_and_digest_mismatched_artifacts_are_not_packable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            repository = root / "repository"
            artifact_root = repository / "example" / "sample" / "1"
            artifact_root.mkdir(parents=True)
            (artifact_root / "sample-1.jar").write_bytes(b"wrong")
            sbom = root / "sbom.json"
            sbom.write_text(json.dumps({"components": [{
                "purl": "pkg:maven/example/sample@1",
                "hashes": [{"alg": "SHA-256", "content": "a" * 64}],
            }]}), encoding="utf-8")
            original_descriptors = airgap.BUILD_DESCRIPTORS
            airgap.BUILD_DESCRIPTORS = ()
            try:
                manifest = airgap.plan(repository, sbom)
                self.assertFalse(manifest["maven_payload_complete"])
                self.assertTrue(manifest["missing"])
                self.assertTrue(manifest["digest_mismatches"])
                with self.assertRaisesRegex(ValueError, "INCOMPLETE"):
                    airgap.build(manifest, root / "blocked.tar")

                pom = artifact_root / "sample-1.pom"
                pom.write_text("<project/>\n", encoding="utf-8")
                manifest = airgap.plan(repository, sbom)
                manifest["digest_mismatches"] = []
                manifest["maven_payload_complete"] = True
                (artifact_root / "sample-1.jar").write_bytes(b"drift-after-plan")
                output = root / "drift-blocked.tar"
                with self.assertRaisesRegex(ValueError, "SOURCE_DRIFT"):
                    airgap.build(manifest, output)
                self.assertFalse(output.exists())
            finally:
                airgap.BUILD_DESCRIPTORS = original_descriptors

    def test_offline_repository_pack_is_digest_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            repository = root / "repository"
            artifact = repository / "example/sample/1/sample-1.jar"
            artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b"offline")
            archive = root / "repository.tar"
            built = airgap.build_repository_pack(repository, archive)
            verified = airgap.verify_repository_pack(archive)
            self.assertEqual("PASS_NONFINAL", built["decision"])
            self.assertEqual(1, verified["verified_entry_count"])


if __name__ == "__main__":
    unittest.main()
