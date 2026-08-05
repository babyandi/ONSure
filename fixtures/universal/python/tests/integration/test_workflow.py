import json
import tempfile
import unittest
from pathlib import Path

from calculator import divide


class WorkflowTest(unittest.TestCase):
    def test_request_to_artifact_readback(self):
        request = {"left": 8, "right": 2}
        artifact = {"result": divide(request["left"], request["right"])}
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "result.json"
            output.write_text(json.dumps(artifact), encoding="utf-8")
            self.assertEqual(artifact, json.loads(output.read_text(encoding="utf-8")))
