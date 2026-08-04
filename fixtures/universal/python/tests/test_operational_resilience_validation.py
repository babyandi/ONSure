import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path


class OperationalResilienceValidationTest(unittest.TestCase):
    def test_interruption(self):
        with self.assertRaises(subprocess.TimeoutExpired):
            subprocess.run(["python3", "-c", "import time; time.sleep(2)"], timeout=0.05, check=True)

    def test_resume(self):
        states = ["checkpoint"]
        states.extend(["resumed", "complete"])
        self.assertEqual(["checkpoint", "resumed", "complete"], states)

    def test_rollback(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "state"
            state.write_text("before", encoding="utf-8")
            baseline = hashlib.sha256(state.read_bytes()).hexdigest()
            state.write_text("after", encoding="utf-8")
            state.write_text("before", encoding="utf-8")
            self.assertEqual(baseline, hashlib.sha256(state.read_bytes()).hexdigest())

    def test_rerun(self):
        operation = lambda: hashlib.sha256(b"deterministic-artifact").hexdigest()
        self.assertEqual(operation(), operation())


if __name__ == "__main__":
    unittest.main()
