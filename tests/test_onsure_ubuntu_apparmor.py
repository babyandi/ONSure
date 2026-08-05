from __future__ import annotations

import pathlib
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import validate_onsure_ubuntu_apparmor as apparmor  # noqa: E402


class ONSureUbuntuAppArmorTest(unittest.TestCase):
    def documents(self) -> tuple[str, dict[str, str]]:
        return (
            apparmor.PROFILE.read_text(encoding="utf-8"),
            {
                path: (ROOT / path).read_text(encoding="utf-8")
                for path in apparmor.DROPINS
            },
        )

    def test_current_candidate_is_named_and_fail_closed(self):
        profile, dropins = self.documents()
        self.assertEqual([], apparmor.validate_documents(profile, dropins))

    def test_complain_mode_is_not_packaged_as_final_policy(self):
        profile, dropins = self.documents()
        violations = apparmor.validate_documents(
            profile.replace(
                "profile onsure-api flags=(attach_disconnected,mediate_deleted)",
                "profile onsure-api flags=(complain)",
            ),
            dropins,
        )
        self.assertIn("APPARMOR_PACKAGE_COMPLAIN_MODE", violations)

    def test_dropin_profile_drift_is_rejected(self):
        profile, dropins = self.documents()
        changed = dict(dropins)
        path = "deploy/ubuntu/systemd/onsure.service.d/10-apparmor.conf"
        changed[path] = changed[path].replace("onsure-api", "wrong-profile")
        self.assertIn(
            "APPARMOR_DROPIN_BINDING:" + path,
            apparmor.validate_documents(profile, changed),
        )


if __name__ == "__main__":
    unittest.main()
