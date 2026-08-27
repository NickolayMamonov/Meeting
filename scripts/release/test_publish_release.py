import tempfile
import unittest
from pathlib import Path

from publication_harness import run_harness
from publish_release import PublicationError
from test_package_artifacts import PackageArtifactsTest


class PublishReleaseTest(unittest.TestCase):
    def test_loopback_driver_posts_one_asset_and_publishes_last(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root))
            result = run_harness(
                evidence_directory=evidence,
                manifest_path=evidence / "release-manifest.json",
                release_body="Release Please body",
                tag="v1.0.0",
                source_sha="a" * 40,
            )
            transcript = result["transcript"]
            self.assertEqual(sum(item["method"] == "POST" for item in transcript), 1)
            self.assertEqual(sum(item["method"] == "PATCH" for item in transcript), 1)
            self.assertEqual(transcript[-1]["method"], "GET")
            self.assertTrue(result["published"])

    def test_final_read_race_is_detected_without_repair(self):
        with tempfile.TemporaryDirectory() as root:
            evidence = PackageArtifactsTest.package_release(Path(root))
            with self.assertRaises(PublicationError):
                run_harness(
                    evidence_directory=evidence,
                    manifest_path=evidence / "release-manifest.json",
                    release_body="Release Please body",
                    tag="v1.0.0",
                    source_sha="a" * 40,
                    inject_after_final_read=True,
                )
