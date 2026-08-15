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

    def test_release_please_resolves_one_canonical_authority_and_safe_noop(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        release_job = workflow[
            workflow.index("  release-please:") : workflow.index("  stable-build:")
        ]
        self.assertIn("fetch-depth: 0", release_job)
        self.assertIn("release_created: ${{ steps.authority.outputs.release_created }}", release_job)
        self.assertIn("materialize: ${{ steps.authority.outputs.materialize }}", release_job)
        self.assertIn("tag_name: ${{ steps.authority.outputs.tag_name }}", release_job)
        self.assertIn("source_sha: ${{ steps.authority.outputs.source_sha }}", release_job)
        self.assertIn("release_id: ${{ steps.authority.outputs.release_id }}", release_job)
        self.assertIn("manifest_version=", release_job)
        self.assertIn("releases/tags/$tag_name", release_job)
        self.assertIn("(.draft != true) or (.published_at != null)", release_job)
        self.assertIn("emit_noop", release_job)
        self.assertIn("release-list-error.txt", release_job)
        self.assertIn(".target_commitish", release_job)
        self.assertIn(".assets | length == 0", release_job)
        self.assertIn("^[0-9a-fA-F]{40}$", release_job)
        self.assertIn("git merge-base --is-ancestor", release_job)
        self.assertIn("refs/tags/$tag_name", release_job)
        resolver = release_job[release_job.index("Resolve canonical release authority") :]
        for forbidden in ("--method POST", "--method PATCH", "--method DELETE", "uploads.github.com"):
            self.assertNotIn(forbidden, resolver)

    def test_existing_draft_resolver_uses_paginated_list_and_unique_exact_tag(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        release_job = workflow[
            workflow.index("  release-please:") : workflow.index("  stable-build:")
        ]
        resume_start = release_job.index('elif [ "$release_created" = "false" ]')
        resume_end = release_job.index(
            'else\n            echo "unexpected Release Please release_created output"',
            resume_start,
        )
        resume = release_job[resume_start:resume_end]
        self.assertIn("gh api --paginate --slurp", resume)
        self.assertIn("releases?per_page=100", resume)
        self.assertIn("release-pages.json", resume)
        self.assertIn("release list pagination is incomplete or malformed", resume)
        self.assertIn("[.[][] | select(.tag_name == $tag)]", resume)
        self.assertIn("match_count=\"$(jq -er 'length'", resume)
        self.assertIn('if [ "$match_count" -ne 1 ]; then', resume)
        self.assertNotIn("releases/tags/$tag_name", resume)
        self.assertIn("emit_noop", resume)
        self.assertIn("(.draft != true) or (.published_at != null)", resume)
        for forbidden in ("--method POST", "--method PATCH", "--method DELETE", "uploads.github.com"):
            self.assertNotIn(forbidden, resume)

    def test_stable_jobs_build_evidence_and_probe_only_canonical_sha(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        job_boundaries = {
            "stable-build": "  stable-sign:",
            "stable-evidence": "  stable-public-probe:",
            "stable-public-probe": "  stable-mutate:",
        }
        for job, boundary in job_boundaries.items():
            section = workflow[workflow.index(f"  {job}:") : workflow.index(boundary)]
            self.assertIn("ref: ${{ needs.release-please.outputs.source_sha }}", section)
            self.assertNotIn("ref: ${{ needs.release-please.outputs.tag_name }}", section)
        stable_build = workflow[
            workflow.index("  stable-build:") : workflow.index("  stable-sign:")
        ]
        self.assertIn('test "$(git rev-parse HEAD)" = "$RELEASE_SHA"', stable_build)
        self.assertIn(".target_commitish == $sha", stable_build)
        self.assertIn(".assets | length == 0", stable_build)
        self.assertIn("release_id: ${{ needs.release-please.outputs.release_id }}", stable_build)
        self.assertIn("git ls-remote --exit-code --refs origin", stable_build)

    def test_mutation_is_sha_bound_create_only_then_publish_and_verify(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        mutation = workflow[workflow.index("  stable-mutate:") :]
        self.assertIn(".target_commitish == $sha", mutation)
        self.assertIn("release tag already exists before upload", mutation)
        self.assertIn("--request POST", mutation)
        self.assertIn("gh api --method PATCH", mutation)
        self.assertLess(mutation.index("--request POST"), mutation.index("--method PATCH"))
        self.assertIn("Verify public release and lightweight tag", mutation)
        self.assertIn(".draft == false and (.published_at != null)", mutation)
        self.assertIn("git ls-remote --exit-code --refs", mutation)
        self.assertIn('test "$ref" = "$RELEASE_SHA refs/tags/$RELEASE_TAG"', mutation)

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
