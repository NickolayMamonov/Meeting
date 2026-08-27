import tempfile
import unittest
from pathlib import Path

from publication_harness import run_harness
from publish_release import GitHubReleaseClient, MAX_RELEASE_ASSET_BYTES, PublicationError
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
            "attestation_signer_workflow": "owner/repo/.github/workflows/release-credential-audit.yml",
            "attestation_source_ref": "refs/heads/MEE3-59",
            "attestation_source_sha": "b" * 40,
            "attestation_token": "fixture-token",
            "android_verifier": lambda _path: None,
            "attestation_verifier": lambda _path: None,
        }

    def test_loopback_driver_posts_one_asset_and_publishes_last(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root))
            result = run_harness(**self.harness_options(evidence))
            transcript = result["transcript"]
            self.assertEqual(sum(item["method"] == "POST" for item in transcript), 1)
            self.assertEqual(sum(item["method"] == "PATCH" for item in transcript), 1)
            self.assertEqual(transcript[-1]["method"], "GET")
            self.assertTrue(result["published"])
            self.assertTrue(result["transport"]["loopback_http"])
            self.assertEqual(result["transport"]["mode"], "direct")
            self.assertTrue(all(result["rejection_matrix"].values()))
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
            evidence = PackageArtifactsTest.package_release(Path(root))
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

    def test_final_read_race_is_detected_without_repair(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root))
            options = self.harness_options(evidence)
            options["inject_after_final_read"] = True
            with self.assertRaises(PublicationError):
                run_harness(**options)

    def test_harness_rejects_missing_identity_inputs(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root))
            options = self.harness_options(evidence)
            options["application_source_sha"] = "c" * 40
            with self.assertRaisesRegex(PublicationError, "bound to application"):
                run_harness(**options)

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
