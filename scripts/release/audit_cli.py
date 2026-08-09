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
    classify_pull_request,
    effective_singleton_queue,
    map_merge_group_to_pr,
    pull_request_tuple,
    required_check_decision,
    select_latest_producer_evidence,
    stable_read,
    verify_merge_group_event,
    verify_pull_request_snapshot,
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

    def all_pages(
        self,
        path: str,
        query: dict[str, Any] | None = None,
        *,
        max_entries: int = 10_000,
    ) -> list[Any]:
        page = 1
        result: list[Any] = []
        while True:
            payload = self.get(path, {**(query or {}), "per_page": 100, "page": page})
            values = payload if isinstance(payload, list) else payload.get("workflow_runs", payload.get("artifacts", []))
            if not isinstance(values, list):
                raise EvidenceError(f"unexpected paginated response for {path}")
            if len(result) + len(values) > max_entries:
                raise EvidenceError(f"paginated response for {path} exceeds max_entries")
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
        runs_first = fixture["producer_runs_first"]
        artifacts_first = {
            int(run_id): values
            for run_id, values in fixture["producer_artifacts_first"].items()
        }
        runs_second = fixture["producer_runs_second"]
        artifacts_second = {
            int(run_id): values
            for run_id, values in fixture["producer_artifacts_second"].items()
        }
        first_snapshot = {"runs": runs_first, "artifacts": artifacts_first}
        second_snapshot = {"runs": runs_second, "artifacts": artifacts_second}
        if (
            runs_first is runs_second
            or fixture["producer_artifacts_first"] is fixture["producer_artifacts_second"]
        ):
            raise EvidenceError("producer snapshots are not independent")
        verify_producer_snapshot(first_snapshot, second_snapshot)
        selected = select_latest_producer_evidence(
            runs_first,
            artifacts_first,
            workflow_id=int(fixture["producer_workflow_id"]),
            workflow_path=fixture["producer_workflow_path"],
            protected_ref="refs/heads/master",
            artifact_name=fixture.get("producer_artifact_name", "credential-audit-evidence.json"),
        )
        return selected.__dict__

    workflow_id = os.environ.get("PRODUCER_WORKFLOW_ID")
    if not workflow_id:
        raise EvidenceError("PRODUCER_WORKFLOW_ID repository variable is required")
    workflow_path = os.environ.get("PRODUCER_WORKFLOW_PATH")
    if not workflow_path:
        raise EvidenceError("PRODUCER_WORKFLOW_PATH repository variable is required")
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
        workflow_path=workflow_path,
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
    raise EvidenceError(
        "live exhaustive merge queue adapter is unavailable; refusing environment evidence"
    )


def _queue_entries(
    queue: dict[str, Any],
    snapshot: str,
    *,
    max_entries: int = 10_000,
) -> list[dict[str, Any]]:
    pages_key = f"entries_pages_{snapshot}"
    entries_key = f"entries_{snapshot}"
    if pages_key not in queue:
        entries = queue[entries_key]
        if not isinstance(entries, list):
            raise EvidenceError(f"merge queue {snapshot} entries are not a list")
        if len(entries) > max_entries:
            raise EvidenceError("merge queue entries exceed max_entries")
        return entries
    pages = queue[pages_key]
    if not isinstance(pages, list) or not pages:
        raise EvidenceError(f"merge queue {snapshot} pages are missing")
    entries: list[dict[str, Any]] = []
    for page in pages:
        if not isinstance(page, list):
            raise EvidenceError(f"merge queue {snapshot} page is not a list")
        if len(entries) + len(page) > max_entries:
            raise EvidenceError("merge queue entries exceed max_entries")
        entries.extend(page)
    return entries


def _live_pr(api: GitHubApi, number: int) -> dict[str, Any]:
    return api.get(f"/pulls/{number}")


def _fixture_pr_reads(
    fixture: dict[str, Any],
    *,
    prefix: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    first = fixture[f"{prefix}_first"]
    second = fixture[f"{prefix}_second"]
    if first is second:
        raise EvidenceError(f"{prefix} snapshots are not independent")
    return first, second


def run(fixture_path: str | None) -> str:
    fixture = _fixture(fixture_path) if fixture_path else None
    event = fixture["event"] if fixture else _event()
    event_name = os.environ.get("GITHUB_EVENT_NAME", event.get("event_name", ""))
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GITHUB_TOKEN", "")
    api = GitHubApi(repository, token) if not fixture else None
    pr = event.get("pull_request") if event_name == "pull_request" else None
    live_pr_first: dict[str, Any] | None = None
    live_pr_second: dict[str, Any] | None = None
    if event_name == "pull_request":
        if not isinstance(pr, dict):
            raise EvidenceError("pull_request event is missing PR data")
        number = int(pr["number"])
        if fixture:
            live_pr_first, live_pr_second = _fixture_pr_reads(
                fixture, prefix="live_pr"
            )
        else:
            live_pr_first = _live_pr(api, number)
        classification = classify_pull_request(live_pr_first)
        if classification != "non-release":
            evidence = _producer_evidence(api, fixture)
        else:
            evidence = {}
        if fixture is None:
            live_pr_second = _live_pr(api, number)
        verified_pr = verify_pull_request_snapshot(
            pr,
            live_pr_first,
            live_pr_second,
            repository=repository or pull_request_tuple(live_pr_first)[3],
            target_branch="dev",
        )
        if classification == "non-release":
            return required_check_decision(event_name, verified_pr, evidence)
        pr = verified_pr
    else:
        evidence = _producer_evidence(api, fixture)
    if event_name == "merge_group":
        queue = _queue_evidence(api, fixture)
        entries_first = _queue_entries(queue, "first")
        entries_second = _queue_entries(queue, "second")
        rule = effective_singleton_queue(
            queue["rules_first"], entries_first, "dev"
        )
        group_first = queue["group_first"]
        group_second = queue["group_second"]
        mapped = map_merge_group_to_pr(
            group_first,
            entries_first,
            {int(number): value for number, value in queue["live_prs_first"].items()},
            required_checks=rule["required_checks"],
        )
        event_group = verify_merge_group_event(
            event,
            group_first,
            queue["commit_first"],
            repository=repository,
        )
        if mapped["pr"]["base_sha"] != event_group["base_sha"]:
            raise EvidenceError("live PR base SHA disagrees with merge_group event")
        verify_two_parent_merge_commit(
            queue["commit_first"],
            event_group["base_sha"],
            mapped["pr"]["head_sha"],
        )
        stable_read(queue["rules_first"], queue["rules_second"], "effective queue rules")
        stable_read(entries_first, entries_second, "merge queue entries")
        stable_read(group_first, group_second, "merge group")
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
