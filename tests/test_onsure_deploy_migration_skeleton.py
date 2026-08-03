from __future__ import annotations

import copy
import json
import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_deploy_migration_skeleton as skeleton  # noqa: E402


class ONSureDeployMigrationSkeletonTest(unittest.TestCase):
    def plans(self):
        return (
            json.loads(skeleton.DEPLOYMENT.read_text(encoding="utf-8")),
            json.loads(skeleton.MIGRATION.read_text(encoding="utf-8")),
        )

    def test_preflight_is_executable_but_deployment_and_migration_are_not(self):
        result = skeleton.preflight()
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertEqual("NOT_RUN_NOT_AUTHORIZED", result["deployment_execution"])
        self.assertEqual("NOT_RUN_NOT_AUTHORIZED", result["database_migration_execution"])
        self.assertFalse(result["production_go"])

    def test_public_bind_and_migration_tool_selection_fail_closed(self):
        deployment, migration = self.plans()
        changed_deployment = copy.deepcopy(deployment)
        changed_deployment["runtime"]["api_bind"] = "0.0.0.0"
        self.assertIn("DEPLOYMENT_RUNTIME_API_BIND", skeleton.validate_plans(changed_deployment, migration))
        changed_migration = copy.deepcopy(migration)
        changed_migration["migration_tool"] = "LIQUIBASE"
        self.assertIn("MIGRATION_POSTGRESQL_FLYWAY_SELECTION", skeleton.validate_plans(deployment, changed_migration))


if __name__ == "__main__":
    unittest.main()
