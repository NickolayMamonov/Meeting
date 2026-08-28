#!/usr/bin/env python3
"""GitHub credential-audit check.

This command is intentionally unconditional in the workflow. It has no
release credential and fails closed when GitHub cannot provide complete,
stable evidence. Local JSON fixtures use the same verifier as live API data.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
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
    verify_producer_artifact,
    verify_two_parent_merge_commit,
)
from release_roles import ReleaseRole, load_release_roles


PRODUCER_REPOSITORY = "NickolayMamonov/Meeting"
PRODUCER_WORKFLOW_ID = 330672623
PRODUCER_WORKFLOW_PATH = ".github/workflows/release-credential-audit.yml"
PRODUCER_ARTIFACT_NAME = "credential-audit-evidence.json"
PRODUCER_BRANCH = "master"
PRODUCER_REF = "refs/heads/master"
_GIT_SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")


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

    def graphql(self, query: str, variables: dict[str, Any]) -> dict[str, Any]:
        request = urllib.request.Request(
            "https://api.github.com/graphql",
            data=json.dumps({"query": query, "variables": variables}).encode("utf-8"),
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
            raise EvidenceError("GitHub GraphQL merge queue request failed") from error
        if payload.get("errors"):
            raise EvidenceError("GitHub GraphQL merge queue response contained errors")
        data = payload.get("data")
        if not isinstance(data, dict):
            raise EvidenceError("GitHub GraphQL merge queue response is missing data")
        return data

    def pull_request_for_queue_entry(self, number: int) -> dict[str, Any]:
        value = self.get(f"/pulls/{number}")
        return {
            "number": int(value["number"]),
            "state": value.get("state"),
            "draft": bool(value.get("draft", False)),
            "head_sha": value.get("head", {}).get("sha"),
            "base_ref": value.get("base", {}).get("ref"),
            "base_sha": value.get("base", {}).get("sha"),
        }

    def current_ref_sha(self, branch: str) -> str:
        """Read one exact branch ref from the repository Git database."""

        value = self.get(
            f"/git/ref/heads/{urllib.parse.quote(branch, safe='')}"
        )
        if not isinstance(value, dict) or value.get("ref") != f"refs/heads/{branch}":
            raise EvidenceError("GitHub branch ref response is not exact")
        target = value.get("object")
        sha = target.get("sha") if isinstance(target, dict) else None
        if not isinstance(sha, str) or _GIT_SHA_PATTERN.fullmatch(sha) is None:
            raise EvidenceError("GitHub branch ref SHA is not lowercase")
        return sha

    def resolve_workflow_path(self, workflow_id: int) -> str:
        """Resolve and verify the repository-owned workflow's bare path."""

        value = self.get(f"/actions/workflows/{workflow_id}")
        if not isinstance(value, dict) or value.get("id") != workflow_id:
            raise EvidenceError("producer workflow identity is not exact")
        path = value.get("path")
        if path != PRODUCER_WORKFLOW_PATH:
            raise EvidenceError("producer workflow path is not repository-controlled")
        return path

    def _merge_queue_snapshot_legacy(
        self,
        *,
        branch: str,
        event_group: dict[str, Any],
        required_checks: list[str],
    ) -> dict[str, Any]:
        """Read the complete queue and bind its singleton entry to the event."""

        query = """
        query($owner: String!, $name: String!, $branch: String!, $after: String) {
          repository(owner: $owner, name: $name) {
            mergeQueue(branch: $branch) {
              configuration {
                maximumEntriesToBuild
                maximumEntriesToMerge
                minimumEntriesToMerge
                mergingStrategy
              }
              entries(first: 100, after: $after) {
                nodes {
                  id
                  state
                  solo
                  pullRequest {
                    number
                    headRefOid
                    baseRefOid
                    baseRefName
                    state
                    isDraft
                  }
                  headCommit { oid }
                  baseCommit { oid }
                }
                pageInfo { hasNextPage endCursor }
                totalCount
              }
            }
          }
        }
        """
        owner, name = self.repository.split("/", 1)
        after: str | None = None
        nodes: list[dict[str, Any]] = []
        configuration: dict[str, Any] | None = None
        total_count = -1
        exhaustive = False
        while True:
            data = self.graphql(
                query,
                {"owner": owner, "name": name, "branch": branch, "after": after},
            )
            queue = ((data.get("repository") or {}).get("mergeQueue"))
            if not isinstance(queue, dict):
                raise EvidenceError("merge queue is not configured")
            configuration = queue.get("configuration")
            entries = queue.get("entries")
            if not isinstance(entries, dict):
                raise EvidenceError("merge queue entries are missing")
            page_nodes = entries.get("nodes", [])
            if not isinstance(page_nodes, list):
                raise EvidenceError("merge queue nodes are malformed")
            nodes.extend(node for node in page_nodes if isinstance(node, dict))
            total_count = int(entries.get("totalCount", -1))
            page_info = entries.get("pageInfo")
            if not isinstance(page_info, dict):
                raise EvidenceError("merge queue pagination metadata is missing")
            if not page_info.get("hasNextPage"):
                exhaustive = page_info.get("hasNextPage") is False
                break
            after = page_info.get("endCursor")
            if not isinstance(after, str) or not after:
                raise EvidenceError("merge queue pagination cursor is missing")
            if len(nodes) > 10_000:
                raise EvidenceError("merge queue exceeds the evidence bound")
        if (
            not isinstance(configuration, dict)
            or not exhaustive
            or total_count != len(nodes)
        ):
            raise EvidenceError("merge queue enumeration is not exhaustive")
        if len(nodes) != 1:
            raise EvidenceError("effective merge queue is not singleton")
        raw_entry = nodes[0]
        pull = raw_entry.get("pullRequest")
        if not isinstance(pull, dict):
            raise EvidenceError("merge queue entry has no pull request")
        number = int(pull["number"])
        head_sha = str((raw_entry.get("headCommit") or {}).get("oid") or pull.get("headRefOid"))
        base_sha = str((raw_entry.get("baseCommit") or {}).get("oid") or pull.get("baseRefOid"))
        if not head_sha or not base_sha:
            raise EvidenceError("merge queue entry commit identity is incomplete")
        conclusions = self.required_check_conclusions(head_sha, required_checks)
        entry = {
            "id": raw_entry.get("id"),
            "state": raw_entry.get("state"),
            "target_branch": branch,
            "exhaustive": True,
            "pull_request_numbers": [number],
            "head_sha": head_sha,
            "base_ref": pull.get("baseRefName"),
            "base_sha": base_sha,
            "required_check_conclusions": conclusions,
        }
        max_build = int(configuration.get("maximumEntriesToBuild", -1))
        max_merge = int(configuration.get("maximumEntriesToMerge", -1))
        min_merge = int(configuration.get("minimumEntriesToMerge", -1))
        rule = {
            "target_branch": branch,
            "enabled": True,
            "max_entries": max_merge,
            "min_entries": min_merge,
            "batch_size": max_build,
            "grouping": "NONE" if raw_entry.get("solo") and len(nodes) == 1 else "BATCH",
            "entries_exhaustive": True,
            "required_checks": required_checks,
        }
        event_expected = {
            "head_sha": str(event_group.get("head_sha", "")),
            "head_ref": str(event_group.get("head_ref", "")),
            "base_sha": str(event_group.get("base_sha", "")),
            "base_ref": str(event_group.get("base_ref", "")),
        }
        group = {
            **event_expected,
            "queue_entry_id": entry["id"],
            "target_branch": branch,
            "pr_tuple": (number, head_sha, branch, base_sha),
        }
        return {
            "rule": rule,
            "entry": entry,
            "group": group,
            "live_pr": self.pull_request_for_queue_entry(number),
            "commit": self.get(f"/commits/{event_expected['head_sha']}"),
        }

    def required_check_conclusions(self, head_sha: str, required: list[str]) -> dict[str, str]:
        payload = self.get(f"/commits/{head_sha}/check-runs", {"per_page": 100})
        runs = payload.get("check_runs", []) if isinstance(payload, dict) else []
        found = {
            str(run.get("name")): str(run.get("conclusion") or run.get("status"))
            for run in runs
            if isinstance(run, dict)
        }
        if set(required) - set(found):
            raise EvidenceError("merge queue required check conclusion is missing")
        return {name: found[name] for name in required}

    def merge_queue_snapshot(
        self,
        branch: str,
        event_group: dict[str, Any],
        required_checks: list[str],
    ) -> dict[str, Any]:
        query = """
        query($owner: String!, $name: String!, $branch: String!, $after: String) {
          repository(owner: $owner, name: $name) {
            mergeQueue(branch: $branch) {
              configuration {
                maximumEntriesToBuild
                maximumEntriesToMerge
                minimumEntriesToMerge
                mergingStrategy
              }
              entries(first: 100, after: $after) {
                nodes {
                  id
                  position
                  state
                  solo
                  pullRequest {
                    number
                    headRefOid
                    baseRefOid
                    baseRefName
                    state
                    isDraft
                  }
                  headCommit { oid }
                  baseCommit { oid }
                }
                pageInfo { hasNextPage endCursor }
                totalCount
              }
            }
          }
        }
        """
        owner, name = self.repository.split("/", 1)
        after: str | None = None
        nodes: list[dict[str, Any]] = []
        configuration: dict[str, Any] | None = None
        total_count: int | None = None
        while True:
            data = self.graphql(
                query,
                {"owner": owner, "name": name, "branch": branch, "after": after},
            )
            queue = ((data.get("repository") or {}).get("mergeQueue"))
            if not isinstance(queue, dict):
                raise EvidenceError("merge queue is not configured")
            configuration = queue.get("configuration")
            connection = queue.get("entries")
            if not isinstance(connection, dict):
                raise EvidenceError("merge queue entries are missing")
            page_nodes = connection.get("nodes", [])
            if not isinstance(page_nodes, list):
                raise EvidenceError("merge queue nodes are malformed")
            nodes.extend(node for node in page_nodes if isinstance(node, dict))
            total_count = int(connection.get("totalCount", -1))
            page_info = connection.get("pageInfo", {})
            if not isinstance(page_info, dict) or not page_info.get("hasNextPage"):
                exhaustive = isinstance(page_info, dict) and page_info.get("hasNextPage") is False
                break
            after = page_info.get("endCursor")
            if not isinstance(after, str) or not after:
                raise EvidenceError("merge queue pagination cursor is missing")
            if len(nodes) > 10_000:
                raise EvidenceError("merge queue exceeds the evidence bound")
        if not isinstance(configuration, dict) or total_count != len(nodes) or not exhaustive:
            raise EvidenceError("merge queue enumeration is not exhaustive")
        if len(nodes) != 1:
            raise EvidenceError("effective merge queue does not contain exactly one entry")
        config = configuration
        entry_raw = nodes[0]
        pull = entry_raw.get("pullRequest")
        if not isinstance(pull, dict):
            raise EvidenceError("merge queue entry has no pull request")
        number = int(pull["number"])
        head_sha = str((entry_raw.get("headCommit") or {}).get("oid") or pull.get("headRefOid"))
        base_sha = str((entry_raw.get("baseCommit") or {}).get("oid") or pull.get("baseRefOid"))
        conclusions = self.required_check_conclusions(head_sha, required_checks)
        entry = {
            "id": entry_raw.get("id"),
            "state": entry_raw.get("state"),
            "target_branch": branch,
            "exhaustive": True,
            "pull_request_numbers": [number],
            "head_sha": head_sha,
            "base_ref": pull.get("baseRefName"),
            "base_sha": base_sha,
            "required_check_conclusions": conclusions,
        }
        rule = {
            "target_branch": branch,
            "enabled": True,
            "max_entries": int(config.get("maximumEntriesToMerge", -1)),
            "min_entries": int(config.get("minimumEntriesToMerge", -1)),
            "batch_size": int(config.get("maximumEntriesToBuild", -1)),
            "grouping": (
                "NONE"
                if int(config.get("maximumEntriesToBuild", -1)) == 1
                and int(config.get("maximumEntriesToMerge", -1)) == 1
                and int(config.get("minimumEntriesToMerge", -1)) == 1
                else "BATCH"
            ),
            "entries_exhaustive": exhaustive,
            "required_checks": required_checks,
        }
        event_expected = {
            "head_sha": str(event_group.get("head_sha", "")),
            "head_ref": str(event_group.get("head_ref", "")),
            "base_sha": str(event_group.get("base_sha", "")),
            "base_ref": str(event_group.get("base_ref", "")),
        }
        group = {
            **event_expected,
            "queue_entry_id": entry["id"],
            "target_branch": branch,
            "pr_tuple": (number, head_sha, branch, base_sha),
        }
        return {
            "rule": rule,
            "entry": entry,
            "group": group,
            "live_pr": self.pull_request_for_queue_entry(number),
            "commit": self.get(f"/commits/{event_expected['head_sha']}"),
        }

    def download_artifact_json(
        self,
        artifact_id: int,
        expected_name: str,
        expected_digest: str,
    ) -> dict[str, Any]:
        url = f"https://api.github.com/repos/{self.repository}/actions/artifacts/{artifact_id}/zip"
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                archive_bytes = response.read()
            actual_digest = hashlib.sha256(archive_bytes).hexdigest()
            if actual_digest != expected_digest.removeprefix("sha256:").lower():
                raise EvidenceError("protected producer artifact ZIP digest mismatch")
            archive = io.BytesIO(archive_bytes)
            with zipfile.ZipFile(archive) as value:
                names = value.namelist()
                if names != [expected_name]:
                    raise EvidenceError("producer artifact contains unexpected files")
                payload = json.loads(value.read(expected_name).decode("utf-8"))
        except (OSError, urllib.error.URLError, zipfile.BadZipFile, json.JSONDecodeError) as error:
            raise EvidenceError("protected producer artifact could not be downloaded") from error
        if not isinstance(payload, dict):
            raise EvidenceError("protected producer artifact is not an object")
        return payload

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


