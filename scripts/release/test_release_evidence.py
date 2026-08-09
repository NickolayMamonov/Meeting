#!/usr/bin/env python3
import unittest

from release_evidence import (
    EvidenceError,
    attest_identity,
    effective_singleton_queue,
    map_merge_group_to_pr,
    required_check_decision,
    select_latest_producer_evidence,
    stable_read,
    verify_two_parent_merge_commit,
)


class EvidenceTest(unittest.TestCase):
    def test_only_explicit_non_release_is_na(self):
        self.assertEqual(
            required_check_decision(
                "pull_request",
                {"labels": [{"name": "non-release"}], "body": "", "head": {"ref": "feature"}},
                {},
            ),
            "N/A",
        )
        with self.assertRaises(EvidenceError):
            required_check_decision("pull_request", {"labels": [], "body": ""}, {})
        self.assertEqual(
            required_check_decision(
                "pull_request",
                {
                    "labels": [{"name": "autorelease: pending"}],
                    "body": "",
                    "head": {"ref": "release-please--branches--dev"},
                },
                {"verified": True},
            ),
            "PASS",
        )

    def test_merge_queue_maps_one_live_pr(self):
        rule = effective_singleton_queue(
            [{"target_branch": "dev", "enabled": True, "max_entries": 1,
              "min_entries": 1, "batch_size": 1, "grouping": "NONE",
              "entries_exhaustive": True,
              "required_checks": ["release-please-credential-audit"]}],
            [{"id": "q1", "target_branch": "dev", "exhaustive": True,
              "pull_request_numbers": [7]}],
        )
        self.assertEqual(rule["batch_size"], 1)
        mapped = map_merge_group_to_pr(
            {"queue_entry_id": "q1", "pr_tuple": (7, "h", "dev", "base")},
            [{"id": "q1", "target_branch": "dev", "exhaustive": True,
             "pull_request_numbers": [7]}],
            {7: {"number": 7, "state": "open", "draft": False, "head_sha": "h",
                 "base_ref": "dev", "base_sha": "base"}},
        )
        self.assertEqual(mapped["tuple"], (7, "h", "dev", "base"))

    def test_exactly_two_parents(self):
        verify_two_parent_merge_commit(
            {"parents": [{"sha": "h"}, {"sha": "base"}]}, "h", "base"
        )
        with self.assertRaises(EvidenceError):
            verify_two_parent_merge_commit({"parents": [{"sha": "h"}]}, "h", "base")

    def test_newer_failed_run_is_not_a_fallback(self):
        runs = [
            {"workflow_id": 4, "id": 10, "run_number": 10, "run_attempt": 1,
             "ref": "refs/heads/master", "status": "completed", "conclusion": "success"},
            {"workflow_id": 4, "id": 11, "run_number": 11, "run_attempt": 1,
             "ref": "refs/heads/master", "status": "completed", "conclusion": "failure"},
        ]
        with self.assertRaises(EvidenceError):
            select_latest_producer_evidence(
                runs, {10: [{"id": 1, "name": "credential-audit-evidence.json",
                             "digest": "a" * 64}]}, workflow_id=4
            )

    def test_producer_selection_is_exhaustive_and_over_100_runs(self):
        runs = [
            {
                "workflow_id": 4,
                "id": index,
                "run_number": index,
                "run_attempt": 1,
                "ref": "refs/heads/master",
                "status": "completed",
                "conclusion": "success",
            }
            for index in range(1, 121)
        ]
        selected = select_latest_producer_evidence(
            runs,
            {
                120: [
                    {
                        "id": 9,
                        "name": "credential-audit-evidence.json",
                        "digest": "c" * 64,
                    }
                ]
            },
            workflow_id=4,
        )
        self.assertEqual(selected.run_number, 120)
        with self.assertRaises(EvidenceError):
            stable_read({"run": 120}, {"run": 119}, "producer snapshot")

    def test_queue_drift_and_reorder_fail(self):
        with self.assertRaises(EvidenceError):
            effective_singleton_queue(
                [{"target_branch": "dev", "enabled": True, "max_entries": 1,
                  "min_entries": 1, "batch_size": 2, "grouping": "NONE",
                  "entries_exhaustive": True, "required_checks": ["audit"]}],
                [],
            )
        with self.assertRaises(EvidenceError):
            stable_read(["q1", "q2"], ["q2", "q1"], "queue entries")

    def test_canonical_attestation_identity(self):
        linked = attest_identity(
            {"digest": "a" * 64},
            {"bundle_digest": "a" * 64, "certificate_identity": "cert",
             "rekor_entry_uuid": "rekor"},
            {"identity": "cert"},
            {"entry_uuid": "rekor"},
        )
        self.assertEqual(linked["bundle_digest"], "a" * 64)
        with self.assertRaises(EvidenceError):
            attest_identity(
                {"digest": "a" * 64, "list_id": "wrong"},
                {"bundle_digest": "a" * 64, "certificate_identity": "cert",
                 "rekor_entry_uuid": "rekor"},
                {"identity": "cert"},
                {"entry_uuid": "rekor"},
            )


if __name__ == "__main__":
    unittest.main()
