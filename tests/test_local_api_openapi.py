import importlib.util
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_local_api_openapi", ROOT / "scripts/validate-local-api-openapi.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class LocalApiOpenApiTest(unittest.TestCase):
    def test_contract_matches_implemented_routes(self):
        self.assertEqual([], MODULE.validate())