def _producer_snapshot(
    api: GitHubApi,
    *,
    protected_branch: str,
    workflow_id: int,
) -> dict[str, Any]:
    """Enumerate producer runs and artifacts while pinning the protected ref."""

    git_ref_before = api.current_ref_sha(protected_branch)
    runs = api.all_pages(
        f"/actions/workflows/{workflow_id}/runs",
        {"branch": protected_branch},
    )
    artifacts_by_run: dict[int, list[Any]] = {}
    for run in runs:
        run_id = run.get("id") if isinstance(run, dict) else None
        if isinstance(run_id, bool) or not isinstance(run_id, int) or run_id <= 0:
            raise EvidenceError("producer run ID is missing or invalid")
        artifacts_by_run[run_id] = api.all_pages(f"/actions/runs/{run_id}/artifacts")
    git_ref_after = api.current_ref_sha(protected_branch)
    return {
        "runs": runs,
        "artifacts": artifacts_by_run,
        "git_ref_before": git_ref_before,
        "git_ref_after": git_ref_after,
    }


def _producer_evidence(
    api: GitHubApi | None,
    fixture: dict[str, Any] | None,
    *,
    protected_branch: str,
    protected_ref: str,
) -> dict[str, Any]:
    producer_path = PRODUCER_WORKFLOW_PATH
    if fixture is not None:
        if (
            fixture.get("producer_workflow_id") != PRODUCER_WORKFLOW_ID
            or "producer_workflow_id" not in fixture
        ):
            raise EvidenceError("fixture producer workflow ID is not source-controlled")
        if (
            fixture.get("producer_workflow_path") != producer_path
            or "producer_workflow_path" not in fixture
        ):
            raise EvidenceError("fixture producer workflow path is not source-controlled")
        master_sha = fixture["producer_master_sha"]
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
        first_snapshot = {
            "runs": runs_first,
            "artifacts": artifacts_first,
            "git_ref_before": fixture["producer_git_ref_first_before"],
            "git_ref_after": fixture["producer_git_ref_first_after"],
        }
        second_snapshot = {
            "runs": runs_second,
            "artifacts": artifacts_second,
            "git_ref_before": fixture["producer_git_ref_second_before"],
            "git_ref_after": fixture["producer_git_ref_second_after"],
        }
        if (
            runs_first is runs_second
            or fixture["producer_artifacts_first"] is fixture["producer_artifacts_second"]
        ):
            raise EvidenceError("producer snapshots are not independent")
        verify_producer_snapshot(
            first_snapshot,
            second_snapshot,
            master_sha=master_sha,
        )
        selected = select_latest_producer_evidence(
            runs_first,
            artifacts_first,
            workflow_id=PRODUCER_WORKFLOW_ID,
            workflow_path=producer_path,
            repository=PRODUCER_REPOSITORY,
            protected_branch=protected_branch,
            protected_ref=protected_ref,
            head_sha=master_sha,
            artifact_name=PRODUCER_ARTIFACT_NAME,
        )
        if "producer_artifact_payloads" in fixture:
            payloads = fixture["producer_artifact_payloads"]
            if (
                not isinstance(payloads, dict)
                or set(payloads) != {"first", "second"}
            ):
                raise EvidenceError("fixture producer artifact payloads are malformed")
            for payload in (payloads["first"], payloads["second"]):
                verify_producer_artifact(
                    payload,
                    selected,
                    repository=PRODUCER_REPOSITORY,
                    workflow_path=producer_path,
                    protected_branch=protected_branch,
                    protected_ref=protected_ref,
                )
        return selected.__dict__

    if api is None:
        raise EvidenceError("producer API adapter is required")
    if api.repository != PRODUCER_REPOSITORY:
        raise EvidenceError("producer API adapter repository is not source-controlled")
    workflow_path = api.resolve_workflow_path(PRODUCER_WORKFLOW_ID)
    first_snapshot = _producer_snapshot(
        api,
        protected_branch=protected_branch,
        workflow_id=PRODUCER_WORKFLOW_ID,
    )
    second_snapshot = _producer_snapshot(
        api,
        protected_branch=protected_branch,
        workflow_id=PRODUCER_WORKFLOW_ID,
    )
    master_sha = first_snapshot["git_ref_before"]
    verify_producer_snapshot(
        first_snapshot,
        second_snapshot,
        master_sha=master_sha,
    )
    selected = select_latest_producer_evidence(
        first_snapshot["runs"],
        first_snapshot["artifacts"],
        workflow_id=PRODUCER_WORKFLOW_ID,
        workflow_path=workflow_path,
        repository=PRODUCER_REPOSITORY,
        protected_branch=protected_branch,
        protected_ref=protected_ref,
        head_sha=master_sha,
        artifact_name=PRODUCER_ARTIFACT_NAME,
    )
    payload = api.download_artifact_json(
        selected.artifact_id,
        selected.artifact_name,
        selected.artifact_digest,
    )
    verify_producer_artifact(
        payload,
        selected,
        repository=PRODUCER_REPOSITORY,
        workflow_path=workflow_path,
        protected_branch=protected_branch,
        protected_ref=protected_ref,
    )
    return selected.__dict__


