#!/usr/bin/env python3
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from recovery_gate import verify as verify_recovery, verify_producer
from release_mutation_gate import (
    MutationError,
    expected_release_asset_names,
    verify_release_state,
    verify_uploaded_assets,
)
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
            "release_id": 42,
            "tag": "v1.0.0",
            "source_sha": "a" * 40,
            "candidate_sha256": "c" * 64,
            "manifest_sha256": "m" * 64,
            "firebase_package": "dev.whysoezzy.meet",
            "signing_certificate": "a" * 64,
            "tls_spki": ["pin-a", "pin-b"],
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
        manifest = {
            "schema": 1,
            "channel": "release",
            "tag": "v1.0.0",
            "commit": "a" * 40,
            "source_branch": "dev",
            "application_id": "dev.whysoezzy.meet",
            "signing_fingerprint": "a" * 64,
            "spki_pin_digests": ["pin-a", "pin-b"],
        }
        candidate = {
            "tag": "v1.0.0",
            "commit": "a" * 40,
            "manifest": {"sha256": "m" * 64},
        }
        verify_runtime(
            evidence,
            release_id=42,
            tag="v1.0.0",
            source_sha="a" * 40,
            candidate=candidate,
            manifest=manifest,
            candidate_sha256="c" * 64,
            manifest_sha256="m" * 64,
        )
        evidence["authenticated_device"]["state_preserved"] = False
        with self.assertRaises(ValueError):
            verify_runtime(
                evidence,
                release_id=42,
                tag="v1.0.0",
                source_sha="a" * 40,
                candidate=candidate,
                manifest=manifest,
                candidate_sha256="c" * 64,
                manifest_sha256="m" * 64,
            )
        evidence["authenticated_device"]["state_preserved"] = True
        evidence["reset_device_state"] = True
        with self.assertRaises(ValueError):
            verify_runtime(
                evidence,
                release_id=42,
                tag="v1.0.0",
                source_sha="a" * 40,
                candidate=candidate,
                manifest=manifest,
                candidate_sha256="c" * 64,
                manifest_sha256="m" * 64,
            )
        evidence["reset_device_state"] = False
        evidence["signing_certificate"] = "d" * 64
        with self.assertRaisesRegex(ValueError, "certificate"):
            verify_runtime(
                evidence,
                release_id=42,
                tag="v1.0.0",
                source_sha="a" * 40,
                candidate=candidate,
                manifest=manifest,
                candidate_sha256="c" * 64,
                manifest_sha256="m" * 64,
            )
        evidence["signing_certificate"] = "a" * 64
        evidence["tls_spki"] = ["pin-a", "pin-c"]
        with self.assertRaisesRegex(ValueError, "SPKI"):
            verify_runtime(
                evidence,
                release_id=42,
                tag="v1.0.0",
                source_sha="a" * 40,
                candidate=candidate,
                manifest=manifest,
                candidate_sha256="c" * 64,
                manifest_sha256="m" * 64,
            )
        evidence["tls_spki"] = ["pin-a", "pin-b"]
        evidence["tag"] = "v1.0.1"
        with self.assertRaisesRegex(ValueError, "identity"):
            verify_runtime(
                evidence,
                release_id=42,
                tag="v1.0.0",
                source_sha="a" * 40,
                candidate=candidate,
                manifest=manifest,
                candidate_sha256="c" * 64,
                manifest_sha256="m" * 64,
            )
        evidence["tag"] = "v1.0.0"
        evidence["candidate_sha256"] = "d" * 64
        with self.assertRaisesRegex(ValueError, "candidate digest"):
            verify_runtime(
                evidence,
                release_id=42,
                tag="v1.0.0",
                source_sha="a" * 40,
                candidate=candidate,
                manifest=manifest,
                candidate_sha256="c" * 64,
                manifest_sha256="m" * 64,
            )

    def test_release_asset_allowlist_rejects_unreferenced_extra(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            names = {
                "release-authority.json",
                "release-manifest.json",
                "SHA256SUMS",
                "release-candidate.json",
                "recovery-envelope.json",
                "app.apk",
                "app.aab",
                "app.apk.attestation.json",
            }
            for name in names:
                (directory / name).write_text("{}", encoding="utf-8")
            (directory / "release-manifest.json").write_text(
                json.dumps(
                    {
                        "schema": 1,
                        "channel": "release",
                        "tag": "v1.0.0",
                        "commit": "a" * 40,
                        "artifacts": [
                            {"name": "app.apk", "type": "apk"},
                            {"name": "app.aab", "type": "aab"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            (directory / "release-candidate.json").write_text(
                json.dumps({"tag": "v1.0.0", "commit": "a" * 40}),
                encoding="utf-8",
            )
            (directory / "recovery-envelope.json").write_text(
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

    def test_release_workflow_dispatch_inputs_are_shell_safe_and_ordered(self):
        workflow = (
            Path(__file__).parents[2] / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")
        run_bodies: list[str] = []
        in_run = False
        for line in workflow.splitlines():
            if line == "        run: |":
                in_run = True
                continue
            if in_run and line.startswith("          "):
                run_bodies.append(line)
                continue
            in_run = False
        self.assertNotIn("${{ inputs.", "\n".join(run_bodies))
        recovery = workflow.split("\n  recovery:\n", 1)[1].split(
            "\n  recovery-mutate:\n", 1
        )[0]
        self.assertLess(
            recovery.index("Validate workflow dispatch inputs"),
            recovery.index("actions/checkout@"),
        )
        self.assertLess(
            recovery.index("actions/checkout@"),
            recovery.index("unzip -q"),
        )
        self.assertIn(
            "actions/runs/$SOURCE_RUN_ID/artifacts?per_page=100",
            recovery,
        )
        self.assertIn("--paginate --slurp", recovery)
        mutation = workflow.split("\n  stable-mutate:\n", 1)[1]
        self.assertNotIn("gh release upload", mutation)
        self.assertIn('releases/$RELEASE_ID/assets?name=$name', mutation)

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
