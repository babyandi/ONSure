import importlib.util
import pathlib
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "analyze_onsure_worktree", ROOT / "scripts" / "analyze_onsure_worktree.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class AnalyzeOnsureWorktreeTest(unittest.TestCase):
    def test_classifies_without_returning_secret_values(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = pathlib.Path(directory)
            self.git(repo, "init", "-q")
            self.git(repo, "config", "user.email", "test@example.invalid")
            self.git(repo, "config", "user.name", "Test")
            (repo / "App.java").write_text("class App {}\n", encoding="utf-8")
            self.git(repo, "add", "App.java")
            self.git(repo, "commit", "-qm", "baseline")

            (repo / "App.java").write_text("class App { int value; }\n", encoding="utf-8")
            (repo / "target").mkdir()
            (repo / "target" / "App.class").write_bytes(b"generated")
            (repo / "notes.bak").write_text("backup", encoding="utf-8")
            (repo / "conflict.txt").write_text(
                "<<<<<<< ours\na\n=======\nb\n>>>>>>> theirs\n", encoding="utf-8"
            )
            secret = "sk-" + "x" * 32
            (repo / "settings.txt").write_text(secret, encoding="utf-8")

            result = MODULE.analyze(repo)
            categories = {entry["path"]: entry["category"] for entry in result["entries"]}
            self.assertEqual("source", categories["App.java"])
            self.assertEqual("generated", categories["target/App.class"])
            self.assertEqual("backup", categories["notes.bak"])
            self.assertEqual("conflict", categories["conflict.txt"])
            self.assertEqual("sensitive_candidate", categories["settings.txt"])
            self.assertFalse(result["secret_value_disclosure"])
            self.assertNotIn(secret, repr(result))

    @staticmethod
    def git(repo: pathlib.Path, *args: str):
        subprocess.run(["git", "-C", str(repo), *args], check=True)


if __name__ == "__main__":
    unittest.main()
