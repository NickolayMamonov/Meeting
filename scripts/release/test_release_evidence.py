#!/usr/bin/env python3
import base64
import json
import unittest

from release_evidence import (
    AttestedSubject,
    AttestationGroupIdentity,
    EvidenceError,
    attest_identity,
    attestation_group_identity,
    effective_singleton_queue,
    map_merge_group_to_pr,
    pull_request_tuple,
    required_check_decision,
    select_latest_producer_evidence,
    sha256_bytes,
    sha256_jcs,
    stable_read,
    verify_attestation_link,
    verify_attestation_groups,
    verify_merge_group_event,
    verify_pull_request_snapshot,
    verify_two_parent_merge_commit,
)


WORKFLOW_PATH = ".github/workflows/release-please-credential-audit.yml"


class EvidenceTest(unittest.TestCase):
    @staticmethod
    def pr(*, head_sha="h", base_sha="base", classification="release"):
        return {
            "number": 7,
            "state": "open",
            "draft": False,
            "labels": [{"name": classification}],
            "body": "",
            "head": {
                "sha": head_sha,
                "ref": "feature",
                "repo": {"full_name": "owner/repo"},
            },
            "base": {
                "sha": base_sha,
                "ref": "dev",
                "repo": {"full_name": "owner/repo"},
            },
        }

    @staticmethod
    def attestation_parts():
        certificate_bytes = b"canonical DER certificate"
        certificate = {
            "der_base64": base64.b64encode(certificate_bytes).decode("ascii")
        }
        rekor = {"log_id": "d" * 64, "log_index": 4, "integrated_time": 9}
        statement = {
            "subject": {"name": "app.apk", "sha256": "a" * 64},
            "predicate": "https://slsa.dev/provenance/v1",
            "signer": "owner/repo/.github/workflows/release.yml",
            "source_ref": "refs/heads/dev",
            "source_sha": "b" * 40,
            "run_id": 12,
            "run_attempt": 2,
            "certificate_sha256": sha256_bytes(certificate_bytes),
            "rekor": rekor,
        }
        bundle = {
            "media_type": "application/vnd.dev.sigstore.bundle.v0.3+json",
            "statement": statement,
            "certificate": certificate,
            "rekor": rekor,
            "signature": "MEUCIQcanonical",
        }
        return bundle, statement, certificate, rekor

    def test_only_explicit_non_release_is_na(self):
        self.assertEqual(
            required_check_decision(
                "pull_request",
                {"labels": [{"name": "non-release"}], "body": "",
                 "head": {"ref": "feature"}},
                {},
            ),
            "N/A",
        )
        with self.assertRaises(EvidenceError):
            required_check_decision("pull_request", {"labels": [], "body": ""}, {})

    def test_pull_request_snapshots_are_exact_and_independent(self):
        event = self.pr()
        first = self.pr()
        second = self.pr()
        verified = verify_pull_request_snapshot(
            event, first, second, repository="owner/repo"
        )
        self.assertEqual(pull_request_tuple(verified), pull_request_tuple(event))
        with self.assertRaises(EvidenceError):
            verify_pull_request_snapshot(
                event, first, self.pr(head_sha="moved"), repository="owner/repo"
            )
        with self.assertRaises(EvidenceError):
            verify_pull_request_snapshot(
                event, first, first, repository="owner/repo"
            )

    def test_merge_queue_maps_exact_tuple_and_checks(self):
        rule = {
            "target_branch": "dev", "enabled": True, "max_entries": 1,
            "min_entries": 1, "batch_size": 1, "grouping": "NONE",
            "entries_exhaustive": True,
            "required_checks": [
                "continuous-integration",
                "release-please-credential-audit",
            ],
        }
        entry = {
            "id": "q1", "state": "QUEUED", "target_branch": "dev", "exhaustive": True,
            "pull_request_numbers": [7], "head_sha": "head",
            "base_ref": "dev", "base_sha": "base",
            "required_check_conclusions": {
                "continuous-integration": "success",
                "release-please-credential-audit": "success",
            },
        }
        selected = effective_singleton_queue([rule], [entry])
        mapped = map_merge_group_to_pr(
            {"queue_entry_id": "q1", "target_branch": "dev",
             "pr_tuple": (7, "head", "dev", "base")},
            [entry],
            {7: {"number": 7, "state": "open", "draft": False,
                 "head_sha": "head", "base_ref": "dev", "base_sha": "base"}},
            required_checks=selected["required_checks"],
        )
        self.assertEqual(mapped["tuple"], (7, "head", "dev", "base"))
        with self.assertRaises(EvidenceError):
            effective_singleton_queue([rule], [entry, {**entry, "id": "q2"}])
        with self.assertRaises(EvidenceError):
            effective_singleton_queue(
                [{**rule, "required_checks": ["continuous-integration"]}], [entry]
            )
        with self.assertRaises(EvidenceError):
            map_merge_group_to_pr(
                {"queue_entry_id": "q1", "target_branch": "dev",
                 "pr_tuple": (7, "head", "dev", "base")},
                [{**entry, "head_sha": "moved"}],
                {7: {"number": 7, "state": "open", "draft": False,
                     "head_sha": "head", "base_ref": "dev", "base_sha": "base"}},
                required_checks=rule["required_checks"],
            )
        with self.assertRaises(EvidenceError):
            map_merge_group_to_pr(
                {"queue_entry_id": "q1", "target_branch": "dev",
                 "pr_tuple": (7, "head", "dev", "base")},
                [{**entry, "state": "DEQUEUED"}],
                {7: {"number": 7, "state": "open", "draft": False,
                     "head_sha": "head", "base_ref": "dev", "base_sha": "base"}},
                required_checks=rule["required_checks"],
            )

    def test_merge_group_event_and_parent_order_are_exact(self):
        event = {
            "action": "checks_requested",
            "repository": {"full_name": "owner/repo"},
            "merge_group": {
                "head_sha": "group",
                "head_ref": "refs/heads/gh-readonly-queue/dev/pr-7",
                "base_sha": "base",
                "base_ref": "refs/heads/dev",
            },
        }
        group = {
            **event["merge_group"],
            "queue_entry_id": "q1",
            "target_branch": "dev",
            "pr_tuple": [7, "head", "dev", "base"],
        }
        commit = {
            "sha": "group",
            "parents": [{"sha": "base"}, {"sha": "head"}],
        }
        verify_merge_group_event(
            event, group, commit, repository="owner/repo"
        )
        verify_two_parent_merge_commit(commit, "base", "head")
        with self.assertRaises(EvidenceError):
            verify_two_parent_merge_commit(
                {"parents": [{"sha": "head"}, {"sha": "base"}]},
                "base",
                "head",
            )
        with self.assertRaises(EvidenceError):
            verify_merge_group_event(
                {**event, "action": "destroyed"},
                group,
                commit,
                repository="owner/repo",
            )

    def test_canonical_attestation_identity_uses_content_bytes(self):
        bundle, statement, certificate, rekor = self.attestation_parts()
        linked = attest_identity(bundle, statement, certificate, rekor)
        self.assertEqual(linked["canonical_bundle_sha256"], sha256_jcs(bundle))
        self.assertEqual(linked["statement_sha256"], sha256_jcs(statement))
        expected_certificate = sha256_bytes(
            base64.b64decode(certificate["der_base64"])
        )
        self.assertEqual(linked["certificate_identity"], expected_certificate)
        self.assertFalse(sha256_jcs(bundle).endswith("\n"))

    def test_attested_subject_rejects_non_local_names_cross_platform(self):
        for name in (".", "..", "nested/name.apk", r"nested\name.apk", r"C:artifact.apk", "line\nbreak.apk", "tab\t.apk", "nul\x00.apk"):
            with self.subTest(name=repr(name)):
                with self.assertRaises(EvidenceError):
                    AttestedSubject(name, "a" * 64)

    def test_attestation_group_subjects_require_a_list(self):
        bundle, statement, certificate, rekor = self.attestation_parts()
        payload_statement = {
            "predicate": {"buildType": "sanitized"},
            "subject": [{"name": "app.apk", "digest": {"sha256": "a" * 64}}],
        }
        payload = json.dumps(payload_statement, separators=(",", ":")).encode("utf-8")
        statement = {
            **statement,
            "predicate": payload_statement["predicate"],
            "source_repository": "owner/repo",
            "payload_sha256": sha256_bytes(payload),
        }
        bundle = {
            **bundle,
            "statement": statement,
            "signature": {"payload": base64.b64encode(payload).decode("ascii")},
        }
        group = attestation_group_identity(bundle, statement, certificate, rekor)
        for subjects in (None, 1, {}, "app.apk"):
            with self.subTest(subjects=subjects):
                with self.assertRaises(EvidenceError):
                    AttestationGroupIdentity.from_mapping(
                        {**group.to_mapping(), "subjects": subjects}
                    )

    def test_five_subject_attestation_group_is_deterministic_and_complete(self):
        certificate_bytes = b"group certificate"
        certificate = {
            "der_base64": base64.b64encode(certificate_bytes).decode("ascii")
        }
        rekor = {"log_id": "e" * 64, "log_index": 7, "integrated_time": 11}
        signed_subjects = [
            {"name": f"artifact-{index}.apk", "digest": {"sha256": f"{index:064x}"}}
            for index in range(1, 6)
        ]
        signed_statement = {
            "predicate": {"buildType": "https://slsa.dev/provenance/v1"},
            "subject": signed_subjects,
        }
        payload = json.dumps(signed_statement, separators=(",", ":")).encode("utf-8")
        groups = []
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
                "run_id": 42,
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
            group = attestation_group_identity(bundle, statement, certificate, rekor)
            groups.append(group)
        self.assertEqual({group.identity for group in groups}, {groups[0].identity})
        self.assertEqual(len(groups[0].subjects), 5)
        self.assertEqual(
            [subject.name for subject in groups[0].subjects],
            [f"artifact-{index}.apk" for index in range(1, 6)],
        )
        verify_attestation_groups(
            [
                {
                    "subject": subject.to_mapping(),
                    "rekor_identity": groups[0].rekor_identity,
                    "attestation_group": groups[0],
                }
                for subject in groups[0].subjects
            ]
        )

    def test_attestation_group_rejects_tamper_partial_and_legacy_reuse(self):
        bundle, statement, certificate, rekor = self.attestation_parts()
        payload_statement = {
            "predicate": statement["predicate"],
            "subject": [{"name": "app.apk", "digest": {"sha256": "a" * 64}}],
        }
        payload = json.dumps(payload_statement, separators=(",", ":")).encode("utf-8")
        statement = {
            **statement,
            "source_repository": "owner/repo",
            "payload_sha256": sha256_bytes(payload),
        }
        bundle = {
            **bundle,
            "statement": statement,
            "signature": {"payload": base64.b64encode(payload).decode("ascii")},
        }
        group = attestation_group_identity(bundle, statement, certificate, rekor)
        with self.assertRaises(EvidenceError):
            AttestationGroupIdentity.from_mapping({**group.to_mapping(), "identity": "0" * 64})
        with self.assertRaises(EvidenceError):
            verify_attestation_groups(
                [
                    {
                        "subject": {"name": "app.apk", "sha256": "a" * 64},
                        "rekor_identity": group.rekor_identity,
                        "attestation_group": group.to_mapping(),
                    },
                    {
                        "subject": {"name": "other.apk", "sha256": "b" * 64},
                        "rekor_identity": group.rekor_identity,
                    },
                ]
            )
        verify_attestation_groups(
            [{
                "subject": {"name": "app.apk", "sha256": "a" * 64},
                "rekor_identity": group.rekor_identity,
            }]
        )

    def test_attestation_group_rejects_partial_mixed_and_divergent_buckets(self):
        certificate_bytes = b"group matrix certificate"
        certificate = {
            "der_base64": base64.b64encode(certificate_bytes).decode("ascii")
        }
        rekor = {"log_id": "c" * 64, "log_index": 8, "integrated_time": 12}
        signed_subjects = [
            {"name": f"subject-{index}.apk", "digest": {"sha256": f"{index:064x}"}}
            for index in range(1, 4)
        ]
        signed_statement = {
            "predicate": {"buildType": "matrix"},
            "subject": signed_subjects,
        }
        payload = json.dumps(signed_statement, separators=(",", ":")).encode("utf-8")
        groups = []
        for subject in signed_subjects:
            statement = {
                "subject": {
                    "name": subject["name"],
                    "sha256": subject["digest"]["sha256"],
                },
                "predicate": signed_statement["predicate"],
                "source_repository": "owner/repo",
                "signer": "owner/repo/.github/workflows/release.yml",
                "source_ref": "refs/heads/dev",
                "source_sha": "b" * 40,
                "run_id": 99,
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
            groups.append(attestation_group_identity(bundle, statement, certificate, rekor))
        records = [
            {
                "subject": group.subjects[index].to_mapping(),
                "rekor_identity": group.rekor_identity,
                "attestation_group": group,
            }
            for index, group in enumerate(groups)
        ]
        with self.assertRaises(EvidenceError):
            verify_attestation_groups(records[:2])
        with self.assertRaises(EvidenceError):
            verify_attestation_groups(
                [records[0], {
                    "subject": records[1]["subject"],
                    "rekor_identity": groups[1].rekor_identity,
                }]
            )
        divergent_mapping = groups[1].to_mapping()
        divergent_mapping["source_ref"] = "refs/heads/other"
        divergent_without_identity = {
            key: value for key, value in divergent_mapping.items() if key != "identity"
        }
        divergent_mapping["identity"] = sha256_jcs(divergent_without_identity)
        divergent = AttestationGroupIdentity.from_mapping(divergent_mapping)
        with self.assertRaises(EvidenceError):
            verify_attestation_groups([
                records[0],
                {
                    "subject": records[1]["subject"],
                    "rekor_identity": divergent.rekor_identity,
                    "attestation_group": divergent,
                },
            ])

    def test_attestation_rejects_component_and_authority_tamper(self):
        bundle, statement, certificate, rekor = self.attestation_parts()
        producer = {
            "bundle": bundle, "statement": statement,
            "certificate": certificate, "rekor": rekor,
        }
        authoritative = {
            key: (
                dict(value) if isinstance(value, dict) else value
            )
            for key, value in producer.items()
        }
        authoritative["bundle"] = {
            **bundle,
            "statement": authoritative["statement"],
            "certificate": authoritative["certificate"],
            "rekor": authoritative["rekor"],
        }
        verify_attestation_link(producer, authoritative)
        tampered_statement = {**statement, "source_ref": "refs/heads/master"}
        tampered_bundle = {**bundle, "statement": tampered_statement}
        with self.assertRaises(EvidenceError):
            verify_attestation_link(
                {
                    **producer,
                    "bundle": tampered_bundle,
                    "statement": tampered_statement,
                },
                authoritative,
            )
        changed_certificate = {
            "der_base64": base64.b64encode(b"different certificate").decode("ascii")
        }
        with self.assertRaises(EvidenceError):
            attest_identity(
                {**bundle, "certificate": changed_certificate},
                statement,
                changed_certificate,
                rekor,
            )
        with self.assertRaises(EvidenceError):
            attest_identity(
                {key: value for key, value in bundle.items() if key != "media_type"},
                statement,
                certificate,
                rekor,
            )

    def test_newer_failed_run_is_not_a_fallback(self):
        runs = [
            {"workflow_id": 4, "path": WORKFLOW_PATH, "id": 10,
             "run_number": 10, "run_attempt": 1, "ref": "refs/heads/master",
             "status": "completed", "conclusion": "success"},
            {"workflow_id": 4, "path": WORKFLOW_PATH, "id": 11,
             "run_number": 11, "run_attempt": 1, "ref": "refs/heads/master",
             "status": "completed", "conclusion": "failure"},
        ]
        with self.assertRaises(EvidenceError):
            select_latest_producer_evidence(
                runs,
                {10: [{"id": 1, "name": "credential-audit-evidence.json",
                       "digest": "a" * 64}]},
                workflow_id=4,
                workflow_path=WORKFLOW_PATH,
            )

    def test_rerun_attempt_supersedes_and_wrong_path_is_excluded(self):
        runs = [
            {"workflow_id": 4, "path": WORKFLOW_PATH, "id": 10,
             "run_number": 10, "run_attempt": 1, "ref": "refs/heads/master",
             "status": "completed", "conclusion": "failure"},
            {"workflow_id": 4, "path": WORKFLOW_PATH, "id": 10,
             "run_number": 10, "run_attempt": 2, "ref": "refs/heads/master",
             "status": "completed", "conclusion": "success"},
            {"workflow_id": 4, "path": ".github/workflows/copied.yml", "id": 99,
             "run_number": 99, "run_attempt": 1, "ref": "refs/heads/master",
             "status": "completed", "conclusion": "success"},
        ]
        selected = select_latest_producer_evidence(
            runs,
            {10: [{"id": 8, "name": "credential-audit-evidence.json",
                   "digest": "c" * 64}]},
            workflow_id=4,
            workflow_path=WORKFLOW_PATH,
        )
        self.assertEqual(selected.run_attempt, 2)
        with self.assertRaises(EvidenceError):
            select_latest_producer_evidence(
                [runs[1], dict(runs[1])],
                {10: [{"id": 8, "name": "credential-audit-evidence.json",
                       "digest": "c" * 64}]},
                workflow_id=4,
                workflow_path=WORKFLOW_PATH,
            )

    def test_producer_selection_is_exhaustive_and_race_aware(self):
        runs = [
            {
                "workflow_id": 4, "path": WORKFLOW_PATH, "id": index,
                "run_number": index, "run_attempt": 1,
                "ref": "refs/heads/master", "status": "completed",
                "conclusion": "success",
            }
            for index in range(1, 121)
        ]
        selected = select_latest_producer_evidence(
            runs,
            {120: [{"id": 9, "name": "credential-audit-evidence.json",
                    "digest": "c" * 64}]},
            workflow_id=4,
            workflow_path=WORKFLOW_PATH,
        )
        self.assertEqual(selected.run_number, 120)
        with self.assertRaises(EvidenceError):
            stable_read({"run": 120}, {"run": 119}, "producer snapshot")


if __name__ == "__main__":
    unittest.main()
