#!/usr/bin/env python3
import hashlib
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from audit_cli import (
    GitHubApi,
    PRODUCER_REPOSITORY,
    PRODUCER_REF,
    PRODUCER_BRANCH,
    PRODUCER_WORKFLOW_ID,
    PRODUCER_WORKFLOW_PATH,
    _producer_evidence,
    _queue_entries,
    run,
)
from release_evidence import EvidenceError


PRODUCER_SHA = "a" * 40
PRODUCER_REPOSITORY_ID = 101
BOOTSTRAP_FIXTURE = (
    Path(__file__).parent / "fixtures" / "missing-verifier-pull-request-target.json"
)


def pr(*, classification="release", head_sha="h", base_sha="base"):
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


def producer():
    runs = [{
        "workflow_id": PRODUCER_WORKFLOW_ID,
        "path": f"{PRODUCER_WORKFLOW_PATH}@{PRODUCER_BRANCH}",
        "id": 10,
        "run_number": 10,
        "run_attempt": 1,
        "event": "push",
        "head_branch": PRODUCER_BRANCH,
        "head_sha": PRODUCER_SHA,
        "repository": {
            "id": PRODUCER_REPOSITORY_ID,
            "full_name": PRODUCER_REPOSITORY,
        },
        "head_repository": {
            "id": PRODUCER_REPOSITORY_ID,
            "full_name": PRODUCER_REPOSITORY,
        },
        "status": "completed",
        "conclusion": "success",
    }]
    artifacts = {
        "10": [{
            "id": 1,
            "name": "credential-audit-evidence.json",
            "digest": "a" * 64,
            "expired": False,
            "workflow_run": {
                "id": 10,
                "repository_id": PRODUCER_REPOSITORY_ID,
                "head_repository_id": PRODUCER_REPOSITORY_ID,
                "head_branch": PRODUCER_BRANCH,
                "head_sha": PRODUCER_SHA,
            },
        }]
    }
    return {
        "producer_workflow_id": PRODUCER_WORKFLOW_ID,
        "producer_workflow_path": PRODUCER_WORKFLOW_PATH,
        "producer_master_sha": PRODUCER_SHA,
        "producer_git_ref_first_before": PRODUCER_SHA,
        "producer_git_ref_first_after": PRODUCER_SHA,
        "producer_git_ref_second_before": PRODUCER_SHA,
        "producer_git_ref_second_after": PRODUCER_SHA,
        "producer_runs_first": runs,
        "producer_runs_second": json.loads(json.dumps(runs)),
        "producer_artifacts_first": artifacts,
        "producer_artifacts_second": json.loads(json.dumps(artifacts)),
    }


