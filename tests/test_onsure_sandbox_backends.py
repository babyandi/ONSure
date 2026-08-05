from __future__ import annotations

import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import onsure_sandbox_diagnostics as diagnostics  # noqa: E402


class ONSureSandboxBackendsTest(unittest.TestCase):
    def test_diagnostic_receipt_digest_changes_with_backend_evidence(self):
        receipt = {"contract": "TEST", "decision": "PASS_NONFINAL", "image_id": "sha256:a"}
        first = diagnostics.canonical_sha256(receipt)
        receipt["image_id"] = "sha256:b"
        self.assertNotEqual(first, diagnostics.canonical_sha256(receipt))

    def test_contract_allows_only_fail_closed_local_backends(self):
        contract = json.loads(
            (ROOT / "contracts/sandbox-boundary.v1.json").read_text(encoding="utf-8")
        )
        backends = contract["backends"]
        self.assertEqual("AUTO", backends["default"])
        self.assertEqual({"AUTO", "ROOTLESS_BWRAP", "OCI_DOCKER"}, set(backends["allowed"]))
        self.assertEqual("NEVER", backends["oci"]["image_pull"])
        self.assertEqual("NONE", backends["oci"]["network"])
        self.assertEqual("READ_ONLY", backends["oci"]["root_filesystem"])
        self.assertEqual("FORBIDDEN", backends["oci"]["docker_socket_mount"])

    def test_oci_launchers_pin_local_image_and_apply_security_controls(self):
        fixture = (ROOT / "scripts/fixture-sandbox-launcher.sh").read_text(encoding="utf-8")
        validation = (ROOT / "scripts/validation-sandbox-launcher.sh").read_text(encoding="utf-8")
        helper = (ROOT / "scripts/onsure-sandbox-backend.sh").read_text(encoding="utf-8")
        combined = fixture + validation
        for required in (
            "--pull never", "--network none", "--read-only", "--cap-drop ALL",
            "no-new-privileges:true", "apparmor=docker-default", "--pids-limit",
            "--memory", "--cpus", "--tmpfs", "--entrypoint /usr/bin/prlimit",
        ):
            self.assertIn(required, combined)
        self.assertIn("docker image inspect --format '{{.Id}}'", helper)
        self.assertIn("^sha256:[0-9a-f]{64}$", helper)
        self.assertNotIn("--privileged", combined)
        self.assertNotIn("--network host", combined)
        self.assertNotIn("/var/run/docker.sock", combined)

    def test_validation_image_and_probe_cover_declared_document_dependencies(self):
        image = (ROOT / "deploy/validation/Dockerfile").read_text(encoding="utf-8")
        launcher = (ROOT / "scripts/validation-sandbox-launcher.sh").read_text(encoding="utf-8")
        executor = (ROOT / "modules/onsure-core/src/main/java/io/onsure/platform/"
                    "SandboxedValidationStepExecutor.java").read_text(encoding="utf-8")
        for package in ("clamav", "fontconfig", "fonts-noto-cjk"):
            self.assertIn(package, image)
        self.assertIn(".onsure/internal/environment-probe.sh", launcher)
        self.assertIn("ONSURE_ENVIRONMENT_PROBE_MISSING", executor)
        self.assertIn("--font", executor)

    def test_diagnostic_propagates_an_isolated_temp_root_to_sandbox_children(self):
        source = (ROOT / "scripts/onsure_sandbox_diagnostics.py").read_text(encoding="utf-8")
        self.assertIn('TemporaryDirectory(prefix="onsure-sandbox-runtime-")', source)
        self.assertIn('"TMPDIR": sandbox_temp', source)
        self.assertIn('"ONSURE_TEMP_ROOT": sandbox_temp', source)

    def test_invalid_oci_image_reference_fails_closed_before_execution(self):
        command = """
set -euo pipefail
source scripts/onsure-sandbox-backend.sh
export ONSURE_VALIDATION_OCI_IMAGE='bad image;touch /tmp/escape'
if onsure_select_sandbox_backend OCI_DOCKER; then
  onsure_sandbox_backend_cleanup
  exit 9
fi
onsure_sandbox_backend_cleanup
"""
        result = subprocess.run(
            ["bash", "-c", command], cwd=ROOT, text=True, capture_output=True, check=False
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_runtime_probe_uses_immutable_oci_image_when_available(self):
        if shutil.which("docker") is None:
            self.skipTest("Docker is an optional validation backend")
        image = subprocess.run(
            ["docker", "image", "inspect", "onsure-validation-runtime:java17-node20-v1"],
            cwd=ROOT, capture_output=True, check=False,
        )
        if image.returncode != 0:
            self.skipTest("The offline validation image is not installed")
        with tempfile.TemporaryDirectory(prefix="onsure-oci-probe-") as temporary:
            environment = {"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "ONSURE_SANDBOX_PROBE": "1",
                           "ONSURE_VALIDATION_SANDBOX_BACKEND": "OCI_DOCKER",
                           "TMPDIR": temporary}
            result = subprocess.run(
                ["bash", "scripts/validation-sandbox-launcher.sh", temporary, "15", "true"],
                cwd=ROOT, env=environment, text=True, capture_output=True, check=False, timeout=30,
            )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertRegex(
            result.stdout, r"ONSURE_VALIDATION_SANDBOX_BACKEND OCI_DOCKER sha256:[0-9a-f]{64}"
        )


if __name__ == "__main__":
    unittest.main()
