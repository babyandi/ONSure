import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]


class FixturePackagingTest(unittest.TestCase):
    def test_generated_dependency_and_runtime_files_are_excluded(self) -> None:
        pom = ET.parse(ROOT / "modules/onsure-test-fixtures/pom.xml")
        namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
        excludes = {
            node.text for node in pom.findall(".//m:resource/m:excludes/m:exclude", namespace)
        }
        self.assertTrue({
            "**/__pycache__/**",
            "**/*.pyc",
            "**/*.pyo",
            "**/node_modules/**",
            "**/.onsure/**",
        }.issubset(excludes))


if __name__ == "__main__":
    unittest.main()
