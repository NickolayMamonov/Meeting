#!/usr/bin/env python3
import json
import tempfile
import unittest
from pathlib import Path

from public_backend_probe import MAX_MEETINGS_BYTES, ProbeError, probe
from release_mutation_gate import (
    MutationError,
    expected_release_asset_names,
    verify_release_state,
    verify_uploaded_assets,
)


class PublicationGateTest(unittest.TestCase):
    @staticmethod
    def state(*, draft=True, assets=None):
        return {
            "id": 42,
            "tagName": "v1.0.0",
            "isDraft": draft,
            "publishedAt": None if draft else "2026-08-09T00:00:00Z",
            "author": {"login": "release-please"},
            "assets": assets if assets is not None else [],
        }

    def test_empty_draft_is_required_before_one_shot_upload(self):
        verify_release_state(
            self.state(),
            release_id=42,
            tag="v1.0.0",
            allowed_names={"release.apk"},
        )
        with self.assertRaises(MutationError):
            verify_release_state(
                self.state(
                    assets=[{
                        "name": "release.apk",
                    }]
                ),
                release_id=42,
                tag="v1.0.0",
                allowed_names={"release.apk"},
            )
        with self.assertRaises(MutationError):
            verify_release_state(
                self.state(draft=False),
                release_id=42,
                tag="v1.0.0",
                allowed_names={"release.apk"},
            )

    def test_uploaded_assets_are_exact_and_authoritative(self):
        assets = [{"name": "release.apk"}]
        verify_uploaded_assets(
            self.state(assets=assets),
            release_id=42,
            tag="v1.0.0",
            expected_names={"release.apk"},
        )
        with self.assertRaises(MutationError):
            verify_uploaded_assets(
                self.state(assets=assets),
                release_id=42,
                tag="v1.0.0",
                expected_names={"release.apk", "release-manifest.json"},
            )

    def test_public_probe_requires_meetings_json_200_and_actuator_404(self):
        def fetch(url):
            if url.endswith("/meetings"):
                return 200, b"[]"
            return 404, b""

        probe(fetch)

        with self.assertRaisesRegex(ProbeError, "meetings"):
            probe(lambda url: (204, b"") if url.endswith("/meetings") else (404, b""))
        with self.assertRaisesRegex(ProbeError, "JSON"):
            probe(lambda url: (200, b"not-json") if url.endswith("/meetings") else (404, b""))
        with self.assertRaisesRegex(ProbeError, "actuator"):
            probe(lambda url: (200, b"{}"))
        with self.assertRaises(ProbeError):
            probe(lambda url: (200, b"x" * (MAX_MEETINGS_BYTES + 1)) if url.endswith("/meetings") else (404, b""))
        with self.assertRaises(ProbeError):
            probe(lambda _url: (_ for _ in ()).throw(ProbeError("TLS failure")))

    def test_release_asset_allowlist_rejects_unreferenced_extra(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            names = {
                "release-authority.json",
                "release-manifest.json",
                "SHA256SUMS",
                "release-candidate.json",
                "attestation-index.json",
                "app.apk",
                "app.aab",
                "app.apk.attestation.json",
            }
            for name in names:
                (directory / name).write_text("{}", encoding="utf-8")
            (directory / "release-manifest.json").write_text(
                json.dumps({
                    "schema": 1,
                    "channel": "release",
                    "tag": "v1.0.0",
                    "commit": "a" * 40,
                    "artifacts": [
                        {"name": "app.apk", "type": "apk"},
                        {"name": "app.aab", "type": "aab"},
                    ],
                }),
                encoding="utf-8",
            )
            (directory / "release-candidate.json").write_text(
                json.dumps({"tag": "v1.0.0", "commit": "a" * 40}),
                encoding="utf-8",
            )
            (directory / "attestation-index.json").write_text(
                json.dumps({"attestations": [{"name": "app.apk.attestation.json"}]}),
                encoding="utf-8",
            )
            self.assertEqual(
                expected_release_asset_names(
                    directory, tag="v1.0.0", source_sha="a" * 40
                ),
                names,
            )
            (directory / "operator-note.txt").write_text("unexpected", encoding="utf-8")
            with self.assertRaisesRegex(MutationError, "unreferenced"):
                expected_release_asset_names(
                    directory, tag="v1.0.0", source_sha="a" * 40
                )

    def test_workflow_has_public_probe_before_protected_one_shot_mutation(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        self.assertIn("stable-public-probe", workflow)
        self.assertIn("android-release", workflow)
        self.assertLess(
            workflow.index("stable-public-probe"),
            workflow.index("stable-mutate"),
        )
        self.assertLess(
            workflow.index("POST"),
            workflow.index("PATCH"),
        )

    def test_release_credential_inventory_and_secret_boundaries_are_exact(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        stable_sign = workflow[
            workflow.index("  stable-sign:")
            : workflow.index("  stable-evidence:")
        ]
        outside_sign = workflow.replace(stable_sign, "", 1)
        for expression in (
            "secrets.RELEASE_KEYSTORE_BASE64",
            "secrets.RELEASE_KEYSTORE_PASSWORD",
            "secrets.RELEASE_KEY_PASSWORD",
        ):
            self.assertIn(expression, stable_sign)
            self.assertNotIn(expression, outside_sign)
        self.assertIn("secrets.RELEASE_PLEASE_TOKEN", workflow)
        self.assertIn("secrets.GOOGLE_SERVICES_JSON", workflow)
        self.assertIn("vars.BASE_URL_RELEASE", workflow)
        self.assertIn("bundletool_version='1.18.3'", workflow)
        self.assertIn(
            "bundletool_sha256='a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29'",
            workflow,
        )
        self.assertNotIn("vars.BUNDLETOOL", workflow)
        self.assertNotIn("BUNDLETOOL_VERSION:", workflow)
        self.assertNotIn("BUNDLETOOL_SHA256:", workflow)

    def test_release_static_audit_is_credential_free_and_focused(self):
        workflow = (
            Path(__file__).parents[2]
            / ".github"
            / "workflows"
            / "release-credential-audit.yml"
        ).read_text(encoding="utf-8")

        self.assertIn("  pull_request:\n    branches: [dev]", workflow)
        self.assertIn("  push:\n    branches: [dev]", workflow)
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertEqual(workflow.count("permissions:"), 1)
        self.assertIn("permissions:\n  contents: read", workflow)
        self.assertIn(
            "ref: ${{ github.event.pull_request.head.sha || github.sha }}",
            workflow,
        )
        self.assertIn("persist-credentials: false", workflow)
        self.assertIn(
            "python scripts/release/test_publication_gates.py",
            workflow,
        )
        self.assertIn(
            "python scripts/release/test_snapshot_apksigner_workflow.py",
            workflow,
        )
        for forbidden in (
            "pull_request_target",
            "merge_group",
            "audit_cli",
            "secrets.",
            "vars.",
            "GITHUB_TOKEN",
            "RELEASE_KEYSTORE_BASE64",
            "RELEASE_KEYSTORE_PASSWORD",
            "RELEASE_KEY_PASSWORD",
            "RELEASE_PLEASE_TOKEN",
            "GOOGLE_SERVICES_JSON",
        ):
            self.assertNotIn(forbidden, workflow)


if __name__ == "__main__":
    unittest.main()
