import hashlib
import json
import tempfile
import unittest
from dataclasses import FrozenInstanceError
from pathlib import Path
from unittest.mock import Mock, patch

from github_attestation import (
    MAX_ATTESTATION_JSON_BYTES,
    SLSA_PROVENANCE_V1,
    AttestationError,
    AttestationPolicy,
    AttestedSubject,
    child_environment,
    parse_verified_result,
    verify_file,
)


class GitHubAttestationTest(unittest.TestCase):
    REPOSITORY = "owner/repo"
    SIGNER = "owner/repo/.github/workflows/release.yml"
    SOURCE_REF = "refs/heads/dev"
    SOURCE_DIGEST = "a" * 40

    @classmethod
    def policy(cls, **changes):
        values = {
            "repository": cls.REPOSITORY,
            "signer_workflow": cls.SIGNER,
            "source_ref": cls.SOURCE_REF,
            "source_digest": cls.SOURCE_DIGEST,
            "predicate_type": SLSA_PROVENANCE_V1,
            "result_limit": 100,
        }
        values.update(changes)
        return AttestationPolicy(**values)

    @staticmethod
    def result(subjects):
        return {
            "verificationResult": {
                "statement": {
                    "predicate": {"buildType": "fixture"},
                    "subject": [
                        {"name": name, "digest": {"sha256": digest}}
                        for name, digest in subjects
                    ],
                }
            }
        }

    def test_policy_is_immutable_and_requires_the_exact_contract(self):
        policy = self.policy()
        with self.assertRaises(FrozenInstanceError):
            policy.source_ref = "refs/heads/other"

        invalid = (
            {"repository": "owner"},
            {"signer_workflow": "repo/.github/workflows/release.yml"},
            {"source_ref": "refs/tags/v1"},
            {"source_digest": "A" * 40},
            {"predicate_type": "https://slsa.dev/provenance/v0.2"},
            {"result_limit": 99},
        )
        for change in invalid:
            with self.subTest(change=change):
                with self.assertRaises(AttestationError):
                    self.policy(**change)

    def test_child_environment_has_only_the_attestation_token_boundary(self):
        environment = child_environment(
            "attestation-secret",
            parent_environment={
                "PATH": "path",
                "RELEASE_API_TOKEN": "release-secret",
                "ATTESTATION_TOKEN": "parent-secret",
                "GH_TOKEN": "old-secret",
                "GITHUB_TOKEN": "github-secret",
                "SIGNING_PASSWORD": "signing-secret",
                "ANDROID_KEYSTORE_FILE": "keystore",
                "KEEP": "value",
            },
        )
        self.assertEqual(environment["GH_TOKEN"], "attestation-secret")
        self.assertEqual(environment["PATH"], "path")
        self.assertEqual(environment["KEEP"], "value")
        for name in (
            "RELEASE_API_TOKEN",
            "ATTESTATION_TOKEN",
            "GITHUB_TOKEN",
            "SIGNING_PASSWORD",
            "ANDROID_KEYSTORE_FILE",
        ):
            self.assertNotIn(name, environment)

    def test_exact_command_bounded_runner_and_complete_five_subject_group(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "Meet.apk"
            path.write_bytes(b"installer bytes")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            subjects = [
                ("release-manifest.json", "1" * 64),
                ("Meet.apk", digest),
                ("release-build.json", "2" * 64),
                ("signed.aab", "3" * 64),
                ("checksums.sha256", "4" * 64),
            ]
            calls = []

            def runner(command, *, env, stdout_limit):
                calls.append((command, env, stdout_limit))
                return json.dumps([self.result(subjects)]).encode("utf-8")

            verified = verify_file(
                path,
                self.policy(),
                token="attestation-secret",
                runner=runner,
            )

        self.assertEqual(
            calls[0][0],
            (
                "gh",
                "attestation",
                "verify",
                str(path),
                "--repo",
                self.REPOSITORY,
                "--format",
                "json",
                "--predicate-type",
                SLSA_PROVENANCE_V1,
                "--signer-workflow",
                self.SIGNER,
                "--source-ref",
                self.SOURCE_REF,
                "--source-digest",
                self.SOURCE_DIGEST,
                "--limit",
                "100",
            ),
        )
        self.assertEqual(calls[0][1]["GH_TOKEN"], "attestation-secret")
        self.assertEqual(calls[0][2], MAX_ATTESTATION_JSON_BYTES)
        self.assertEqual(
            [subject.name for subject in verified.statement_subjects],
            ["Meet.apk", "checksums.sha256", "release-build.json", "release-manifest.json", "signed.aab"],
        )
        self.assertEqual(verified.matched_subject.name, "Meet.apk")
        self.assertEqual(verified.file_sha256, digest)

    def test_result_cardinality_and_stdout_bound_are_fail_closed(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "Meet.apk"
            path.write_bytes(b"installer bytes")
            one = self.result([("Meet.apk", hashlib.sha256(path.read_bytes()).hexdigest())])

            for output in (
                b"{}",
                json.dumps([]).encode(),
                json.dumps([one, one]).encode(),
                json.dumps([1]).encode(),
                b"x" * (MAX_ATTESTATION_JSON_BYTES + 1),
            ):
                with self.subTest(output_size=len(output)):
                    with self.assertRaises(AttestationError):
                        verify_file(
                            path,
                            self.policy(),
                            token="secret",
                            runner=lambda *args, output=output, **kwargs: output,
                        )

            saturated = json.dumps([one] * 100).encode()
            with self.assertRaisesRegex(AttestationError, "pagination"):
                verify_file(
                    path,
                    self.policy(),
                    token="secret",
                    runner=lambda *args, **kwargs: saturated,
                )

    def test_subject_group_rejects_malformed_duplicates_and_wrong_target(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "Meet.apk"
            path.write_bytes(b"installer bytes")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()

            cases = (
                [("other.apk", digest)],
                [("Meet.apk", "f" * 64)],
                [("other.apk", "f" * 64)],
                [("Meet.apk", digest), ("Meet.apk", digest)],
                [("Meet.apk", digest), ("Meet.apk", "f" * 64)],
                [("Meet.apk", digest), ("other.apk", "f" * 64), ("other.apk", "e" * 64)],
            )
            for subjects in cases:
                with self.subTest(subjects=subjects):
                    with self.assertRaises(AttestationError):
                        verify_file(
                            path,
                            self.policy(),
                            token="secret",
                            runner=lambda *args, subjects=subjects, **kwargs: json.dumps(
                                [self.result(subjects)]
                            ).encode(),
                        )

    def test_subject_parser_requires_exact_sha256_digest_mapping_and_valid_names(self):
        valid = self.result([("Meet.apk", "a" * 64)])
        self.assertEqual(parse_verified_result(valid), (AttestedSubject("Meet.apk", "a" * 64),))

        malformed = (
            [{"name": "Meet.apk", "digest": {"sha256": "A" * 64}}],
            [{"name": "Meet.apk", "digest": {"sha256": "a" * 64, "sha512": "b" * 128}}],
            [{"name": "nested/Meet.apk", "digest": {"sha256": "a" * 64}}],
            [{"name": "", "digest": {"sha256": "a" * 64}}],
            [{"name": "Meet.apk", "digest": {"sha256": "short"}}],
        )
        for subjects in malformed:
            with self.subTest(subjects=subjects):
                value = {
                    "verificationResult": {
                        "statement": {"subject": subjects},
                    }
                }
                with self.assertRaises(AttestationError):
                    parse_verified_result(value)

    def test_collector_adapter_delegates_to_shared_verifier(self):
        from collect_attestation_evidence import run_gh

        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "Meet.apk"
            path.write_bytes(b"installer bytes")
            raw = self.result([("Meet.apk", hashlib.sha256(path.read_bytes()).hexdigest())])
            verified = Mock(raw_result=raw)
            with patch("collect_attestation_evidence.verify_file", return_value=verified) as verify:
                self.assertEqual(
                    run_gh(
                        path,
                        self.REPOSITORY,
                        self.SIGNER,
                        self.SOURCE_REF,
                        self.SOURCE_DIGEST,
                        attestation_token="secret",
                    ),
                    raw,
                )

            policy = verify.call_args.args[1]
            self.assertEqual(policy, self.policy())
            self.assertEqual(verify.call_args.kwargs["token"], "secret")


if __name__ == "__main__":
    unittest.main()
