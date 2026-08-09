#!/usr/bin/env python3
"""GitHub credential-audit check.

This command is intentionally unconditional in the workflow. It has no
release credential and fails closed when GitHub cannot provide complete,
stable evidence. Local JSON fixtures use the same verifier as live API data.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from release_evidence import (
    EvidenceError,
    attest_identity,
    classify_pull_request,
    effective_singleton_queue,
    map_merge_group_to_pr,
    required_check_decision,
    select_latest_producer_evidence,
    stable_read,
    verify_producer_snapshot,
    verify_two_parent_merge_commit,
)


class GitHubApi:
    def __init__(self, repository: str, token: str) -> None:
        self.repository = repository
        self.token = token

    def get(self, path: str, query: dict[str, Any] | None = None) -> Any:
        params = urllib.parse.urlencode(query or {})
        url = f"https://api.github.com/repos/{self.repository}{path}"
        if params:
            url += f"?{params}"
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)

    def all_pages(self, path: str, query: dict[str, Any] | None = None) -> list[Any]:
        page = 1
        result: list[Any] = []
        while True:
            payload = self.get(path, {**(query or {}), "per_page": 100, "page": page})
            values = payload if isinstance(payload, list) else payload.get("workflow_runs", payload.get("artifacts", []))
            if not isinstance(values, list):
                raise EvidenceError(f"unexpected paginated response for {path}")
            result.extend(values)
            if len(values) < 100:
                return result
            page += 1


def _fixture(path: str) -> dict[str, Any]:
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise EvidenceError("audit fixture must be an object")
    return value


def _event() -> dict[str, Any]:
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if not event_path:
        raise EvidenceError("GITHUB_EVENT_PATH is required")
    return _fixture(event_path)


def _producer_evidence(api: GitHubApi, fixture: dict[str, Any] | None) -> dict[str, Any]:
    if fixture is not None:
        runs = fixture["producer_runs"]
        artifacts = {
            int(run_id): values
            for run_id, values in fixture["producer_artifacts"].items()
        }
        selected = select_latest_producer_evidence(
            runs,
            artifacts,
            workflow_id=int(fixture["producer_workflow_id"]),
            protected_ref="refs/heads/master",
            artifact_name=fixture.get("producer_artifact_name", "credential-audit-evidence.json"),
        )
        verify_producer_snapshot(fixture["producer_snapshot"], fixture["producer_snapshot"])
        return selected.__dict__

    workflow_id = os.environ.get("PRODUCER_WORKFLOW_ID")
    if not workflow_id:
        raise EvidenceError("PRODUCER_WORKFLOW_ID repository variable is required")
    runs_first = api.all_pages(
        f"/actions/workflows/{workflow_id}/runs",
        {"branch": "master"},
    )
    artifact_map_first = {
        int(run["id"]): api.all_pages(f"/actions/runs/{run['id']}/artifacts")
        for run in runs_first
        if run.get("ref") == "refs/heads/master"
    }
    selected = select_latest_producer_evidence(
        runs_first,
        artifact_map_first,
        workflow_id=int(workflow_id),
        protected_ref="refs/heads/master",
        artifact_name=os.environ.get(
            "PRODUCER_ARTIFACT_NAME", "credential-audit-evidence.json"
        ),
    )
    runs_second = api.all_pages(
        f"/actions/workflows/{workflow_id}/runs",
        {"branch": "master"},
    )
    artifact_map_second = {
        int(run["id"]): api.all_pages(f"/actions/runs/{run['id']}/artifacts")
        for run in runs_second
        if run.get("ref") == "refs/heads/master"
    }
    verify_producer_snapshot(
        {"runs": runs_first, "artifacts": artifact_map_first},
        {"runs": runs_second, "artifacts": artifact_map_second},
    )
    return selected.__dict__


def _queue_evidence(api: GitHubApi | None, fixture: dict[str, Any] | None) -> dict[str, Any]:
    if fixture is not None and "queue" in fixture:
        return fixture["queue"]
    path = os.environ.get("MERGE_QUEUE_EVIDENCE_PATH")
    if path:
        value = _fixture(path)
        return value["queue"]
    encoded = os.environ.get("MERGE_QUEUE_EVIDENCE_JSON")
    if encoded:
        value = json.loads(encoded)
        return value["queue"] if "queue" in value else value
    # There is no stable public API adapter for merge queue entries in every
    # GitHub Enterprise version. A trusted adapter must provide the complete
    # evidence object; silently deriving a batch from a commit's associated
    # PRs would violate the singleton mapping contract.
    raise EvidenceError("exhaustive merge queue evidence is unavailable")


def _live_pr(api: GitHubApi, number: int) -> dict[str, Any]:
    return api.get(f"/pulls/{number}")


def run(fixture_path: str | None) -> str:
    fixture = _fixture(fixture_path) if fixture_path else None
    event = fixture["event"] if fixture else _event()
    event_name = os.environ.get("GITHUB_EVENT_NAME", event.get("event_name", ""))
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GITHUB_TOKEN", "")
    api = GitHubApi(repository, token) if not fixture else None
    pr = event.get("pull_request") if event_name == "pull_request" else None

    if event_name == "pull_request" and pr is not None:
        if classify_pull_request(pr) == "non-release":
            return "N/A: explicit non-release classification"

    evidence = _producer_evidence(api, fixture)
    if event_name == "merge_group":
        queue = _queue_evidence(api, fixture)
        rule = effective_singleton_queue(
            queue["rules_first"], queue["entries_first"], "dev"
        )
        mapped = map_merge_group_to_pr(
            queue["group"],
            queue["entries_first"],
            {int(number): value for number, value in queue["live_prs_first"].items()},
        )
        verify_two_parent_merge_commit(
            queue["commit_first"],
            mapped["pr"]["head_sha"],
            mapped["pr"]["base_sha"],
        )
        stable_read(queue["rules_first"], queue["rules_second"], "effective queue rules")
        stable_read(queue["entries_first"], queue["entries_second"], "merge queue entries")
        stable_read(queue["live_prs_first"], queue["live_prs_second"], "live PR tuple")
        stable_read(queue["commit_first"], queue["commit_second"], "synthetic merge commit")
    return required_check_decision(event_name, pr, {"verified": bool(evidence)})


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture")
    args = parser.parse_args()
    try:
        decision = run(args.fixture)
        print(f"release-please-credential-audit: {decision}")
        return 0
    except (EvidenceError, urllib.error.URLError, OSError, KeyError, ValueError) as error:
        print(f"release-please-credential-audit: FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
