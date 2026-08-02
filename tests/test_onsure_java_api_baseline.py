import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import onsure_java_api_baseline as api  # noqa: E402


class ONSureJavaApiBaselineTest(unittest.TestCase):
    def test_normalization_removes_source_banner_and_preserves_descriptors(self):
        output = (
            'Compiled from "Example.java"\n'
            "public final class io.onsure.Example {\n"
            "  public void run();\n"
            "    descriptor: ()V\n"
            "}\n"
        )
        self.assertEqual(
            [
                "public final class io.onsure.Example {",
                "  public void run();",
                "    descriptor: ()V",
                "}",
            ],
            api.normalized_javap(output),
        )

    def test_missing_classes_directory_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "CLASSES_NOT_BUILT"):
            api.class_names(ROOT / "does-not-exist")


if __name__ == "__main__":
    unittest.main()
