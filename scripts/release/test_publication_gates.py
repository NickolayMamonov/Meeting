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
            "name": "Meet v1.0.0",
            "tag_name": "v1.0.0",
            "target_commitish": "a" * 40,
            "draft": draft,
            "published_at": None if draft else "2026-08-09T00:00:00Z",
            "body": "Release Please body",
            "prerelease": False,
            "assets": assets if assets is not None else [],
        }

    def test_empty_draft_is_required_before_one_shot_upload(self):
        verify_release_state(
            self.state(),
            release_id=42,
            tag="v1.0.0",
            allowed_names={"Meet.apk"},
        )
        with self.assertRaises(MutationError):
            verify_release_state(
                self.state(
                    assets=[{
                        "id": 1,
                        "name": "Meet.apk",
                    }]
                ),
                release_id=42,
                tag="v1.0.0",
                allowed_names={"Meet.apk"},
            )
        with self.assertRaises(MutationError):
            verify_release_state(
                self.state(draft=False),
                release_id=42,
                tag="v1.0.0",
                allowed_names={"Meet.apk"},
            )
        malformed = self.state()
        del malformed["assets"]
        with self.assertRaisesRegex(MutationError, "assets field is missing"):
            verify_release_state(
                malformed,
                release_id=42,
                tag="v1.0.0",
                allowed_names={"Meet.apk"},
            )
        with self.assertRaisesRegex(MutationError, "positive"):
            verify_release_state(
                self.state(),
                release_id=0,
                tag="v1.0.0",
                allowed_names={"Meet.apk"},
            )

    def test_uploaded_assets_are_exact_and_authoritative(self):
        assets = [{"id": 1, "name": "Meet.apk", "size": 1}]
        verify_uploaded_assets(
            self.state(assets=assets),
            release_id=42,
            tag="v1.0.0",
            expected_names={"Meet.apk"},
        )
        with self.assertRaises(MutationError):
            verify_uploaded_assets(
                self.state(assets=assets),
                release_id=42,
                tag="v1.0.0",
                expected_names={"Meet.apk", "release-manifest.json"},
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
            from test_package_artifacts import PackageArtifactsTest

            source = PackageArtifactsTest.package_release(directory)
            self.assertEqual(
                expected_release_asset_names(
                    source, tag="v1.0.0", source_sha="a" * 40,
                    expected_source_branch="dev",
                ),
                {"Meet.apk"},
            )
            (source / "operator-note.txt").write_text("unexpected", encoding="utf-8")
            with self.assertRaisesRegex(MutationError, "unreferenced"):
                expected_release_asset_names(
                    source, tag="v1.0.0", source_sha="a" * 40,
                    expected_source_branch="dev",
                )

    def test_release_identity_boundary_rejects_repeated_malformed_branches(self):
        from test_package_artifacts import PackageArtifactsTest

        with tempfile.TemporaryDirectory() as temporary:
            source = PackageArtifactsTest.package_release(Path(temporary))
            for expected_branch in ("refs/heads/dev", "dev/.hidden", "dev/build.lock", 1):
                with self.subTest(expected_branch=expected_branch):
                    with self.assertRaises(MutationError):
                        expected_release_asset_names(
                            source,
                            tag="v1.0.0",
                            source_sha="a" * 40,
                            expected_source_branch=expected_branch,
                        )

    def test_release_identity_boundary_rejects_malformed_manifest_branch(self):
        from test_package_artifacts import PackageArtifactsTest

        with tempfile.TemporaryDirectory() as temporary:
            source = PackageArtifactsTest.package_release(Path(temporary))
            manifest_path = source / "release-manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["source_branch"] = 1
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaises(MutationError):
                expected_release_asset_names(
                    source,
                    tag="v1.0.0",
                    source_sha="a" * 40,
                    expected_source_branch="dev",
                )

    def test_candidate_and_attestation_index_identities_are_exact(self):
        from test_package_artifacts import PackageArtifactsTest

        mutations = (
            ("release-candidate.json", "schema", 2),
            ("release-candidate.json", "kind", "other-candidate"),
            ("release-candidate.json", "channel", "snapshot"),
            ("release-candidate.json", "tag", "v9.9.9"),
            ("release-candidate.json", "commit", "c" * 40),
            ("release-candidate.json", "source_branch", "other"),
            ("attestation-index.json", "schema", 2),
            ("attestation-index.json", "kind", "other-index"),
            ("attestation-index.json", "channel", "snapshot"),
        )
        with tempfile.TemporaryDirectory() as temporary:
            source = PackageArtifactsTest.package_release(Path(temporary))
            for filename, field, value in mutations:
                with self.subTest(filename=filename, field=field):
                    fixture = Path(temporary) / f"{filename}-{field}"
                    import shutil

                    shutil.copytree(source, fixture)
                    path = fixture / filename
                    document = json.loads(path.read_text(encoding="utf-8"))
                    document[field] = value
                    path.write_text(json.dumps(document), encoding="utf-8")
                    with self.assertRaises(MutationError):
                        expected_release_asset_names(
                            fixture,
                            tag="v1.0.0",
                            source_sha="a" * 40,
                            expected_source_branch="dev",
                        )

    def test_attestation_index_references_are_bound_to_admitted_objects(self):
        from test_package_artifacts import PackageArtifactsTest

        with tempfile.TemporaryDirectory() as temporary:
            source = PackageArtifactsTest.package_release(Path(temporary))
            fixture = Path(temporary) / "tampered-index"
            import shutil

            shutil.copytree(source, fixture)
            path = fixture / "attestation-index.json"
            index = json.loads(path.read_text(encoding="utf-8"))
            index["candidate"]["name"] = "release-manifest.json"
            path.write_text(json.dumps(index), encoding="utf-8")
            with self.assertRaisesRegex(MutationError, "candidate reference"):
                expected_release_asset_names(
                    fixture,
                    tag="v1.0.0",
                    source_sha="a" * 40,
                    expected_source_branch="dev",
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
        self.assertIn("python release-tooling/scripts/release/publish_release.py", workflow)

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
        self.assertNotIn("releases/tags/", release_job)
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
        resume = release_job[resume_start:]
        self.assertIn("gh api --paginate --slurp", resume)
        self.assertIn("releases?per_page=100", resume)
        self.assertIn("release-pages.json", release_job)
        self.assertIn("release list pagination is incomplete or malformed", resume)
        self.assertIn("[.[][] | select(.tag_name == $tag)]", resume)
        self.assertIn("match_count=\"$(jq -er 'length'", resume)
        self.assertIn('if [ "$match_count" -ne 1 ]; then', resume)
        self.assertIn('if [ "$match_count" -eq 0 ]; then', resume)
        self.assertNotIn("releases/tags/", resume)
        self.assertIn("emit_noop", resume)
        self.assertIn("(.draft != true) or (.published_at != null)", resume)
        for forbidden in ("--method POST", "--method PATCH", "--method DELETE", "uploads.github.com"):
            self.assertNotIn(forbidden, resume)

    def test_fresh_created_draft_uses_same_list_authority_and_fails_closed(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        release_job = workflow[
            workflow.index("  release-please:") : workflow.index("  stable-build:")
        ]
        resolver = release_job[release_job.index("Resolve canonical release authority") :]
        self.assertEqual(resolver.count("gh api --paginate --slurp"), 1)
        self.assertIn('if [ "$release_created" = "true" ]; then', resolver)
        self.assertIn('source_sha="$ACTION_SOURCE_SHA"', resolver)
        self.assertIn("new Release Please draft is missing from the releases list", resolver)
        self.assertIn(".id == $id and .tag_name == $tag and .target_commitish == $sha", resolver)
        self.assertIn('if [ "$release_created" = "false" ]; then', resolver)
        self.assertIn('source_sha="$(jq -er \'.target_commitish\' "$state")"', resolver)
        self.assertIn('has("id")', resolver)
        self.assertIn('has("tag_name")', resolver)
        self.assertIn('has("target_commitish")', resolver)
        self.assertIn('has("draft")', resolver)
        self.assertIn('has("published_at")', resolver)
        self.assertIn('has("assets")', resolver)
        self.assertNotIn("releases/tags/", resolver)
        for forbidden in ("--method POST", "--method PATCH", "--method DELETE", "uploads.github.com"):
            self.assertNotIn(forbidden, resolver)

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

    def test_stable_firebase_validation_precedes_gradle_and_cleanup(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        stable_build = workflow[
            workflow.index("  stable-build:") : workflow.index("  stable-sign:")
        ]
        provision = stable_build.index("Provision stable Firebase configuration")
        validation = stable_build.index("Validate stable Firebase configuration")
        gradle = stable_build.index("./gradlew")
        cleanup = stable_build.index("Remove Firebase configuration")
        self.assertLess(provision, validation)
        self.assertLess(validation, gradle)
        self.assertLess(gradle, cleanup)
        validation_step = stable_build[validation:gradle]
        self.assertIn("jq -e", validation_step)
        self.assertIn('type == "object"', validation_step)
        self.assertIn('(.project_info | type == "object")', validation_step)
        self.assertIn('(.client | type == "array" and length > 0)', validation_step)
        self.assertIn('package_name == "dev.whysoezzy.meet"', validation_step)
        self.assertIn("app/google-services.json >/dev/null", validation_step)
        self.assertNotIn("GOOGLE_SERVICES_JSON", validation_step)
        self.assertNotIn("echo", validation_step)
        self.assertIn("if: ${{ always() }}", stable_build)

    def test_snapshot_firebase_validation_precedes_gradle_and_cleanup(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "ci.yml"
        ).read_text(encoding="utf-8")
        snapshot_build = workflow[
            workflow.index("  snapshot-build:") : workflow.index("  snapshot-sign:")
        ]
        provision = snapshot_build.index("Provision snapshot Firebase configuration")
        validation = snapshot_build.index("Validate snapshot Firebase configuration")
        gradle = snapshot_build.index("./gradlew")
        cleanup = snapshot_build.index("Remove snapshot Firebase configuration")
        self.assertLess(provision, validation)
        self.assertLess(validation, gradle)
        self.assertLess(gradle, cleanup)
        provision_step = snapshot_build[provision:validation]
        self.assertIn("set -euo pipefail", provision_step)
        self.assertIn("umask 077", provision_step)
        self.assertIn('test -n "$FIREBASE_JSON_B64"', provision_step)
        self.assertIn("test -s app/google-services.json", provision_step)
        validation_step = snapshot_build[validation:gradle]
        self.assertIn("jq -e", validation_step)
        self.assertIn('type == "object"', validation_step)
        self.assertIn('(.project_info | type == "object")', validation_step)
        self.assertIn('(.client | type == "array" and length > 0)', validation_step)
        self.assertIn(
            'package_name == "dev.whysoezzy.meet.snapshot"', validation_step
        )
        self.assertIn("app/google-services.json >/dev/null", validation_step)
        self.assertNotIn("FIREBASE_JSON_B64", validation_step)
        self.assertNotIn("echo", validation_step)
        self.assertIn("if: ${{ always() }}", snapshot_build[cleanup:])
        self.assertIn("rm -f app/google-services.json", snapshot_build[cleanup:])

    def test_mutation_is_sha_bound_create_only_then_publish_and_verify(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        mutation = workflow[workflow.index("  stable-mutate:") :]
        self.assertIn("python release-tooling/scripts/release/publish_release.py", mutation)
        self.assertIn("ATTESTATION_TOKEN: ${{ github.token }}", mutation)
        self.assertIn("--evidence-directory release-output", mutation)
        self.assertIn("--rendered-body", mutation)

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
        self.assertIn("  push:\n    branches: [dev, master]", workflow)
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertEqual(workflow.count("permissions:"), 4)
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

    def test_protected_fixture_keeps_signing_passwords_out_of_environment(self):
        workflow = (
            Path(__file__).parents[2]
            / ".github"
            / "workflows"
            / "release-credential-audit.yml"
        ).read_text(encoding="utf-8")
        self.assertIn("-storetype JKS", workflow)
        self.assertIn("QA_STORE_PASSWORD_FILE", workflow)
        self.assertIn("QA_KEY_PASSWORD_FILE", workflow)
        self.assertNotIn("printf 'QA_STORE_PASSWORD=%s", workflow)
        self.assertNotIn("printf 'QA_KEY_PASSWORD=%s", workflow)
        self.assertGreaterEqual(workflow.count("publication_harness.py"), 2)
        self.assertIn("--redirect", workflow)
        self.assertIn(".rejection_matrix", workflow)
        self.assertIn("transport_second_leg_non_200", workflow)
        self.assertIn("final_asset_metadata_drift", workflow)
        self.assertIn("| keys | sort", workflow)
        for assertion in (
            ".published == true",
            ".request_counts.POST == 1",
            ".request_counts.PATCH == 1",
            ".request_counts.DELETE == 0",
            ".mutation_contract.exactly_one_post",
            ".mutation_contract.exactly_one_patch",
            ".mutation_contract.no_repair",
            ".mutation_contract.no_retry",
            ".external_hosts_contacted == []",
            ".identity_fixtures",
            ".value.rejected == true",
        ):
            self.assertIn(assertion, workflow)

    def test_release_proof_is_dispatch_only_non_publishing_and_exactly_topologized(self):
        workflow = (
            Path(__file__).parents[2]
            / ".github"
            / "workflows"
            / "release-proof.yml"
        ).read_text(encoding="utf-8")
        self.assertIn("name: Android release proof", workflow)
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertIn("application_sha:", workflow)
        for forbidden in (
            "\n  push:",
            "\n  pull_request:",
            "\n  schedule:",
            "RELEASE_PLEASE_TOKEN",
            "release-please",
            "publish_release.py",
            "gh api",
            "release_id",
            "gh release",
            "git push",
        ):
            self.assertNotIn(forbidden, workflow)
        self.assertIn("permissions:\n  contents: read", workflow)
        self.assertNotIn("contents: write", workflow)
        self.assertEqual(workflow.count("id-token: write"), 1)
        self.assertEqual(workflow.count("attestations: write"), 1)
        jobs = (
            "proof-build",
            "proof-sign",
            "proof-evidence",
            "proof-public-probe",
            "proof-report",
        )
        self.assertEqual(
            [workflow.index(f"  {job}:") for job in jobs],
            sorted(workflow.index(f"  {job}:") for job in jobs),
        )
        for job in jobs:
            self.assertEqual(workflow.count(f"  {job}:"), 1)

    def test_release_proof_binds_source_signer_evidence_and_probe(self):
        workflow = (
            Path(__file__).parents[2]
            / ".github"
            / "workflows"
            / "release-proof.yml"
        ).read_text(encoding="utf-8")
        sections = {
            "proof-build": workflow[
                workflow.index("  proof-build:") : workflow.index("  proof-sign:")
            ],
            "proof-sign": workflow[
                workflow.index("  proof-sign:") : workflow.index("  proof-evidence:")
            ],
            "proof-evidence": workflow[
                workflow.index("  proof-evidence:") : workflow.index("  proof-public-probe:")
            ],
            "proof-public-probe": workflow[
                workflow.index("  proof-public-probe:") : workflow.index("  proof-report:")
            ],
            "proof-report": workflow[workflow.index("  proof-report:") :],
        }
        build = sections["proof-build"]
        self.assertIn("environment:\n      name: android-release", build)
        self.assertIn("ref: ${{ inputs.application_sha }}", build)
        self.assertIn('test "$(git -C application-source rev-parse HEAD)" = "$APPLICATION_SHA"', build)
        self.assertIn('git -C application-source cat-file -e "$APPLICATION_SHA^{commit}"', build)
        self.assertIn("git -C release-tooling merge-base --is-ancestor", build)
        self.assertIn("ORG_GRADLE_PROJECT_RELEASE_COMMIT_SHA: ${{ inputs.application_sha }}", build)
        self.assertIn("-PreleaseCommitSha=\"${{ inputs.application_sha }}\"", build)
        self.assertIn(".applicationId == \"dev.whysoezzy.meet\"", build)
        self.assertIn(".releaseBaseUrl == \"https://api.whysoezzy.online\"", build)
        self.assertIn(".releaseHost == \"api.whysoezzy.online\"", build)
        self.assertIn(".commitSha == $application_sha", build)
        signer = sections["proof-sign"]
        for expression in (
            "secrets.RELEASE_KEYSTORE_BASE64",
            "secrets.RELEASE_KEYSTORE_PASSWORD",
            "secrets.RELEASE_KEY_PASSWORD",
        ):
            self.assertIn(expression, signer)
            self.assertNotIn(expression, workflow.replace(signer, "", 1))
        self.assertIn('environment:\n      name: android-release', signer)
        self.assertIn('"$apksigner" sign', signer)
        self.assertIn('"$apksigner" verify', signer)
        self.assertIn("jarsigner", signer)
        self.assertIn("signer-fingerprints.json", signer)
        self.assertIn(
            "b643fc0e49f572d3b7202c1e28e0ded1eb50228c70ae7531a573c97c5763536f",
            signer,
        )
        evidence = sections["proof-evidence"]
        self.assertIn("id-token: write", evidence)
        self.assertIn("attestations: write", evidence)
        for script in (
            "verify_android_artifacts.py",
            "package_artifacts.py",
            "collect_attestation_evidence.py",
            "verify_chain.py",
        ):
            self.assertIn(f"release-tooling/scripts/release/{script}", evidence)
        self.assertIn("actions/attest-build-provenance", evidence)
        self.assertIn('--commit "${{ needs.proof-build.outputs.application_sha }}"', evidence)
        self.assertIn('--source-sha "${{ github.sha }}"', evidence)
        self.assertIn("sha256sum --check SHA256SUMS", evidence)
        self.assertIn("release-chain verification passed", evidence)
        self.assertIn("--expected-debuggable false", evidence)
        self.assertIn("--aab", evidence)
        self.assertIn("--bundletool-jar", evidence)
        self.assertIn("--bundletool-sha256", evidence)
        probe = sections["proof-public-probe"]
        self.assertIn("needs: proof-evidence", probe)
        self.assertIn("if: ${{ needs.proof-evidence.result == 'success' }}", probe)
        self.assertIn("environment:\n      name: android-release", probe)
        self.assertIn("public_backend_probe.py", probe)
        self.assertIn("public backend probe passed", probe)
        self.assertNotIn("continue-on-error", probe)

    def test_release_proof_retains_all_revision_and_run_identities(self):
        workflow = (
            Path(__file__).parents[2]
            / ".github"
            / "workflows"
            / "release-proof.yml"
        ).read_text(encoding="utf-8")
        report = workflow[workflow.index("  proof-report:") :]
        self.assertIn(
            "needs: [proof-build, proof-sign, proof-evidence, proof-public-probe]",
            report,
        )
        for job in (
            "proof-build",
            "proof-sign",
            "proof-evidence",
            "proof-public-probe",
        ):
            self.assertIn(f"needs.{job}.result == 'success'", report)
        for identity in (
            "workflow_name",
            "workflow_sha",
            "tooling_sha",
            "workflow_tooling_sha",
            "application_sha",
            "GITHUB_RUN_ID",
            "GITHUB_RUN_ATTEMPT",
            "proof-run.json",
            "proof_jobs",
            "report_job",
        ):
            self.assertIn(identity, report)
        self.assertIn("retention-days: 7", report)
        self.assertIn("android-release-proof-${{ inputs.application_sha }}-${{ github.run_attempt }}", report)
        self.assertIn("report/release-output", report)
        self.assertIn("report/signer-fingerprints.json", report)
        self.assertIn("report/proof-run.json", report)


if __name__ == "__main__":
    unittest.main()
