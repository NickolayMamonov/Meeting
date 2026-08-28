#!/usr/bin/env python3
import base64
import json
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path

from package_artifacts import package
from release_evidence import (
    attestation_group_identity,
    attest_identity,
    canonical_json,
    sha256_bytes,
    verify_attestation_link,
)
from verify_chain import ChainError, verify
from verify_remote_assets import verify as verify_remote_assets


class PackageArtifactsTest(unittest.TestCase):
    @staticmethod
    def _write_evidence(output: Path, path: Path) -> None:
        manifest_path = (
            output / "snapshot-manifest.json"
            if (output / "snapshot-manifest.json").exists()
            else output / "release-manifest.json"
        )
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        names = [
            "release-authority.json",
            *(item["name"] for item in manifest["artifacts"]),
            manifest_path.name,
            "SHA256SUMS",
            "release-candidate.json",
        ]
        certificate_bytes = b"authoritative DER certificate"
        certificate = {
            "der_base64": base64.b64encode(certificate_bytes).decode("ascii")
        }
        certificate_sha256 = sha256_bytes(certificate_bytes)
        records = []
        for index, name in enumerate(names):
            subject = {
                "name": name,
                "sha256": sha256_bytes((output / name).read_bytes()),
            }
            rekor = {
                "log_id": "d" * 64,
                "log_index": index,
                "integrated_time": 1_700_000_000 + index,
            }
            statement = {
                "subject": subject,
                "predicate": "https://slsa.dev/provenance/v1",
                "signer": "owner/repo/.github/workflows/release.yml",
                "source_ref": "refs/heads/dev",
                "source_sha": "a" * 40,
                "run_id": 100,
                "run_attempt": 2,
                "certificate_sha256": certificate_sha256,
                "rekor": rekor,
            }
            bundle = {
                "media_type": "application/vnd.dev.sigstore.bundle.v0.3+json",
                "statement": statement,
                "certificate": certificate,
                "rekor": rekor,
                "signature": f"signature-{index}",
            }
            producer = {
                "bundle": bundle,
                "statement": statement,
                "certificate": certificate,
                "rekor": rekor,
            }
            records.append({
                "subject": subject,
                "producer": producer,
                "authoritative": json.loads(json.dumps(producer)),
            })
        path.write_text(json.dumps({"records": records}), encoding="utf-8")

    @staticmethod
    def _write_five_subject_group_evidence(output: Path, path: Path) -> None:
        manifest_path = (
            output / "snapshot-manifest.json"
            if (output / "snapshot-manifest.json").exists()
            else output / "release-manifest.json"
        )
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        names = [
            "release-authority.json",
            *(item["name"] for item in manifest["artifacts"]),
            manifest_path.name,
            "SHA256SUMS",
            "release-candidate.json",
        ]
        certificate_bytes = b"five-subject authoritative DER certificate"
        certificate = {
            "der_base64": base64.b64encode(certificate_bytes).decode("ascii")
        }
        signed_subjects = [
            {
                "name": name,
                "digest": {"sha256": sha256_bytes((output / name).read_bytes())},
            }
            for name in names
        ]
        signed_statement = {
            "predicate": {"buildType": "https://slsa.dev/provenance/v1"},
            "subject": signed_subjects,
        }
        payload = json.dumps(signed_statement, separators=(",", ":")).encode("utf-8")
        rekor = {"log_id": "f" * 64, "log_index": 17, "integrated_time": 1_700_000_100}
        records = []
        for subject in signed_subjects:
            canonical_subject = {
                "name": subject["name"],
                "sha256": subject["digest"]["sha256"],
            }
            statement = {
                "subject": canonical_subject,
                "predicate": signed_statement["predicate"],
                "source_repository": "owner/repo",
                "signer": "owner/repo/.github/workflows/release.yml",
                "source_ref": "refs/heads/dev",
                "source_sha": "a" * 40,
                "run_id": 101,
                "run_attempt": 1,
                "payload_sha256": sha256_bytes(payload),
                "certificate_sha256": sha256_bytes(certificate_bytes),
                "rekor": rekor,
            }
            bundle = {
                "media_type": "application/vnd.dev.sigstore.bundle.v0.3+json",
                "statement": statement,
                "certificate": certificate,
                "rekor": rekor,
                "signature": {"payload": base64.b64encode(payload).decode("ascii")},
            }
            producer = {
                "bundle": bundle,
                "statement": statement,
                "certificate": certificate,
                "rekor": rekor,
            }
            group = attestation_group_identity(bundle, statement, certificate, rekor)
            records.append({
                "subject": canonical_subject,
                "rekor_identity": group.rekor_identity,
                "attestation_group": group.to_mapping(),
                "producer": producer,
                "authoritative": json.loads(json.dumps(producer)),
            })
        path.write_text(json.dumps({"records": records}), encoding="utf-8")

    @classmethod
    def package_release(cls, root_path: Path) -> Path:
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
                    "releaseBaseUrl": "https://api.whysoezzy.online",
                    "releaseHost": "api.whysoezzy.online",
                }
            ),
            encoding="utf-8",
        )
        apk = root_path / "app.apk"
        aab = root_path / "app.aab"
        apk.write_bytes(b"apk bytes")
        aab.write_bytes(b"aab bytes")
        output = root_path / "out"
        arguments = Namespace(
            metadata=str(metadata), output=str(output), apk=str(apk),
            aab=str(aab), mapping=None, symbols=None, tag="v1.0.0",
            commit="a" * 40, source_branch="dev", workflow="test",
            attestation_evidence=None, prepare_only=True,
        )
        package(arguments)
        evidence = root_path / "attestation-evidence.json"
        cls._write_evidence(output, evidence)
        arguments.attestation_evidence = str(evidence)
        arguments.prepare_only = False
        package(arguments)
        return output

    @classmethod
    def package_five_subject_group_release(cls, root_path: Path) -> Path:
        metadata = root_path / "metadata.json"
        metadata.write_text(
            json.dumps({
                "channel": "snapshot",
                "applicationId": "dev.whysoezzy.meet.snapshot",
                "versionName": "1.0.0-snapshot.1.1+aaaaaaaaaaaa",
                "versionCode": 1,
                "variant": "snapshot",
                "commitSha": "a" * 40,
                "sourceBranch": "dev",
                "workflow": "test",
                "expectedCertificateSha256": "b" * 64,
            }),
            encoding="utf-8",
        )
        apk = root_path / "app.apk"
        aab = root_path / "app.aab"
        apk.write_bytes(b"apk bytes")
        aab.write_bytes(b"aab bytes")
        output = root_path / "out"
        arguments = Namespace(
            metadata=str(metadata), output=str(output), apk=str(apk),
            aab=None, mapping=None, symbols=None, tag=None,
            commit="a" * 40, source_branch="dev", workflow="test",
            attestation_evidence=None, prepare_only=True,
        )
        package(arguments)
        evidence = root_path / "attestation-evidence.json"
        cls._write_five_subject_group_evidence(output, evidence)
        arguments.attestation_evidence = str(evidence)
        arguments.prepare_only = False
        package(arguments)
        return output

    def test_remote_assets_are_exact_and_digest_checked(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            local = root_path / "local"
            remote = root_path / "remote"
            local.mkdir()
            remote.mkdir()
            (local / "Meet.apk").write_bytes(b"apk")
            (remote / "Meet.apk").write_bytes(b"apk")
            assets = {
                "assets": [{
                    "id": 1,
                    "name": "Meet.apk",
                    "size": 3,
                    "digest": "sha256:dd37c2d7274f7ea982cb83390c36918fee9ce8889073c44b68cdc00bdb8c3e04",
                }]
            }
            manifest = root_path / "assets.json"
            manifest.write_text(json.dumps(assets), encoding="utf-8")
            verify_remote_assets(local / "Meet.apk", manifest, remote / "Meet.apk")
            (remote / "Meet.apk").write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                verify_remote_assets(local / "Meet.apk", manifest, remote / "Meet.apk")

    def test_chain_is_acyclic_and_checksums_are_byte_sorted(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            output = self.package_release(root_path)
            verify(output)
            manifest = json.loads((output / "release-manifest.json").read_text())
            candidate = json.loads((output / "release-candidate.json").read_text())
            envelope = json.loads((output / "attestation-index.json").read_text())
            self.assertEqual(manifest["tag"], "v1.0.0")
            self.assertNotIn(b"\r", (output / "SHA256SUMS").read_bytes())
            self.assertNotIn("release-candidate.json", (output / "SHA256SUMS").read_text())
            self.assertNotIn("attestation-index.json", (output / "SHA256SUMS").read_text())
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

    def test_five_subject_shared_rekor_propagates_through_package_and_chain(self):
        with tempfile.TemporaryDirectory() as root:
            output = self.package_five_subject_group_release(Path(root))
            verify(output)
            envelope = json.loads((output / "attestation-index.json").read_text(encoding="utf-8"))
            groups = []
            for reference in envelope["attestations"]:
                attestation = json.loads(
                    (output / reference["name"]).read_text(encoding="utf-8")
                )
                groups.append(attestation["attestation_group"])
                self.assertEqual(
                    reference["attestation_group"],
                    attestation["attestation_group"],
                )
            self.assertEqual(len({group["identity"] for group in groups}), 1)
            self.assertEqual(len(groups[0]["subjects"]), 5)
            self.assertEqual(
                len({reference["rekor_identity"] for reference in envelope["attestations"]}),
                1,
            )

    def test_shared_rekor_partial_group_fails_package_boundary(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            output = self.package_five_subject_group_release(root_path)
            evidence_path = root_path / "attestation-evidence.json"
            evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
            evidence["records"] = evidence["records"][:-1]
            evidence_path.write_bytes(canonical_json(evidence))
            arguments = Namespace(
                metadata=str(root_path / "metadata.json"), output=str(output),
                apk=str(root_path / "app.apk"), aab=None,
                mapping=None, symbols=None, tag=None, commit="a" * 40,
                source_branch="dev", workflow="test",
                attestation_evidence=str(evidence_path), prepare_only=False,
            )
            with self.assertRaises(SystemExit):
                package(arguments)

    def test_outer_subject_swap_fails_package_boundary(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            output = self.package_five_subject_group_release(root_path)
            evidence_path = root_path / "attestation-evidence.json"
            evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
            evidence["records"][0]["subject"], evidence["records"][1]["subject"] = (
                evidence["records"][1]["subject"],
                evidence["records"][0]["subject"],
            )
            evidence_path.write_bytes(canonical_json(evidence))
            arguments = Namespace(
                metadata=str(root_path / "metadata.json"), output=str(output),
                apk=str(root_path / "app.apk"), aab=None,
                mapping=None, symbols=None, tag=None, commit="a" * 40,
                source_branch="dev", workflow="test",
                attestation_evidence=str(evidence_path), prepare_only=False,
            )
            with self.assertRaises(SystemExit):
                package(arguments)

    def test_shared_rekor_group_boundaries_fail_closed_in_chain(self):
        with tempfile.TemporaryDirectory() as root:
            output = self.package_five_subject_group_release(Path(root))
            envelope_path = output / "attestation-index.json"
            envelope = json.loads(envelope_path.read_text(encoding="utf-8"))
            first_reference = envelope["attestations"][0]
            second_reference = envelope["attestations"][1]
            first_path = output / first_reference["name"]
            second_path = output / second_reference["name"]
            first = json.loads(first_path.read_text(encoding="utf-8"))
            second = json.loads(second_path.read_text(encoding="utf-8"))

            tampered = json.loads(json.dumps(first))
            tampered_group = tampered["attestation_group"]
            tampered_group["source_ref"] = "refs/heads/tampered"
            tampered_path_bytes = canonical_json(tampered)
            first_path.write_bytes(tampered_path_bytes)
            first_reference["sha256"] = sha256_bytes(tampered_path_bytes)
            envelope_path.write_bytes(canonical_json(envelope))
            with self.assertRaises(ChainError):
                verify(output)

            output = self.package_five_subject_group_release(Path(root))
            envelope_path = output / "attestation-index.json"
            envelope = json.loads(envelope_path.read_text(encoding="utf-8"))
            first_reference = envelope["attestations"][0]
            second_reference = envelope["attestations"][1]
            first_path = output / first_reference["name"]
            second_path = output / second_reference["name"]
            first = json.loads(first_path.read_text(encoding="utf-8"))
            second = json.loads(second_path.read_text(encoding="utf-8"))
            second.pop("attestation_group")
            second_bytes = canonical_json(second)
            second_path.write_bytes(second_bytes)
            second_reference.pop("attestation_group")
            second_reference["sha256"] = sha256_bytes(second_bytes)
            envelope_path.write_bytes(canonical_json(envelope))
            with self.assertRaises(ChainError):
                verify(output)

            output = self.package_five_subject_group_release(Path(root))
            envelope_path = output / "attestation-index.json"
            envelope = json.loads(envelope_path.read_text(encoding="utf-8"))
            first_reference = envelope["attestations"][0]
            second_reference = envelope["attestations"][1]
            first_path = output / first_reference["name"]
            second_path = output / second_reference["name"]
            first = json.loads(first_path.read_text(encoding="utf-8"))
            second = json.loads(second_path.read_text(encoding="utf-8"))
            second["attestation_group"]["source_ref"] = "refs/heads/divergent"
            second_bytes = canonical_json(second)
            second_path.write_bytes(second_bytes)
            second_reference["attestation_group"] = second["attestation_group"]
            second_reference["sha256"] = sha256_bytes(second_bytes)
            envelope_path.write_bytes(canonical_json(envelope))
            with self.assertRaises(ChainError):
                verify(output)

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
                attestation_evidence=None, prepare_only=True,
            )
            metadata.write_text(
                json.dumps({
                    **json.loads(metadata.read_text(encoding="utf-8")),
                    "sourceBranch": "dev",
                }),
                encoding="utf-8",
            )
            package(arguments)
            apk.write_bytes(b"second")
            package(arguments)
            self.assertEqual(unrelated.read_text(encoding="utf-8"), "keep")
            self.assertIn("operator-note.txt", {path.name for path in output.iterdir()})

    def test_attestation_identity_tamper_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            output = self.package_release(Path(root))
            envelope_path = output / "attestation-index.json"
            envelope = json.loads(envelope_path.read_text(encoding="utf-8"))
            reference = envelope["attestations"][0]
            attestation_path = output / reference["name"]
            attestation = json.loads(attestation_path.read_text(encoding="utf-8"))
            attestation["canonical_bundle_sha256"] = "0" * 64
            encoded = canonical_json(attestation)
            attestation_path.write_bytes(encoded)
            reference["sha256"] = sha256_bytes(encoded)
            envelope_path.write_bytes(canonical_json(envelope))
            with self.assertRaises(ChainError):
                verify(output)

    def test_recomputed_producer_bundle_cannot_replace_authoritative_bundle(self):
        with tempfile.TemporaryDirectory() as root:
            output = self.package_release(Path(root))
            envelope_path = output / "attestation-index.json"
            envelope = json.loads(envelope_path.read_text(encoding="utf-8"))
            reference = envelope["attestations"][0]
            attestation_path = output / reference["name"]
            attestation = json.loads(attestation_path.read_text(encoding="utf-8"))
            producer = attestation["producer"]
            producer["statement"]["source_ref"] = "refs/heads/master"
            producer["bundle"]["statement"] = producer["statement"]
            linked = attest_identity(
                producer["bundle"],
                producer["statement"],
                producer["certificate"],
                producer["rekor"],
            )
            attestation.update(linked)
            encoded = canonical_json(attestation)
            attestation_path.write_bytes(encoded)
            reference.update(linked)
            reference["sha256"] = sha256_bytes(encoded)
            envelope_path.write_bytes(canonical_json(envelope))
            with self.assertRaises(ChainError):
                verify(output)

    def test_attestation_coverage_and_uniqueness_are_exact(self):
        with tempfile.TemporaryDirectory() as root:
            output = self.package_release(Path(root))
            envelope_path = output / "attestation-index.json"
            original = json.loads(envelope_path.read_text(encoding="utf-8"))
            envelope_path.write_bytes(
                canonical_json({
                    **original,
                    "attestations": original["attestations"][:-1],
                })
            )
            with self.assertRaises(ChainError):
                verify(output)
            envelope_path.write_bytes(
                canonical_json({
                    **original,
                    "attestations": [
                        *original["attestations"],
                        dict(original["attestations"][0]),
                    ],
                })
            )
            with self.assertRaises(ChainError):
                verify(output)

    def test_duplicate_rekor_identity_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            output = self.package_release(Path(root))
            envelope_path = output / "attestation-index.json"
            envelope = json.loads(envelope_path.read_text(encoding="utf-8"))
            first_path = output / envelope["attestations"][0]["name"]
            second_reference = envelope["attestations"][1]
            second_path = output / second_reference["name"]
            first = json.loads(first_path.read_text(encoding="utf-8"))
            second = json.loads(second_path.read_text(encoding="utf-8"))
            for source_name in ("producer", "authoritative"):
                source = second[source_name]
                source["rekor"] = json.loads(
                    json.dumps(first["producer"]["rekor"])
                )
                source["statement"]["rekor"] = source["rekor"]
                source["bundle"]["statement"] = source["statement"]
                source["bundle"]["certificate"] = source["certificate"]
                source["bundle"]["rekor"] = source["rekor"]
            linked = verify_attestation_link(
                second["producer"],
                second["authoritative"],
            )
            second.update(linked)
            encoded = canonical_json(second)
            second_path.write_bytes(encoded)
            second_reference.update(linked)
            second_reference["sha256"] = sha256_bytes(encoded)
            envelope_path.write_bytes(canonical_json(envelope))
            with self.assertRaises(ChainError):
                verify(output)


if __name__ == "__main__":
    unittest.main()
