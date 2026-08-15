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
        cls.unit_tests = cls.workflow[
            cls.workflow.index("  unit-tests:")
            : cls.workflow.index("  assemble:")
        ]
        cls.stable_sign = cls.release_workflow[
            cls.release_workflow.index("  stable-sign:")
            : cls.release_workflow.index("  stable-evidence:")
        ]
        cls.stable_evidence = cls.release_workflow[
            cls.release_workflow.index("  stable-evidence:")
            : cls.release_workflow.index("  stable-public-probe:")
        ]
        cls.stable_public_probe = cls.release_workflow[
            cls.release_workflow.index("  stable-public-probe:")
            : cls.release_workflow.index("  stable-mutate:")
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
        self.assertEqual(verifier_call.count("--expected-debuggable true"), 1)
        self.assertNotIn("--expected-debuggable false", verifier_call)
        self.assertNotIn('--apkanalyzer apkanalyzer', verifier_call)

    def test_stable_sign_resolves_apksigner_before_secrets_and_uses_quoted_path(self):
        self.assertIn(
            'ref: ${{ github.sha }}',
            self.stable_sign,
        )
        self.assertIn("path: release-tooling", self.stable_sign)
        self.assertNotIn('ref: ${{ needs.release-please.outputs.source_sha }}', self.stable_sign)
        self.assertNotIn('ref: ${{ needs.release-please.outputs.tag_name }}', self.stable_sign)
        self.assertIn("fetch-depth: 1", self.stable_sign)
        self.assertIn("persist-credentials: false", self.stable_sign)
        self.assertIn("actions/setup-java", self.stable_sign)
        self.assertIn("java-version: \"21\"", self.stable_sign)
        self.assertIn("actions/setup-python", self.stable_sign)
        self.assertIn("python-version: \"3.12\"", self.stable_sign)
        self.assertIn("python release-tooling/scripts/release/android_sdk_tools.py apksigner", self.stable_sign)
        self.assertIn("APKSIGNER_PATH", self.stable_sign)
        self.assertLess(
            self.stable_sign.index("Resolve stable apksigner"),
            self.stable_sign.index("Decode release keystore"),
        )
        self.assertIn('"$apksigner" sign', self.stable_sign)
        self.assertIn('"$apksigner" verify', self.stable_sign)
        self.assertNotIn("\napksigner sign", self.stable_sign)
        self.assertNotIn("\napksigner verify", self.stable_sign)

    def test_release_tooling_tests_run_in_unprivileged_unit_job(self):
        self.assertIn(
            'python -m unittest discover -s scripts/release -p "test_*.py"',
            self.unit_tests,
        )
        self.assertNotIn("environment:", self.unit_tests)
        self.assertNotIn("secrets.", self.unit_tests)
        self.assertNotIn("continue-on-error", self.unit_tests)

    def test_stable_call_site_requires_false_and_keeps_aab_contract(self):
        verifier_call = self.stable_evidence[
            self.stable_evidence.index("python release-tooling/scripts/release/verify_android_artifacts.py") :
        ]
        self.assertEqual(verifier_call.count("--expected-debuggable false"), 1)
        self.assertNotIn("--expected-debuggable true", verifier_call)
        self.assertIn("--aab", verifier_call)
        self.assertIn("--bundletool-jar", verifier_call)
        self.assertIn("--bundletool-sha256", verifier_call)
        self.assertIn('name: Resolve stable Android SDK tools', self.stable_evidence)
        self.assertIn("python release-tooling/scripts/release/android_sdk_tools.py apksigner", self.stable_evidence)
        self.assertIn("python release-tooling/scripts/release/android_sdk_tools.py apkanalyzer", self.stable_evidence)
        self.assertIn('--apksigner "$APKSIGNER_PATH"', verifier_call)
        self.assertIn('--apkanalyzer "$APKANALYZER_PATH"', verifier_call)
        self.assertNotIn("--apksigner apksigner", verifier_call)
        self.assertNotIn("--apkanalyzer apkanalyzer", verifier_call)

    def test_verifiers_fail_fast_before_evidence_production(self):
        for workflow, verifier_script, downstream in (
            (
                self.snapshot_evidence,
                "python scripts/release/verify_android_artifacts.py",
                (
                    "Prepare snapshot evidence",
                    "Attest snapshot subjects",
                    "Finalize snapshot evidence",
                    "actions/upload-artifact",
                ),
            ),
            (
                self.stable_evidence,
                "python release-tooling/scripts/release/verify_android_artifacts.py",
                (
                    "Prepare deterministic release evidence",
                    "actions/attest-build-provenance",
                    "Finalize and verify release evidence",
                    "actions/upload-artifact",
                ),
            ),
        ):
            verifier_call = workflow[workflow.index(verifier_script) :]
            self.assertIn("set -euo pipefail", verifier_call)
            verifier_position = workflow.index(verifier_script)
            for step in downstream:
                self.assertGreater(workflow.index(step), verifier_position)
            self.assertNotIn("continue-on-error", workflow)

    def test_post_build_jobs_use_reviewed_workflow_tooling_commit(self):
        workflow = self.release_workflow
        stable_build = workflow[
            workflow.index("  stable-build:") : workflow.index("  stable-sign:")
        ]
        self.assertIn('ref: ${{ needs.release-please.outputs.source_sha }}', stable_build)
        self.assertNotIn('ref: ${{ needs.release-please.outputs.tag_name }}', stable_build)

        for section in (self.stable_sign, self.stable_evidence, self.stable_public_probe):
            self.assertNotIn('ref: ${{ needs.release-please.outputs.tag_name }}', section)
            self.assertNotIn("python scripts/release/", section)

        self.assertIn('ref: ${{ github.sha }}', self.stable_sign)
        self.assertIn("path: release-tooling", self.stable_sign)
        self.assertNotIn('ref: ${{ needs.release-please.outputs.source_sha }}', self.stable_sign)
        self.assertIn("python release-tooling/scripts/release/android_sdk_tools.py", self.stable_sign)

        self.assertIn('ref: ${{ needs.release-please.outputs.source_sha }}', self.stable_evidence)
        self.assertIn('ref: ${{ github.sha }}', self.stable_evidence)
        self.assertIn("path: release-tooling", self.stable_evidence)
        for script in (
            "android_sdk_tools.py",
            "verify_android_artifacts.py",
            "package_artifacts.py",
            "collect_attestation_evidence.py",
            "verify_chain.py",
        ):
            self.assertIn(
                f"python release-tooling/scripts/release/{script}",
                self.stable_evidence,
            )

        self.assertIn('ref: ${{ needs.release-please.outputs.source_sha }}', self.stable_public_probe)
        self.assertIn('ref: ${{ github.sha }}', self.stable_public_probe)
        self.assertIn("path: release-tooling", self.stable_public_probe)
        self.assertIn(
            "run: python release-tooling/scripts/release/public_backend_probe.py",
            self.stable_public_probe,
        )

    def test_stable_downstream_jobs_require_successful_predecessors(self):
        self.assertIn(
            "if: ${{ needs.snapshot-sign.result == 'success' }}",
            self.snapshot_evidence,
        )
        public_probe = self.release_workflow[
            self.release_workflow.index("  stable-public-probe:")
            : self.release_workflow.index("  stable-mutate:")
        ]
        mutation = self.release_workflow[self.release_workflow.index("  stable-mutate:") :]
        self.assertIn("needs.stable-evidence.result == 'success'", public_probe)
        self.assertIn("needs.stable-public-probe.result == 'success'", mutation)
        self.assertNotIn("continue-on-error", public_probe + mutation)
        self.assertNotIn("RELEASE_KEYSTORE", public_probe + mutation)
        self.assertNotIn("RELEASE_KEY_PASSWORD", public_probe + mutation)


if __name__ == "__main__":
    unittest.main()
