#!/usr/bin/env python3
"""Fail-closed, dependency-free release evidence primitives.

The functions in this module deliberately accept already fetched GitHub API
documents. Network access and credentials stay in the workflow boundary; this
keeps verification deterministic and makes it possible to test every race and
pagination case locally.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Any, Iterable, Mapping, Sequence


class EvidenceError(ValueError):
    """Raised when release evidence is missing, ambiguous, or inconsistent."""


def canonical_json(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_json(value: Any) -> str:
    return sha256_bytes(canonical_json(value))


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceError(message)


def classify_pull_request(pr: Mapping[str, Any]) -> str:
    """Return the explicit release classification required by the audit.

    Missing classification is release-relevant. It must never be interpreted
    as N/A merely because the PR has no obvious release files.
    """

    labels = {str(label.get("name", "")).strip().lower() for label in pr.get("labels", [])}
    body = str(pr.get("body", ""))
    marker = "release-classification:"
    classifications: list[str] = []
    for line in body.splitlines():
        if line.lower().startswith(marker):
            value = line[len(marker) :].strip().lower()
            if value in {"release", "non-release"}:
                classifications.append(value)
                continue
            raise EvidenceError("invalid explicit release classification")
    if "release" in labels:
        classifications.append("release")
    if "non-release" in labels:
        classifications.append("non-release")
    head_ref = str(pr.get("head", {}).get("ref", ""))
    if "autorelease: pending" in labels or head_ref.startswith("release-please--branches--dev"):
        classifications.append("release")
    _require(classifications, "PR has no explicit release classification")
    _require(
        len(set(classifications)) == 1,
        "PR has conflicting release classifications",
    )
    return classifications[0]


def required_check_decision(
    event_name: str,
    pr: Mapping[str, Any] | None,
    evidence: Mapping[str, Any],
) -> str:
    """Compute PASS/N/A while keeping the check unconditional.

    A release PR requires verified evidence. An explicitly classified
    non-release PR is the only permitted N/A outcome. Merge groups are always
    release-relevant because their exact PR mapping must be proved.
    """

    if event_name not in {"pull_request", "merge_group"}:
        raise EvidenceError("credential audit only supports pull_request and merge_group")
    if event_name == "pull_request" and pr is not None:
        classification = classify_pull_request(pr)
        if classification == "non-release":
            return "N/A"
    _require(bool(evidence.get("verified")), "release credential evidence was not verified")
    return "PASS"


def effective_singleton_queue(
    repository_rules: Sequence[Mapping[str, Any]],
    queue_entries: Sequence[Mapping[str, Any]],
    target_branch: str = "dev",
) -> Mapping[str, Any]:
    """Prove the effective queue is singleton and contains exhaustive entries."""

    matching = [
        rule
        for rule in repository_rules
        if rule.get("target_branch") == target_branch
        and rule.get("enabled", True)
    ]
    _require(len(matching) == 1, "effective dev merge queue configuration is not singleton")
    rule = matching[0]
    _require(rule.get("max_entries") == 1, "dev merge queue max_entries must be one")
    _require(rule.get("min_entries") == 1, "dev merge queue min_entries must be one")
    _require(rule.get("batch_size") == 1, "dev merge queue batch_size must be one")
    _require(rule.get("grouping") in {"NONE", "none"}, "dev queue batching is enabled")
    _require(
        rule.get("entries_exhaustive") is True,
        "merge queue entries were not exhaustively enumerated",
    )
    _require(rule.get("entries_exhaustive") is True, "merge queue entries were not exhaustively enumerated")
    _require(bool(rule.get("required_checks")), "effective queue has no required checks")
    _require(
        all(entry.get("target_branch") == target_branch for entry in queue_entries),
        "queue entries contain another target branch",
    )
    _require(
        len(queue_entries) == len({str(entry.get("id")) for entry in queue_entries}),
        "queue entries contain duplicate identities",
    )
    return rule


def map_merge_group_to_pr(
    group: Mapping[str, Any],
    queue_entries: Sequence[Mapping[str, Any]],
    live_prs: Mapping[int, Mapping[str, Any]],
) -> Mapping[str, Any]:
    """Map a synthetic merge group to exactly one live PR."""

    entry_id = group.get("queue_entry_id")
    matches = [entry for entry in queue_entries if entry.get("id") == entry_id]
    _require(len(matches) == 1, "merge group queue entry is absent or ambiguous")
    entry = matches[0]
    _require(
        entry.get("exhaustive") is True,
        "merge queue entry is not from an exhaustive enumeration",
    )
    pr_numbers = [int(number) for number in entry.get("pull_request_numbers", [])]
    _require(len(pr_numbers) == 1, "merge group does not contain exactly one PR")
    number = pr_numbers[0]
    _require(number in live_prs, "merge group PR is not live")
    pr = live_prs[number]
    _require(pr.get("state") == "open", "merge group PR is not open")
    _require(not pr.get("draft", False), "merge group PR is draft")
    expected_tuple = (
        int(pr["number"]),
        str(pr["head_sha"]),
        str(pr["base_ref"]),
        str(pr["base_sha"]),
    )
    _require(
        tuple(group.get("pr_tuple", ())) == expected_tuple,
        "merge group PR tuple disagrees with live PR",
    )
    return {"entry": entry, "pr": pr, "tuple": expected_tuple}


def verify_two_parent_merge_commit(
    commit: Mapping[str, Any],
    expected_head_sha: str,
    expected_base_sha: str,
) -> None:
    parents = commit.get("parents", [])
    _require(len(parents) == 2, "synthetic merge commit must have exactly two parents")
    _require(expected_head_sha != expected_base_sha, "merge commit parents must be distinct")
    actual = [str(parent.get("sha")) for parent in parents]
    _require(
        actual == [expected_head_sha, expected_base_sha],
        "synthetic merge commit parents do not match head and base",
    )


def stable_read(first: Any, second: Any, what: str) -> Any:
    _require(first == second, f"{what} changed between verification reads")
    return first


def attest_identity(
    bundle: Mapping[str, Any],
    statement: Mapping[str, Any],
    certificate: Mapping[str, Any],
    rekor: Mapping[str, Any],
) -> Mapping[str, str]:
    """Link an attestation using executable canonical identities only."""

    bundle_digest = str(bundle.get("digest", ""))
    statement_bundle = str(statement.get("bundle_digest", ""))
    certificate_identity = str(certificate.get("identity", ""))
    statement_certificate = str(statement.get("certificate_identity", ""))
    rekor_identity = str(rekor.get("entry_uuid") or rekor.get("log_index") or "")
    statement_rekor = str(statement.get("rekor_entry_uuid") or statement.get("rekor_log_index") or "")
    _require(bundle_digest and bundle_digest == statement_bundle, "bundle/statement identity mismatch")
    _require(
        certificate_identity and certificate_identity == statement_certificate,
        "certificate identity mismatch",
    )
    _require(rekor_identity and rekor_identity == statement_rekor, "Rekor identity mismatch")
    _require("list_id" not in bundle and "list_id" not in statement, "list IDs are not attestation identities")
    return {
        "bundle_digest": bundle_digest,
        "certificate_identity": certificate_identity,
        "rekor_identity": rekor_identity,
    }


@dataclass(frozen=True)
class ProducerEvidence:
    run_number: int
    run_id: int
    run_attempt: int
    artifact_name: str
    artifact_digest: str
    artifact_id: int


def _producer_key(run: Mapping[str, Any]) -> tuple[int, int, int]:
    return (
        int(run.get("run_number", -1)),
        int(run.get("id", -1)),
        int(run.get("run_attempt", -1)),
    )


def select_latest_producer_evidence(
    runs: Iterable[Mapping[str, Any]],
    artifacts_by_run: Mapping[int, Sequence[Mapping[str, Any]]],
    *,
    workflow_id: int,
    protected_ref: str = "refs/heads/master",
    head_sha: str | None = None,
    artifact_name: str = "credential-audit-evidence.json",
) -> ProducerEvidence:
    """Select exact evidence without fallback from a newer bad run.

    Every matching run is considered. The greatest run-number/run-ID/attempt
    is authoritative: failure, expiry, absence, duplicate names, or changing
    artifact metadata at that run fails closed.
    """

    matching = [
        run
        for run in runs
        if int(run.get("workflow_id", -1)) == workflow_id
        and run.get("ref") == protected_ref
        and (head_sha is None or run.get("head_sha") == head_sha)
    ]
    _require(matching, "no exact protected-master producer runs found")
    latest = max(matching, key=_producer_key)
    _require(latest.get("status") == "completed", "newest producer run is incomplete")
    _require(latest.get("conclusion") == "success", "newest producer run did not succeed")
    artifacts = [
        artifact
        for artifact in artifacts_by_run.get(int(latest["id"]), [])
        if artifact.get("name") == artifact_name
    ]
    _require(len(artifacts) == 1, "newest producer run has missing or duplicate evidence")
    artifact = artifacts[0]
    _require(not artifact.get("expired", False), "newest producer evidence is expired")
    digest = str(artifact.get("digest", ""))
    if digest.startswith("sha256:"):
        digest = digest.removeprefix("sha256:")
    _require(len(digest) == 64 and all(c in "0123456789abcdef" for c in digest), "invalid evidence digest")
    return ProducerEvidence(
        run_number=int(latest["run_number"]),
        run_id=int(latest["id"]),
        run_attempt=int(latest.get("run_attempt", 1)),
        artifact_name=artifact_name,
        artifact_digest=digest,
        artifact_id=int(artifact["id"]),
    )


def verify_producer_snapshot(first: Mapping[str, Any], second: Mapping[str, Any]) -> None:
    stable_read(first, second, "producer workflow/artifact enumeration")
