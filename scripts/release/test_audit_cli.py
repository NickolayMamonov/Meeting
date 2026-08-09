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

from audit_cli import GitHubApi, _queue_entries, run
from release_evidence import EvidenceError


WORKFLOW_PATH = ".github/workflows/release-please-credential-audit.yml"


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
        "workflow_id": 4,
        "path": WORKFLOW_PATH,
        "id": 10,
        "run_number": 10,
        "run_attempt": 1,
        "ref": "refs/heads/master",
        "status": "completed",
        "conclusion": "success",
    }]
    artifacts = {
        "10": [{
            "id": 1,
            "name": "credential-audit-evidence.json",
            "digest": "a" * 64,
        }]
    }
    return {
        "producer_workflow_id": 4,
        "producer_workflow_path": WORKFLOW_PATH,
        "producer_runs_first": runs,
        "producer_runs_second": json.loads(json.dumps(runs)),
        "producer_artifacts_first": artifacts,
        "producer_artifacts_second": json.loads(json.dumps(artifacts)),
    }


class AuditCliTest(unittest.TestCase):
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
