#!/usr/bin/env python3
import json
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path

from package_artifacts import package
from verify_chain import verify


class PackageArtifactsTest(unittest.TestCase):
    def test_chain_is_acyclic_and_checksums_are_byte_sorted(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            metadata = root_path / "metadata.json"
            metadata.write_text(
                json.dumps(
                    {
                        "channel": "release",
                        "applicationId": "dev.whysoezzy.meet",
                        "versionName": "1.0.0",
                        "versionCode": 1000000,
                        "variant": "release",
                        "commitSha": "a" * 40,
                        "sourceBranch": "dev",
                        "workflow": "test",
                        "expectedCertificateSha256": "b" * 64,
                    }
                ),
                encoding="utf-8",
            )
            apk = root_path / "app.apk"
            aab = root_path / "app.aab"
            apk.write_bytes(b"apk bytes")
            aab.write_bytes(b"aab bytes")
            output = root_path / "out"
            package(
                Namespace(
                    metadata=str(metadata),
                    output=str(output),
                    apk=str(apk),
                    aab=str(aab),
                    mapping=None,
                    symbols=None,
                    tag="v1.0.0",
                    commit="a" * 40,
                    source_branch="dev",
                    workflow="test",
                )
            )
            verify(output)
            manifest = json.loads((output / "release-manifest.json").read_text())
            candidate = json.loads((output / "release-candidate.json").read_text())
            envelope = json.loads((output / "recovery-envelope.json").read_text())
            self.assertEqual(manifest["tag"], "v1.0.0")
            self.assertNotIn("release-candidate.json", (output / "SHA256SUMS").read_text())
            self.assertNotIn("recovery-envelope.json", (output / "SHA256SUMS").read_text())
            self.assertEqual(
                [line.split("  ", 1)[1] for line in (output / "SHA256SUMS").read_text().splitlines()],
                sorted(line.split("  ", 1)[1] for line in (output / "SHA256SUMS").read_text().splitlines()),
            )
            self.assertEqual(
                candidate["manifest"]["name"],
                "release-manifest.json",
            )
            self.assertIn("release-candidate.json", envelope["excluded_from_coverage"])
            self.assertTrue(
                any(
                    item["name"] == "release-candidate.json.attestation.json"
                    for item in envelope["attestations"]
                )
            )

    def test_rerun_replaces_owned_outputs_and_preserves_unrelated_file(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            metadata = root_path / "metadata.json"
            metadata.write_text(
                json.dumps(
                    {
                        "channel": "snapshot",
                        "applicationId": "dev.whysoezzy.meet.snapshot",
                        "versionName": "1.0.0-snapshot.7.1+" + "a" * 12,
                        "versionCode": 7,
                        "variant": "snapshot",
                        "commitSha": "a" * 40,
                        "expectedCertificateSha256": "b" * 64,
                    }
                ),
                encoding="utf-8",
            )
            apk = root_path / "app.apk"
            apk.write_bytes(b"first")
            output = root_path / "out"
            output.mkdir()
            unrelated = output / "operator-note.txt"
            unrelated.write_text("keep", encoding="utf-8")
            arguments = Namespace(
                metadata=str(metadata), output=str(output), apk=str(apk), aab=None,
                mapping=None, symbols=None, tag=None, commit=None,
                source_branch=None, workflow=None,
            )
            package(arguments)
            apk.write_bytes(b"second")
            package(arguments)
            self.assertEqual(unrelated.read_text(encoding="utf-8"), "keep")
            self.assertIn("operator-note.txt", {path.name for path in output.iterdir()})


if __name__ == "__main__":
    unittest.main()