def _queue_evidence(
    api: GitHubApi | None,
    fixture: dict[str, Any] | None,
    event: dict[str, Any] | None = None,
    *,
    target_branch: str,
) -> dict[str, Any]:
    if fixture is not None and "queue" in fixture:
        return fixture["queue"]
    if api is None or event is None:
        raise EvidenceError("live merge queue API adapter requires an event and API client")
    raw = os.environ.get("MERGE_QUEUE_EVIDENCE_JSON")
    if not raw:
        raise EvidenceError("MERGE_QUEUE_EVIDENCE_JSON is required for live queue evidence")
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as error:
        raise EvidenceError("MERGE_QUEUE_EVIDENCE_JSON is not valid JSON") from error
    if not isinstance(value, dict):
        raise EvidenceError("MERGE_QUEUE_EVIDENCE_JSON must contain an object")
    if value.get("source") != "github-api":
        raise EvidenceError("merge queue evidence must declare github-api as its source")
    branch = value.get("branch")
    required_checks = value.get("required_checks", ["release-please-credential-audit"])
    if not isinstance(branch, str) or branch != target_branch:
        raise EvidenceError("merge queue adapter target branch is not the integration role")
    if (
        not isinstance(required_checks, list)
        or not required_checks
        or len(required_checks) != len(set(required_checks))
    ):
        raise EvidenceError("merge queue adapter required checks are invalid")
    group = event.get("merge_group")
    if not isinstance(group, dict):
        raise EvidenceError("merge_group event payload is missing")
    first = api.merge_queue_snapshot(
        branch=branch,
        event_group=group,
        required_checks=[str(check) for check in required_checks],
    )
    second = api.merge_queue_snapshot(
        branch=branch,
        event_group=group,
        required_checks=[str(check) for check in required_checks],
    )
    return {
        "rules_first": [first["rule"]],
        "rules_second": [second["rule"]],
        "entries_first": [first["entry"]],
        "entries_second": [second["entry"]],
        "group_first": first["group"],
        "group_second": second["group"],
        "live_prs_first": {str(first["live_pr"]["number"]): first["live_pr"]},
        "live_prs_second": {str(second["live_pr"]["number"]): second["live_pr"]},
        "commit_first": first["commit"],
        "commit_second": second["commit"],
    }


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
    roles = load_release_roles()
    integration = roles.authority(ReleaseRole.INTEGRATION)
    stable = roles.authority(ReleaseRole.STABLE)
    if stable.branch != PRODUCER_BRANCH or stable.head_ref != PRODUCER_REF:
        raise EvidenceError("stable release role is not the source-controlled producer branch")
    fixture = _fixture(fixture_path) if fixture_path else None
    event = fixture["event"] if fixture else _event()
    event_name = os.environ.get("GITHUB_EVENT_NAME", event.get("event_name", ""))
    if event_name == "pull_request_target":
        event_name = "pull_request"
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GITHUB_TOKEN", "")
    api = GitHubApi(repository, token) if not fixture else None
    producer_api = GitHubApi(PRODUCER_REPOSITORY, token) if not fixture else None
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
        if fixture is None:
            live_pr_second = _live_pr(api, number)
        verified_pr = verify_pull_request_snapshot(
            pr,
            live_pr_first,
            live_pr_second,
            repository=repository
            or pull_request_tuple(
                live_pr_first,
                integration_branch=integration.branch,
            )[3],
            target_branch=integration.branch,
        )
        classification = classify_pull_request(
            verified_pr,
            integration_branch=integration.branch,
        )
        if classification != "non-release":
            evidence = _producer_evidence(
                producer_api,
                fixture,
                protected_branch=stable.branch,
                protected_ref=stable.head_ref,
            )
        else:
            evidence = {}
        if classification == "non-release":
            return required_check_decision(
                event_name,
                verified_pr,
                evidence,
                integration_branch=integration.branch,
            )
        pr = verified_pr
    else:
        evidence = _producer_evidence(
            producer_api,
            fixture,
            protected_branch=stable.branch,
            protected_ref=stable.head_ref,
        )
    if event_name == "merge_group":
        queue = _queue_evidence(
            api,
            fixture,
            event,
            target_branch=integration.branch,
        )
        entries_first = _queue_entries(queue, "first")
        entries_second = _queue_entries(queue, "second")
        rule = effective_singleton_queue(
            queue["rules_first"], entries_first, integration.branch
        )
        group_first = queue["group_first"]
        group_second = queue["group_second"]
        mapped = map_merge_group_to_pr(
            group_first,
            entries_first,
            {int(number): value for number, value in queue["live_prs_first"].items()},
            target_branch=integration.branch,
            required_checks=rule["required_checks"],
        )
        event_group = verify_merge_group_event(
            event,
            group_first,
            queue["commit_first"],
            repository=repository,
            target_branch=integration.branch,
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
    return required_check_decision(
        event_name,
        pr,
        {"verified": bool(evidence)},
        integration_branch=integration.branch,
    )


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
