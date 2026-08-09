#!/usr/bin/env python3
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from recovery_gate import verify as verify_recovery, verify_producer
from release_mutation_gate import MutationError, verify_release_state, verify_uploaded_assets
from runtime_gate import verify as verify_runtime


class PublicationGateTest(unittest.TestCase):
    @staticmethod
    def state(*, draft=True, unknown=False):
        assets = [
            {
                "name": "release.apk",
                "uploader": {"login": "release-uploader"},
            }
        ]
        if unknown:
            assets.append(
                {
                    "name": "operator-note.txt",
                    "uploader": {"login": "release-uploader"},
                }
            )
        return {
            "id": 42,
            "tagName": "v1.0.0",
            "isDraft": draft,
            "publishedAt": None if draft else "2026-08-09T00:00:00Z",
            "author": {"login": "release-please"},
            "assets": assets,
        }

    def test_mutation_requires_exact_draft_id_author_and_assets(self):
        verify_release_state(
            self.state(),
            release_id=42,
            tag="v1.0.0",
            uploader="release-uploader",
            release_author="release-please",
            allowed_names={"release.apk", "release-manifest.json"},
        )
        with self.assertRaises(MutationError):
            verify_release_state(
                self.state(unknown=True),
                release_id=42,
                tag="v1.0.0",
                uploader="release-uploader",
                release_author="release-please",
                allowed_names={"release.apk"},
            )
        with self.assertRaises(MutationError):
            verify_release_state(
                self.state(draft=False),
                release_id=42,
                tag="v1.0.0",
                uploader="release-uploader",
                release_author="release-please",
                allowed_names={"release.apk"},
            )

    def test_uploaded_assets_are_exact(self):
        verify_uploaded_assets(
            self.state(),
            release_id=42,
            tag="v1.0.0",
            uploader="release-uploader",
            release_author="release-please",
            expected_names={"release.apk"},
        )
        with self.assertRaises(MutationError):
            verify_uploaded_assets(
                self.state(),
                release_id=42,
                tag="v1.0.0",
                uploader="release-uploader",
                release_author="release-please",
                expected_names={"release.apk", "release-manifest.json"},
            )

    def test_recovery_requires_release_please_draft_and_exact_candidate(self):
        verify_recovery(
            {
                "id": 42,
                "tagName": "v1.0.0",
                "isDraft": True,
                "publishedAt": None,
            },
            release_id=42,
            tag="v1.0.0",
            source_sha="a" * 40,
            candidate={"tag": "v1.0.0", "commit": "a" * 40, "source_branch": "dev"},
        )
        with self.assertRaises(ValueError):
            verify_recovery(
                {
                    "id": 42,
                    "tagName": "v1.0.0",
                    "isDraft": False,
                    "publishedAt": "2026-08-09T00:00:00Z",
                },
                release_id=42,
                tag="v1.0.0",
                source_sha="a" * 40,
                candidate={"tag": "v1.0.0", "commit": "a" * 40, "source_branch": "dev"},
            )

    def test_runtime_gate_requires_every_external_input(self):
        evidence = {
            "firebase_package": "dev.whysoezzy.meet",
            "signing_certificate": "a" * 64,
            "tls_spki": ["b" * 64, "c" * 64],
            "backend_revision": "backend-2026-08-09",
            "authenticated_device": {
                "serial": "emulator-5554",
                "authenticated_before": True,
                "authenticated_after": True,
                "state_preserved": True,
            },
            "runtime_install": {
                "package": "dev.whysoezzy.meet",
                "installed": True,
                "state_preserved": True,
            },
            "runtime_authenticated_api": True,
        }
        verify_runtime(evidence)
        evidence["authenticated_device"]["state_preserved"] = False
        with self.assertRaises(ValueError):
            verify_runtime(evidence)
        evidence["authenticated_device"]["state_preserved"] = True
        evidence["reset_device_state"] = True
        with self.assertRaises(ValueError):
            verify_runtime(evidence)

    def test_recovery_binds_successful_stable_producer_and_zip_digest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            evidence = root / "evidence"
            evidence.mkdir()
            (evidence / "release-candidate.json").write_text(
                '{"tag":"v1.0.0","commit":"' + "a" * 40 + '","source_branch":"dev"}',
                encoding="utf-8",
            )
            (evidence / "release-authority.json").write_text(
                '{"tag":"v1.0.0","commit":"' + "a" * 40 + '","source_branch":"dev"}',
                encoding="utf-8",
            )
            statement = {
                "signer": "owner/repo/.github/workflows/release.yml",
                "source_ref": "refs/heads/dev",
                "source_sha": "a" * 40,
                "run_id": 77,
                "run_attempt": 2,
            }
            (evidence / "recovery-envelope.json").write_text(
                '{"attestations":[{"name":"artifact.attestation.json"}]}',
                encoding="utf-8",
            )
            (evidence / "artifact.attestation.json").write_text(
                '{"producer":{"statement":' + json.dumps(statement)
                + '},"authoritative":{"statement":' + json.dumps(statement) + '}}',
                encoding="utf-8",
            )
            archive = root / "evidence.zip"
            with zipfile.ZipFile(archive, "w") as output:
                output.write(evidence / "release-candidate.json", "release-candidate.json")
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            verify_producer(
                run={
                    "id": 77,
                    "path": ".github/workflows/release.yml",
                    "event": "push",
                    "head_branch": "dev",
                    "head_sha": "a" * 40,
                    "status": "completed",
                    "conclusion": "success",
                    "run_attempt": 2,
                },
                jobs={
                    "jobs": [{
                        "name": "Verify and attest stable artifacts",
                        "status": "completed",
                        "conclusion": "success",
                        "run_id": 77,
                        "run_attempt": 2,
                    }]
                },
                artifact={
                    "id": 9,
                    "name": "android-release-evidence-v1.0.0",
                    "digest": "sha256:" + digest,
                    "expired": False,
                    "workflow_run": {"id": 77},
                },
                archive=archive,
                evidence_directory=evidence,
                repository="owner/repo",
                source_run_id=77,
                source_sha="a" * 40,
                tag="v1.0.0",
                artifact_name="android-release-evidence-v1.0.0",
            )
            archive.write_bytes(archive.read_bytes() + b"tamper")
            with self.assertRaisesRegex(ValueError, "ZIP digest"):
                verify_producer(
                    run={"id": 77},
                    jobs=[],
                    artifact={"digest": "sha256:" + digest, "name": "x", "id": 9},
                    archive=archive,
                    evidence_directory=evidence,
                    repository="owner/repo",
                    source_run_id=77,
                    source_sha="a" * 40,
                    tag="v1.0.0",
                    artifact_name="android-release-evidence-v1.0.0",
                )
if __name__ == "__main__":
    unittest.main()