class AuditCliTest(unittest.TestCase):
    def test_live_producer_adapter_uses_direct_run_identity_and_bracketed_refs(self):
        run_record = {
            "workflow_id": PRODUCER_WORKFLOW_ID,
            "path": f"{PRODUCER_WORKFLOW_PATH}@{PRODUCER_BRANCH}",
            "id": 10,
            "run_number": 10,
            "run_attempt": 1,
            "event": "push",
            "head_branch": PRODUCER_BRANCH,
            "head_sha": PRODUCER_SHA,
            "repository": {
                "id": PRODUCER_REPOSITORY_ID,
                "full_name": PRODUCER_REPOSITORY,
            },
            "head_repository": {
                "id": PRODUCER_REPOSITORY_ID,
                "full_name": PRODUCER_REPOSITORY,
            },
            "status": "completed",
            "conclusion": "success",
            "ref": "refs/heads/not-master",
            "referenced_workflows": [{"path": "untrusted.yml@master"}],
        }
        artifact = {
            "id": 1,
            "name": "credential-audit-evidence.json",
            "digest": "a" * 64,
            "expired": False,
            "workflow_run": {
                "id": 10,
                "repository_id": PRODUCER_REPOSITORY_ID,
                "head_repository_id": PRODUCER_REPOSITORY_ID,
                "head_branch": PRODUCER_BRANCH,
                "head_sha": PRODUCER_SHA,
            },
        }
        payload = {
            "schema": 1,
            "repository": PRODUCER_REPOSITORY,
            "workflow_path": PRODUCER_WORKFLOW_PATH,
            "ref": PRODUCER_REF,
            "sha": PRODUCER_SHA,
            "run_id": 10,
            "run_number": 10,
            "run_attempt": 1,
            "status": "completed",
            "conclusion": "success",
        }

        class FakeApi(GitHubApi):
            def __init__(self):
                super().__init__(PRODUCER_REPOSITORY, "token")
                self.ref_reads = [PRODUCER_SHA] * 4

            def resolve_workflow_path(self, workflow_id):
                if workflow_id != PRODUCER_WORKFLOW_ID:
                    raise AssertionError("unexpected producer workflow ID")
                return PRODUCER_WORKFLOW_PATH

            def current_ref_sha(self, branch):
                if branch != PRODUCER_BRANCH:
                    raise AssertionError("unexpected producer branch")
                return self.ref_reads.pop(0)

            def all_pages(self, path, query=None, **kwargs):
                if path.endswith(f"/actions/workflows/{PRODUCER_WORKFLOW_ID}/runs"):
                    if query["branch"] != PRODUCER_BRANCH:
                        raise AssertionError("unexpected producer query branch")
                    return [run_record]
                if path != "/actions/runs/10/artifacts":
                    raise AssertionError("unexpected producer artifact path")
                return [artifact]

            def download_artifact_json(self, artifact_id, expected_name, expected_digest):
                if (
                    artifact_id != 1
                    or expected_name != "credential-audit-evidence.json"
                    or expected_digest != "a" * 64
                ):
                    raise AssertionError("unexpected producer artifact identity")
                return payload

        selected = _producer_evidence(
            FakeApi(),
            None,
            protected_branch=PRODUCER_BRANCH,
            protected_ref=PRODUCER_REF,
        )
        self.assertEqual(selected["head_sha"], PRODUCER_SHA)

    def test_live_producer_adapter_rejects_ref_move_inside_snapshot(self):
        class FakeApi(GitHubApi):
            def __init__(self):
                super().__init__(PRODUCER_REPOSITORY, "token")
                self.ref_reads = [PRODUCER_SHA, "b" * 40, "b" * 40, "b" * 40]

            def resolve_workflow_path(self, workflow_id):
                return PRODUCER_WORKFLOW_PATH

            def current_ref_sha(self, branch):
                return self.ref_reads.pop(0)

            def all_pages(self, path, query=None, **kwargs):
                return []

        with self.assertRaises(EvidenceError):
            _producer_evidence(
                FakeApi(),
                None,
                protected_branch=PRODUCER_BRANCH,
                protected_ref=PRODUCER_REF,
            )

    def test_fixture_producer_identity_fields_and_payloads_are_fail_closed(self):
        base = {
            "event": {"event_name": "pull_request", "pull_request": pr()},
            "live_pr_first": pr(),
            "live_pr_second": pr(),
            **producer(),
        }
        for field in ("producer_workflow_id", "producer_workflow_path"):
            fixture = dict(base)
            fixture.pop(field)
            with self.subTest(field=field), self.assertRaises(EvidenceError):
                self.fixture_run(fixture)

        for payloads in ({}, {"first": {}}, {"first": {}, "second": {}, "extra": {}}):
            fixture = dict(base)
            fixture["producer_artifact_payloads"] = payloads
            with self.subTest(payloads=payloads), self.assertRaises(EvidenceError):
                self.fixture_run(fixture)

    def fixture_run(self, value):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "fixture.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with patch.dict(
                os.environ,
                {
                    "GITHUB_EVENT_NAME": value["event"]["event_name"],
                    "GITHUB_REPOSITORY": "owner/repo",
                },
                clear=False,
            ):
                return run(str(path))

    def test_pull_request_fixture_uses_independent_exact_snapshots(self):
        fixture = {
            "event": {"event_name": "pull_request", "pull_request": pr()},
            "live_pr_first": pr(),
            "live_pr_second": pr(),
            **producer(),
        }
        self.assertEqual(self.fixture_run(fixture), "PASS")
        fixture["live_pr_second"] = pr(head_sha="moved")
        with self.assertRaises(EvidenceError):
            self.fixture_run(fixture)

    def test_non_release_still_requires_stable_live_tuple(self):
        fixture = {
            "event": {
                "event_name": "pull_request",
                "pull_request": pr(classification="non-release"),
            },
            "live_pr_first": pr(classification="non-release"),
            "live_pr_second": pr(classification="non-release"),
        }
        self.assertEqual(self.fixture_run(fixture), "N/A")
        fixture["live_pr_second"] = pr(
            classification="non-release", base_sha="moved"
        )
        with self.assertRaises(EvidenceError):
            self.fixture_run(fixture)

    def test_missing_verifier_pull_request_target_fixture_is_na(self):
        event = json.loads(BOOTSTRAP_FIXTURE.read_text(encoding="utf-8"))
        fixture = {
            "event": event,
            "live_pr_first": event["pull_request"],
            "live_pr_second": json.loads(json.dumps(event["pull_request"])),
        }
        self.assertEqual(event["event_name"], "pull_request_target")
        self.assertEqual(self.fixture_run(fixture), "N/A")

    def test_producer_fixture_race_fails_closed(self):
        fixture = {
            "event": {"event_name": "pull_request", "pull_request": pr()},
            "live_pr_first": pr(),
            "live_pr_second": pr(),
            **producer(),
        }
        fixture["producer_runs_second"][0]["conclusion"] = "failure"
        with self.assertRaises(EvidenceError):
            self.fixture_run(fixture)

    def test_merge_group_fixture_checks_exact_queue_tuple_and_conclusion(self):
        rule = {
            "target_branch": "dev",
            "enabled": True,
            "max_entries": 1,
            "min_entries": 1,
            "batch_size": 1,
            "grouping": "NONE",
            "entries_exhaustive": True,
            "required_checks": ["release-please-credential-audit"],
        }
        entry = {
            "id": "q1",
            "state": "QUEUED",
            "target_branch": "dev",
            "exhaustive": True,
            "pull_request_numbers": [7],
            "head_sha": "h",
            "base_ref": "dev",
            "base_sha": "base",
            "required_check_conclusions": {
                "release-please-credential-audit": "success"
            },
        }
        live_pr = {
            "number": 7,
            "state": "open",
            "draft": False,
            "head_sha": "h",
            "base_ref": "dev",
            "base_sha": "base",
        }
        event_group = {
            "head_sha": "group",
            "head_ref": "refs/heads/gh-readonly-queue/dev/pr-7",
            "base_sha": "base",
            "base_ref": "refs/heads/dev",
        }
        group = {
            **event_group,
            "queue_entry_id": "q1",
            "target_branch": "dev",
            "pr_tuple": [7, "h", "dev", "base"],
        }
        commit = {
            "sha": "group",
            "parents": [{"sha": "base"}, {"sha": "h"}],
        }
        fixture = {
            "event": {
                "event_name": "merge_group",
                "action": "checks_requested",
                "repository": {"full_name": "owner/repo"},
                "merge_group": event_group,
            },
            **producer(),
            "queue": {
                "rules_first": [rule],
                "rules_second": json.loads(json.dumps([rule])),
                "entries_first": [entry],
                "entries_second": json.loads(json.dumps([entry])),
                "group_first": group,
                "group_second": json.loads(json.dumps(group)),
                "live_prs_first": {"7": live_pr},
                "live_prs_second": {"7": dict(live_pr)},
                "commit_first": commit,
                "commit_second": json.loads(json.dumps(commit)),
            },
        }
        self.assertEqual(self.fixture_run(fixture), "PASS")
        fixture["queue"]["entries_second"][0]["head_sha"] = "moved"
        with self.assertRaises(EvidenceError):
            self.fixture_run(fixture)
        fixture["queue"]["entries_second"][0]["head_sha"] = "h"
        fixture["event"]["merge_group"]["head_sha"] = "unrelated"
        with self.assertRaises(EvidenceError):
            self.fixture_run(fixture)

    def test_pagination_enforces_max_entries(self):
        api = GitHubApi("owner/repo", "token")
        api.get = lambda path, query=None: [{}] * 100
        with self.assertRaises(EvidenceError):
            api.all_pages("/runs", max_entries=150)
        with self.assertRaises(EvidenceError):
            _queue_entries(
                {"entries_pages_first": [[{}] * 100, [{}] * 51]},
                "first",
                max_entries=150,
            )

    def test_downloaded_producer_zip_digest_is_checked_before_parsing(self):
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, "w") as value:
            value.writestr("credential-audit-evidence.json", '{"verified": true}')
        archive_bytes = archive.getvalue()
        digest = hashlib.sha256(archive_bytes).hexdigest()

        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *_):
                return False

            def read(self):
                return archive_bytes

        api = GitHubApi("owner/repo", "token")
        with patch("audit_cli.urllib.request.urlopen", return_value=Response()):
            self.assertEqual(
                api.download_artifact_json(
                    1,
                    "credential-audit-evidence.json",
                    digest,
                ),
                {"verified": True},
            )
        with patch("audit_cli.urllib.request.urlopen", return_value=Response()):
            with self.assertRaisesRegex(EvidenceError, "ZIP digest"):
                api.download_artifact_json(
                    1,
                    "credential-audit-evidence.json",
                    "0" * 64,
                )


if __name__ == "__main__":
    unittest.main()
