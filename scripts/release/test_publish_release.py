import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from publication_harness import EXPECTED_REJECTION_KEYS, run_harness
from publish_release import (
    GitHubReleaseClient,
    MAX_RELEASE_ASSET_BYTES,
    PublicationError,
    run,
)
from test_package_artifacts import PackageArtifactsTest


class PublishReleaseTest(unittest.TestCase):
    @staticmethod
    def harness_options(evidence):
        return {
            "evidence_directory": evidence,
            "manifest_path": evidence / "release-manifest.json",
            "release_body": "Release Please body",
            "tag": "v1.0.0",
            "source_sha": "a" * 40,
            "application_source_sha": "a" * 40,
            "attestation_repository": "owner/repo",
            "attestation_signer_workflow": "owner/repo/.github/workflows/release.yml",
            "attestation_source_ref": "refs/heads/dev",
            "attestation_source_sha": "b" * 40,
            "attestation_token": "fixture-token",
            "android_verifier": lambda _path: None,
            "attestation_verifier": lambda _path: None,
        }

    def test_loopback_driver_posts_one_asset_and_publishes_last(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root), grouped=True)
            result = run_harness(**self.harness_options(evidence))
            transcript = result["transcript"]
            self.assertEqual(sum(item["method"] == "POST" for item in transcript), 1)
            self.assertEqual(sum(item["method"] == "PATCH" for item in transcript), 1)
            self.assertEqual(transcript[-1]["method"], "GET")
            self.assertTrue(result["published"])
            self.assertEqual(result["request_counts"], {"GET": 8, "POST": 1, "PATCH": 1, "DELETE": 0})
            self.assertEqual(result["mutation_contract"], {
                "exactly_one_post": True,
                "exactly_one_patch": True,
                "no_repair": True,
                "no_retry": True,
            })
            self.assertEqual(result["external_hosts_contacted"], [])
            self.assertTrue(result["transport"]["loopback_http"])
            self.assertEqual(result["transport"]["mode"], "direct")
            self.assertTrue(all(result["rejection_matrix"].values()))
            self.assertEqual(set(result["rejection_matrix"]), EXPECTED_REJECTION_KEYS)
            self.assertTrue(result["rejection_matrix"]["transport_second_leg_non_200"])
            self.assertTrue(result["rejection_matrix"]["final_asset_metadata_drift"])
            self.assertTrue(result["rejection_matrix"]["manifest_identity_mismatch"])
            self.assertTrue(result["rejection_matrix"]["candidate_identity_mismatch"])
            self.assertTrue(result["rejection_matrix"]["loopback_state_identity_mismatch"])
            self.assertTrue(all(
                fixture["rejected"] for fixture in result["identity_fixtures"].values()
            ))
            authorities = {
                fixture["authority"] for fixture in result["identity_fixtures"].values()
            }
            self.assertEqual(len(authorities), len(result["identity_fixtures"]))
            self.assertTrue(result["android_checks"]["local"])
            self.assertTrue(result["android_checks"]["downloaded"])
            self.assertTrue(
                any(item["path"].endswith("/git/ref/tags/v1.0.0") for item in transcript)
            )
            self.assertEqual(
                set(
                    item["headers"]
                    for item in transcript
                    if item["url"].startswith("https://release-assets.githubusercontent.com/")
                ),
                set(),
            )

    def test_loopback_driver_uses_real_single_redirect_without_data_credentials(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root), grouped=True)
            options = self.harness_options(evidence)
            options["use_redirect"] = True
            result = run_harness(**options)
            data_legs = [
                item
                for item in result["transcript"]
                if item["url"].startswith("https://release-assets.githubusercontent.com/")
            ]
            self.assertEqual(len(data_legs), 1)
            self.assertEqual(data_legs[0]["status"], 200)
            self.assertNotIn("authorization", data_legs[0]["headers"])
            self.assertTrue(result["transport"]["credential_free_data_leg"])
            self.assertIn("content-length", data_legs[0]["response_headers"])
            self.assertIn("content-type", data_legs[0]["response_headers"])

    def test_final_read_race_is_indeterminate_without_repair(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root), grouped=True)
            options = self.harness_options(evidence)
            options["inject_after_final_read"] = True
            result = run_harness(**options)
            self.assertTrue(result["indeterminate"])
            self.assertEqual(result["classification"], "excluded-concurrency/indeterminate")
            self.assertEqual(result["race"]["request_counts"]["POST"], 1)
            self.assertEqual(result["race"]["request_counts"]["PATCH"], 1)
            self.assertFalse(result["race"]["retry_attempted"])
            self.assertFalse(result["race"]["repair_attempted"])
            self.assertEqual(result["request_counts"]["DELETE"], 0)

    def test_harness_rejects_missing_identity_inputs(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root), grouped=True)
            options = self.harness_options(evidence)
            options["application_source_sha"] = "c" * 40
            with self.assertRaisesRegex(PublicationError, "bound to application"):
                run_harness(**options)

    def test_noncanonical_manifest_is_rejected_before_client_state_read(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root), grouped=True)
            alternate = Path(root) / "alternate-manifest.json"
            alternate.write_bytes((evidence / "release-manifest.json").read_bytes())

            class NoStateReadClient:
                def get_release(self, _release_id):
                    raise AssertionError("client state must not be read")

            with self.assertRaisesRegex(PublicationError, "canonical release evidence manifest"):
                run(
                    client=NoStateReadClient(),
                    release_id=42,
                    tag="v1.0.0",
                    source_sha="a" * 40,
                    expected_source_branch="dev",
                    evidence_directory=evidence,
                    manifest_path=alternate,
                    attestation_repository="owner/repo",
                    attestation_signer_workflow="owner/repo/.github/workflows/release.yml",
                    attestation_source_ref="refs/heads/dev",
                    attestation_source_sha="b" * 40,
                    attestation_run_id=100,
                    attestation_run_attempt=2,
                )

    def test_mismatched_chain_identity_is_rejected_before_post_or_patch(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root))
            class NoMutationClient:
                post_count = 0
                patch_count = 0

                def get_release(self, _release_id):
                    raise AssertionError("release state must not be read")

                def create_asset(self, _release_id, _path):
                    self.post_count += 1
                    raise AssertionError("mismatched admission reached POST")

                def patch_release(self, _release_id, _payload):
                    self.patch_count += 1
                    raise AssertionError("mismatched admission reached PATCH")

            client = NoMutationClient()
            with self.assertRaisesRegex(PublicationError, "protected evidence admission failed"):
                run(
                    client=client,
                    release_id=42,
                    tag="v1.0.0",
                    source_sha="a" * 40,
                    expected_source_branch="dev",
                    evidence_directory=evidence,
                    manifest_path=evidence / "release-manifest.json",
                    attestation_repository="other/repo",
                    attestation_signer_workflow="owner/repo/.github/workflows/release.yml",
                    attestation_source_ref="refs/heads/dev",
                    attestation_source_sha="b" * 40,
                    attestation_run_id=100,
                    attestation_run_attempt=2,
                )
            self.assertEqual(client.post_count, 0)
            self.assertEqual(client.patch_count, 0)

    def test_harness_passes_branch_from_validated_fixture_provenance(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root))
            options = self.harness_options(evidence)
            calls = []

            def injected_run(**kwargs):
                calls.append(kwargs)
                return {"published": True}

            with (
                patch(
                    "publication_harness.admit_release_evidence",
                    return_value={"source_branch": "fixture-branch"},
                ),
                patch("publication_harness.run", side_effect=injected_run),
            ):
                run_harness(**options)

            self.assertTrue(calls)
            self.assertEqual(calls[0]["expected_source_branch"], "fixture-branch")

    def test_upload_size_cap_is_checked_before_opening_asset_body(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "Meet.apk"
            with path.open("wb") as output:
                output.truncate(MAX_RELEASE_ASSET_BYTES + 1)

            class NoRequestOpener:
                def open(self, _request):
                    raise AssertionError("transport must not be reached")

            client = GitHubReleaseClient(
                "owner/repo",
                token="fixture",
                opener=NoRequestOpener(),
                data_opener=NoRequestOpener(),
            )
            with self.assertRaisesRegex(PublicationError, "outside the configured bound"):
                client.create_asset(42, path)

    def test_redirect_data_leg_requires_exact_http_200(self):
        from publication_harness import _DownloadFixtureOpener

        with tempfile.TemporaryDirectory() as root:
            client = GitHubReleaseClient(
                "owner/repo",
                token="fixture",
                opener=_DownloadFixtureOpener(
                    status=302,
                    location="https://release-assets.githubusercontent.com/asset",
                ),
                data_opener=_DownloadFixtureOpener(status=201),
            )
            with self.assertRaisesRegex(PublicationError, "not HTTP 200"):
                client.download_asset(
                    1,
                    Path(root) / "Meet.apk",
                    expected_size=3,
                    expected_sha256="d2f8b6f3e3f7f2f7b7b9f7a1d0f4f4e9f1e8c5b7b1f3f2d9d8f7e6c5b4a39281",
                )
