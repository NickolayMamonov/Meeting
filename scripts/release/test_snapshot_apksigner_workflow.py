import re
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).parents[2] / ".github" / "workflows" / "ci.yml"
RELEASE_WORKFLOW = Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"


class SnapshotApksignerWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.release_workflow = RELEASE_WORKFLOW.read_text(encoding="utf-8")
        cls.snapshot_sign = cls.workflow[
            cls.workflow.index("  snapshot-sign:")
            : cls.workflow.index("  snapshot-evidence:")
        ]
        cls.snapshot_evidence = cls.workflow[
            cls.workflow.index("  snapshot-evidence:")
            : cls.workflow.index("  release-") if "  release-" in cls.workflow else len(cls.workflow)
        ]

    def test_snapshot_sign_is_checkout_free_and_resolves_before_secret_decode(self):
        self.assertNotIn("actions/checkout", self.snapshot_sign)
        self.assertIn("name: Resolve snapshot apksigner", self.snapshot_sign)
        self.assertLess(
            self.snapshot_sign.index("name: Resolve snapshot apksigner"),
            self.snapshot_sign.index("name: Decode snapshot keystore"),
        )
        self.assertIn("ANDROID_SDK_ROOT", self.snapshot_sign)
        self.assertIn("ANDROID_HOME", self.snapshot_sign)
        self.assertIn("source.properties", self.snapshot_sign)
        self.assertIn("apksigner version", self.snapshot_sign)
        self.assertIn("highest Android SDK build-tools version is ambiguous", self.snapshot_sign)

    def test_snapshot_sign_reuses_one_quoted_resolved_path(self):
        self.assertIn('apksigner="$APKSIGNER_PATH"', self.snapshot_sign)
        self.assertIn('"$apksigner" sign', self.snapshot_sign)
        self.assertIn('"$apksigner" verify', self.snapshot_sign)
        self.assertNotIn("\napksigner sign", self.snapshot_sign)
        self.assertNotIn("\napksigner verify", self.snapshot_sign)
        self.assertEqual(self.snapshot_sign.count('"$apksigner"'), 2)

    def test_snapshot_evidence_uses_checked_out_python_resolver_and_injects_output(self):
        self.assertIn("name: Resolve snapshot Android SDK tools", self.snapshot_evidence)
        self.assertIn("python scripts/release/android_sdk_tools.py apksigner", self.snapshot_evidence)
        self.assertIn("python scripts/release/android_sdk_tools.py apkanalyzer", self.snapshot_evidence)
        self.assertIn('printf \'APKSIGNER_PATH=%s\\n\' "$apksigner"', self.snapshot_evidence)
        self.assertIn('printf \'APKANALYZER_PATH=%s\\n\' "$apkanalyzer"', self.snapshot_evidence)
        verifier_call = self.snapshot_evidence[
            self.snapshot_evidence.index("python scripts/release/verify_android_artifacts.py") :
        ]
        self.assertIn('--apksigner "$APKSIGNER_PATH"', verifier_call)
        self.assertIn('--apkanalyzer "$APKANALYZER_PATH"', verifier_call)
        self.assertNotIn('--apkanalyzer apkanalyzer', verifier_call)

    def test_stable_call_site_keeps_bare_path_compatibility(self):
        calls = re.findall(
            r"python scripts/release/verify_android_artifacts\.py\s*\\\n"
            r"(?:.*\\\n)*?.*--apksigner\s+([^\s\\]+)",
            self.release_workflow,
        )
        self.assertEqual(calls, ["apksigner"])
        self.assertIn("--apkanalyzer apkanalyzer", self.release_workflow)


if __name__ == "__main__":
    unittest.main()
