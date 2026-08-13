#!/usr/bin/env python3
"""Fail-closed, dependency-free release evidence primitives.

The functions in this module deliberately accept already fetched GitHub API
documents. Network access and credentials stay in the workflow boundary; this
keeps verification deterministic and makes it possible to test every race and
pagination case locally.
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


class EvidenceError(ValueError):
    """Raised when release evidence is missing, ambiguous, or inconsistent."""


ACTIVE_MERGE_QUEUE_STATES = frozenset(
    {"QUEUED", "BUILDING", "AWAITING_CHECKS", "READY", "MERGING"}
)


def canonical_json(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_json(value: Any) -> str:
    return sha256_bytes(canonical_json(value))


def jcs_bytes(value: Any) -> bytes:
    """Return RFC 8785 canonical bytes for the integer-only evidence schema."""

    def encode(item: Any) -> str:
        if item is None or isinstance(item, (bool, int, str)):
            return json.dumps(item, ensure_ascii=False, separators=(",", ":"))
        if isinstance(item, float):
            raise EvidenceError("floating-point values are not allowed in canonical evidence")
        if isinstance(item, list):
            return "[" + ",".join(encode(element) for element in item) + "]"
        if isinstance(item, Mapping):
            if not all(isinstance(key, str) for key in item):
                raise EvidenceError("canonical evidence object keys must be strings")
            keys = sorted(
                item,
                key=lambda key: key.encode("utf-16-be", errors="strict"),
            )
            return "{" + ",".join(
                f"{encode(key)}:{encode(item[key])}" for key in keys
            ) + "}"
        raise EvidenceError(f"unsupported canonical evidence type: {type(item).__name__}")

    return encode(value).encode("utf-8")


def sha256_jcs(value: Any) -> str:
    return sha256_bytes(jcs_bytes(value))


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceError(message)


def _sha256(value: Any, what: str) -> str:
    digest = str(value).lower()
    _require(
        len(digest) == 64 and all(character in "0123456789abcdef" for character in digest),
        f"invalid {what} SHA-256",
    )
    return digest


def _strict_sha256(value: Any, what: str) -> str:
    _require(
        isinstance(value, str)
        and len(value) == 64
        and all(character in "0123456789abcdef" for character in value),
        f"invalid {what} SHA-256",
    )
    return value


def pull_request_tuple(pr: Mapping[str, Any]) -> tuple[Any, ...]:
    """Return the complete mutable PR identity used by race checks."""

    head = pr.get("head", {})
    base = pr.get("base", {})
    head_repository = head.get("repo") or {}
    base_repository = base.get("repo") or {}
    labels = tuple(sorted(str(label.get("name", "")).strip().lower() for label in pr.get("labels", [])))
    classification = classify_pull_request(pr)
    return (
        int(pr["number"]),
        str(head.get("sha", pr.get("head_sha", ""))),
        str(head.get("ref", pr.get("head_ref", ""))),
        str(head_repository.get("full_name", pr.get("head_repository", ""))),
        str(base.get("ref", pr.get("base_ref", ""))),
        str(base.get("sha", pr.get("base_sha", ""))),
        str(base_repository.get("full_name", pr.get("base_repository", ""))),
        str(pr.get("state", "")),
        bool(pr.get("draft", False)),
        labels,
        classification,
    )


def verify_pull_request_snapshot(
    event_pr: Mapping[str, Any],
    first: Mapping[str, Any],
    second: Mapping[str, Any],
    *,
    repository: str,
    target_branch: str = "dev",
) -> Mapping[str, Any]:
    """Bind a pull_request event to two independent live API reads."""

    event_tuple = pull_request_tuple(event_pr)
    first_tuple = pull_request_tuple(first)
    second_tuple = pull_request_tuple(second)
    _require(first is not second, "live PR snapshots are not independent")
    _require(first_tuple == second_tuple, "live PR tuple changed between verification reads")
    _require(event_tuple == first_tuple, "event PR tuple disagrees with live PR")
    (
        _,
        head_sha,
        head_ref,
        head_repository,
        base_ref,
        base_sha,
        base_repository,
        state,
        draft,
        _labels,
        _classification,
    ) = first_tuple
    _require(bool(head_sha and head_ref and base_sha), "live PR tuple is incomplete")
    _require(bool(head_repository), "PR head repository is missing")
    _require(base_repository == repository, "PR base repository mismatch")
    _require(base_ref == target_branch, "PR base ref mismatch")
    _require(state == "open", "PR is not open")
    _require(not draft, "PR is draft")
    return first


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

    if event_name == "pull_request_target":
        event_name = "pull_request"
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
    _require(rule.get("entries_exhaustive") is True, "merge queue entries were not exhaustively enumerated")
    required_checks = [str(value) for value in rule.get("required_checks", [])]
    _require(required_checks, "effective queue has no required checks")
    _require(
        len(required_checks) == len(set(required_checks)),
        "effective queue has duplicate required checks",
    )
    _require(
        "release-please-credential-audit" in required_checks,
        "effective queue does not require release-please-credential-audit",
    )
    _require(len(queue_entries) <= int(rule["max_entries"]), "effective queue exceeds max_entries")
    _require(len(queue_entries) == 1, "effective queue does not contain exactly one active entry")
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
    *,
    required_checks: Sequence[str] = ("release-please-credential-audit",),
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
    _require(
        entry.get("state") in ACTIVE_MERGE_QUEUE_STATES,
        "merge queue entry is not active",
    )
    target_branch = str(group.get("target_branch", ""))
    _require(target_branch == "dev", "merge group target branch mismatch")
    _require(entry.get("target_branch") == target_branch, "queue entry target branch mismatch")
    pr_numbers = [int(number) for number in entry.get("pull_request_numbers", [])]
    _require(len(pr_numbers) == 1, "merge group does not contain exactly one PR")
    number = pr_numbers[0]
    _require(number in live_prs, "merge group PR is not live")
    pr = live_prs[number]
    _require(pr.get("state") == "open", "merge group PR is not open")
    _require(not pr.get("draft", False), "merge group PR is draft")
    _require(pr.get("base_ref") == target_branch, "merge group PR base ref mismatch")
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
    _require(entry.get("head_sha") == expected_tuple[1], "queue entry head SHA mismatch")
    _require(entry.get("base_ref") == expected_tuple[2], "queue entry base ref mismatch")
    _require(entry.get("base_sha") == expected_tuple[3], "queue entry base SHA mismatch")
    conclusions = entry.get("required_check_conclusions")
    _require(isinstance(conclusions, Mapping), "queue entry required-check conclusions are missing")
    expected_checks = {str(value) for value in required_checks}
    _require(
        set(conclusions) == expected_checks,
        "queue entry required-check conclusions are not exact",
    )
    _require(
        conclusions["release-please-credential-audit"] == "success",
        "release-please-credential-audit conclusion is not success",
    )
    _require(
        all(value == "success" for value in conclusions.values()),
        "queue entry has a required check that is not successful",
    )
    return {"entry": entry, "pr": pr, "tuple": expected_tuple}


def verify_merge_group_event(
    event: Mapping[str, Any],
    group: Mapping[str, Any],
    commit: Mapping[str, Any],
    *,
    repository: str,
    target_branch: str = "dev",
) -> Mapping[str, str]:
    """Bind adapter evidence and the synthetic commit to the triggering event."""

    _require(event.get("action") == "checks_requested", "unexpected merge_group action")
    event_repository = str(event.get("repository", {}).get("full_name", ""))
    _require(event_repository == repository, "merge_group repository mismatch")
    event_group = event.get("merge_group")
    _require(isinstance(event_group, Mapping), "merge_group event payload is missing")
    expected = {
        "head_sha": str(event_group.get("head_sha", "")),
        "head_ref": str(event_group.get("head_ref", "")),
        "base_sha": str(event_group.get("base_sha", "")),
        "base_ref": str(event_group.get("base_ref", "")),
    }
    _require(all(expected.values()), "merge_group event identity is incomplete")
    _require(
        expected["base_ref"] == f"refs/heads/{target_branch}",
        "merge_group event base ref mismatch",
    )
    for field, value in expected.items():
        _require(group.get(field) == value, f"queue group {field} disagrees with event")
    _require(commit.get("sha") == expected["head_sha"], "synthetic commit SHA disagrees with event")
    return expected


def verify_two_parent_merge_commit(
    commit: Mapping[str, Any],
    expected_base_sha: str,
    expected_head_sha: str,
) -> None:
    parents = commit.get("parents", [])
    _require(len(parents) == 2, "synthetic merge commit must have exactly two parents")
    _require(expected_head_sha != expected_base_sha, "merge commit parents must be distinct")
    actual = [str(parent.get("sha")) for parent in parents]
    _require(
        actual == [expected_base_sha, expected_head_sha],
        "synthetic merge commit parents do not match base and head",
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
    """Compute identities from canonical evidence bytes, never declared hashes."""

    _require("list_id" not in bundle and "list_id" not in statement, "list IDs are not attestation identities")
    _require(
        bundle.get("media_type") == "application/vnd.dev.sigstore.bundle.v0.3+json",
        "unsupported or synthetic attestation bundle",
    )
    _require(bundle.get("statement") == statement, "bundle/statement content mismatch")
    _require(bundle.get("certificate") == certificate, "bundle/certificate content mismatch")
    _require(bundle.get("rekor") == rekor, "bundle/Rekor content mismatch")
    statement_identity = sha256_jcs(statement)
    bundle_identity = sha256_jcs(bundle)
    encoded_certificate = certificate.get("der_base64")
    _require(isinstance(encoded_certificate, str) and encoded_certificate, "certificate DER is missing")
    try:
        certificate_bytes = base64.b64decode(encoded_certificate, validate=True)
    except (binascii.Error, ValueError) as error:
        raise EvidenceError("certificate DER is not valid base64") from error
    _require(bool(certificate_bytes), "certificate DER is empty")
    certificate_identity = sha256_bytes(certificate_bytes)
    _require(
        str(statement.get("certificate_sha256", "")).lower() == certificate_identity,
        "certificate identity mismatch",
    )
    log_id = _sha256(rekor.get("log_id", ""), "Rekor log ID")
    log_index = rekor.get("log_index")
    integrated_time = rekor.get("integrated_time")
    _require(isinstance(log_index, int) and log_index >= 0, "invalid Rekor log index")
    _require(isinstance(integrated_time, int) and integrated_time > 0, "invalid Rekor integrated time")
    statement_rekor = statement.get("rekor")
    _require(
        statement_rekor == {
            "log_id": log_id,
            "log_index": log_index,
            "integrated_time": integrated_time,
        },
        "Rekor identity mismatch",
    )
    subject = statement.get("subject")
    _require(isinstance(subject, Mapping), "attestation statement subject is missing")
    _require(
        set(subject) == {"name", "sha256"},
        "attestation statement subject fields are not exact",
    )
    _sha256(subject.get("sha256", ""), "attestation subject")
    for field in ("predicate", "signer", "source_ref", "source_sha", "run_id", "run_attempt"):
        _require(statement.get(field) not in (None, ""), f"attestation statement {field} is missing")
    for field in ("run_id", "run_attempt"):
        value = statement[field]
        _require(
            isinstance(value, int) and not isinstance(value, bool) and value > 0,
            f"attestation statement {field} is invalid",
        )
    identity_document = {
        "canonical_bundle_sha256": bundle_identity,
        "statement_sha256": statement_identity,
        "certificate_sha256": certificate_identity,
        "rekor": statement_rekor,
        "subject": subject,
        "predicate": statement["predicate"],
        "signer": statement["signer"],
        "source_ref": statement["source_ref"],
        "source_sha": statement["source_sha"],
        "run_id": statement["run_id"],
        "run_attempt": statement["run_attempt"],
    }
    if "source_repository" in statement:
        _require(
            isinstance(statement["source_repository"], str)
            and bool(statement["source_repository"]),
            "attestation statement source_repository is invalid",
        )
        identity_document["source_repository"] = statement["source_repository"]
    return {
        "canonical_bundle_sha256": bundle_identity,
        "statement_sha256": statement_identity,
        "certificate_identity": certificate_identity,
        "rekor_identity": sha256_jcs(statement_rekor),
        "attestation_identity": sha256_jcs(identity_document),
    }


@dataclass(frozen=True)
class AttestedSubject:
    """One exact local subject from the complete signed DSSE subject set."""

    name: str
    sha256: str

    def __post_init__(self) -> None:
        _require(isinstance(self.name, str) and self.name and self.name == self.name.strip(), "attested subject name is invalid")
        _require(self.name == Path(self.name).name, "attested subject name is not local")
        object.__setattr__(self, "sha256", _strict_sha256(self.sha256, "attested subject"))

    def to_mapping(self) -> dict[str, str]:
        return {"name": self.name, "sha256": self.sha256}

    @classmethod
    def from_mapping(cls, value: Any) -> "AttestedSubject":
        _require(isinstance(value, Mapping), "attested subject is malformed")
        _require(set(value) == {"name", "sha256"}, "attested subject fields are not exact")
        return cls(value["name"], value["sha256"])


def _signed_subjects(bundle: Mapping[str, Any]) -> tuple[bytes, tuple[AttestedSubject, ...], Any]:
    signature = bundle.get("signature")
    _require(isinstance(signature, Mapping), "attestation DSSE signature is missing")
    encoded = signature.get("payload")
    _require(isinstance(encoded, str) and encoded, "attestation DSSE payload is missing")
    try:
        payload = base64.b64decode(encoded, validate=True)
        decoded = json.loads(payload)
    except (binascii.Error, ValueError, json.JSONDecodeError) as error:
        raise EvidenceError("attestation DSSE payload is invalid") from error
    _require(isinstance(decoded, Mapping), "attestation DSSE payload is not an object")
    raw_subjects = decoded.get("subject")
    _require(isinstance(raw_subjects, list) and raw_subjects, "signed DSSE subjects are missing")
    subjects: list[AttestedSubject] = []
    names: set[str] = set()
    for raw in raw_subjects:
        _require(isinstance(raw, Mapping), "signed DSSE subject is malformed")
        _require(set(raw) == {"name", "digest"}, "signed DSSE subject fields are not exact")
        digest = raw.get("digest")
        _require(isinstance(digest, Mapping) and set(digest) == {"sha256"}, "signed DSSE subject digest is malformed")
        subject = AttestedSubject(raw.get("name", ""), digest["sha256"])
        _require(subject.name not in names, "signed DSSE subjects contain duplicate names")
        names.add(subject.name)
        subjects.append(subject)
    return payload, tuple(sorted(subjects, key=lambda item: (item.name, item.sha256))), decoded.get("predicate")


@dataclass(frozen=True)
class AttestationGroupIdentity:
    """Canonical identity for one signed multi-subject attestation event."""

    subjects: tuple[AttestedSubject, ...]
    payload_sha256: str
    rekor_identity: str
    certificate_identity: str
    predicate_identity: str
    source_repository: str
    signer: str
    source_ref: str
    source_sha: str
    run_id: int
    run_attempt: int
    identity: str

    def __post_init__(self) -> None:
        normalized = tuple(sorted(self.subjects, key=lambda item: (item.name, item.sha256)))
        _require(normalized == self.subjects and subjects_are_unique(normalized), "attestation group subjects are not canonical")
        object.__setattr__(self, "payload_sha256", _strict_sha256(self.payload_sha256, "attestation payload"))
        for field in ("rekor_identity", "certificate_identity", "predicate_identity"):
            object.__setattr__(self, field, _strict_sha256(getattr(self, field), f"attestation group {field}"))
        _require(
            all(isinstance(value, str) and value for value in (
                self.source_repository, self.signer, self.source_ref, self.source_sha,
            )),
            "attestation group binding is incomplete",
        )
        _require(
            isinstance(self.run_id, int) and not isinstance(self.run_id, bool) and self.run_id > 0
            and isinstance(self.run_attempt, int) and not isinstance(self.run_attempt, bool) and self.run_attempt > 0,
            "attestation group execution identity is invalid",
        )
        expected = sha256_jcs(self._without_identity())
        _require(self.identity == expected, "attestation group identity mismatch")

    def _without_identity(self) -> dict[str, Any]:
        return {
            "payload_sha256": self.payload_sha256,
            "subjects": [subject.to_mapping() for subject in self.subjects],
            "rekor_identity": self.rekor_identity,
            "certificate_identity": self.certificate_identity,
            "predicate_identity": self.predicate_identity,
            "source_repository": self.source_repository,
            "signer": self.signer,
            "source_ref": self.source_ref,
            "source_sha": self.source_sha,
            "run_id": self.run_id,
            "run_attempt": self.run_attempt,
        }

    def to_mapping(self) -> dict[str, Any]:
        return {"identity": self.identity, **self._without_identity()}

    @classmethod
    def from_mapping(cls, value: Any) -> "AttestationGroupIdentity":
        _require(isinstance(value, Mapping), "attestation group is malformed")
        expected = {
            "identity", "payload_sha256", "subjects", "rekor_identity",
            "certificate_identity", "predicate_identity", "source_repository",
            "signer", "source_ref", "source_sha", "run_id", "run_attempt",
        }
        _require(set(value) == expected, "attestation group fields are not exact")
        subjects = tuple(AttestedSubject.from_mapping(item) for item in value["subjects"])
        return cls(
            subjects=subjects,
            payload_sha256=value["payload_sha256"],
            rekor_identity=value["rekor_identity"],
            certificate_identity=value["certificate_identity"],
            predicate_identity=value["predicate_identity"],
            source_repository=value["source_repository"],
            signer=value["signer"],
            source_ref=value["source_ref"],
            source_sha=value["source_sha"],
            run_id=value["run_id"],
            run_attempt=value["run_attempt"],
            identity=value["identity"],
        )


def subjects_are_unique(subjects: Sequence[AttestedSubject]) -> bool:
    return len(subjects) == len({(item.name, item.sha256) for item in subjects}) == len({item.name for item in subjects})


def attestation_group_identity(
    bundle: Mapping[str, Any],
    statement: Mapping[str, Any],
    certificate: Mapping[str, Any],
    rekor: Mapping[str, Any],
    *,
    source_repository: str | None = None,
) -> AttestationGroupIdentity:
    """Recompute a group from retained DSSE bytes and canonical evidence."""

    payload, subjects, signed_predicate = _signed_subjects(bundle)
    _require(sha256_bytes(payload) == statement.get("payload_sha256"), "attestation payload digest mismatch")
    _require(signed_predicate == statement.get("predicate"), "signed predicate differs from canonical statement")
    local = AttestedSubject.from_mapping(statement.get("subject"))
    _require(local in subjects, "canonical subject is not an exact signed subject member")
    linked = attest_identity(bundle, statement, certificate, rekor)
    repository = source_repository if source_repository is not None else statement.get("source_repository")
    _require(isinstance(repository, str) and repository, "attestation group source repository is missing")
    _require(
        statement.get("source_repository") == repository,
        "attestation group source repository mismatch",
    )
    group_without_identity = {
        "payload_sha256": sha256_bytes(payload),
        "subjects": [subject.to_mapping() for subject in subjects],
        "rekor_identity": linked["rekor_identity"],
        "certificate_identity": linked["certificate_identity"],
        "predicate_identity": sha256_jcs(signed_predicate),
        "source_repository": repository,
        "signer": statement["signer"],
        "source_ref": statement["source_ref"],
        "source_sha": statement["source_sha"],
        "run_id": statement["run_id"],
        "run_attempt": statement["run_attempt"],
    }
    return AttestationGroupIdentity(
        subjects=subjects,
        payload_sha256=group_without_identity["payload_sha256"],
        rekor_identity=group_without_identity["rekor_identity"],
        certificate_identity=group_without_identity["certificate_identity"],
        predicate_identity=group_without_identity["predicate_identity"],
        source_repository=repository,
        signer=statement["signer"],
        source_ref=statement["source_ref"],
        source_sha=statement["source_sha"],
        run_id=statement["run_id"],
        run_attempt=statement["run_attempt"],
        identity=sha256_jcs(group_without_identity),
    )


def verify_attestation_group(
    declared: Mapping[str, Any] | AttestationGroupIdentity,
    bundle: Mapping[str, Any],
    statement: Mapping[str, Any],
    certificate: Mapping[str, Any],
    rekor: Mapping[str, Any],
) -> AttestationGroupIdentity:
    expected = attestation_group_identity(bundle, statement, certificate, rekor)
    actual = declared if isinstance(declared, AttestationGroupIdentity) else AttestationGroupIdentity.from_mapping(declared)
    _require(actual == expected, "declared attestation group differs from recomputed evidence")
    return expected


def verify_attestation_groups(records: Iterable[Mapping[str, Any]]) -> None:
    """Enforce Rekor cardinality after each record has been independently linked."""

    buckets: dict[str, list[tuple[AttestedSubject, AttestationGroupIdentity | None]]] = {}
    seen_subjects: set[tuple[str, str]] = set()
    seen_names: set[str] = set()
    for record in records:
        _require(isinstance(record, Mapping), "attestation group record is malformed")
        subject = AttestedSubject.from_mapping(record.get("subject"))
        key = (subject.name, subject.sha256)
        _require(key not in seen_subjects, "duplicate attestation group subject")
        _require(subject.name not in seen_names, "duplicate attestation group subject name")
        seen_subjects.add(key)
        seen_names.add(subject.name)
        rekor = record.get("rekor_identity")
        _require(isinstance(rekor, str), "attestation group Rekor identity is missing")
        declared = record.get("attestation_group")
        group = None if declared is None else (
            declared if isinstance(declared, AttestationGroupIdentity)
            else AttestationGroupIdentity.from_mapping(declared)
        )
        if group is not None:
            _require(group.rekor_identity == rekor, "attestation group Rekor identity mismatch")
            _require(subject in group.subjects, "attestation group does not contain local subject")
        buckets.setdefault(rekor, []).append((subject, group))
    for bucket in buckets.values():
        subjects = {item for item, _ in bucket}
        groups = [group for _, group in bucket]
        if len(bucket) == 1:
            if groups[0] is not None:
                _require(set(groups[0].subjects) == subjects, "singleton attestation group coverage is not exact")
            continue
        _require(all(group is not None for group in groups), "repeated Rekor has legacy or partial group evidence")
        first = groups[0]
        assert first is not None
        _require(all(group == first for group in groups[1:]), "repeated Rekor has divergent attestation groups")
        _require(set(first.subjects) == subjects, "attestation group coverage is not exact")


def verify_attestation_link(
    producer: Mapping[str, Any],
    authoritative: Mapping[str, Any],
) -> Mapping[str, str]:
    """Require producer-local and exhaustively listed evidence to be identical."""

    producer_identity = attest_identity(
        producer["bundle"],
        producer["statement"],
        producer["certificate"],
        producer["rekor"],
    )
    authoritative_identity = attest_identity(
        authoritative["bundle"],
        authoritative["statement"],
        authoritative["certificate"],
        authoritative["rekor"],
    )
    _require(
        producer_identity == authoritative_identity,
        "producer/list attestation identity mismatch",
    )
    _require(
        jcs_bytes(producer["bundle"]) == jcs_bytes(authoritative["bundle"]),
        "producer/list canonical bundle mismatch",
    )
    return producer_identity


@dataclass(frozen=True)
class ProducerEvidence:
    run_number: int
    run_id: int
    run_attempt: int
    artifact_name: str
    artifact_digest: str
    artifact_id: int
    head_sha: str


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
    workflow_path: str,
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
        and run.get("path") == workflow_path
        and run.get("ref") == protected_ref
        and (head_sha is None or run.get("head_sha") == head_sha)
    ]
    _require(matching, "no exact protected-master producer runs found")
    run_keys = [_producer_key(run) for run in matching]
    _require(len(run_keys) == len(set(run_keys)), "duplicate producer execution identity")
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
    digest = _sha256(digest, "evidence")
    return ProducerEvidence(
        run_number=int(latest["run_number"]),
        run_id=int(latest["id"]),
        run_attempt=int(latest.get("run_attempt", 1)),
        artifact_name=artifact_name,
        artifact_digest=digest,
        artifact_id=int(artifact["id"]),
        head_sha=str(latest.get("head_sha", "")),
    )


def verify_producer_snapshot(first: Mapping[str, Any], second: Mapping[str, Any]) -> None:
    stable_read(first, second, "producer workflow/artifact enumeration")


def verify_producer_artifact(
    payload: Mapping[str, Any],
    selected: ProducerEvidence,
    *,
    repository: str,
    workflow_path: str,
) -> None:
    """Bind the downloaded protected-master artifact to its selected run."""

    _require(payload.get("schema") == 1, "producer evidence schema is unsupported")
    _require(payload.get("repository") == repository, "producer artifact repository mismatch")
    _require(payload.get("workflow_path") == workflow_path, "producer artifact workflow mismatch")
    _require(payload.get("ref") == "refs/heads/master", "producer artifact ref is not protected master")
    if selected.head_sha:
        _require(payload.get("sha") == selected.head_sha, "producer artifact commit SHA mismatch")
    _require(int(payload.get("run_id", -1)) == selected.run_id, "producer artifact run ID mismatch")
    _require(int(payload.get("run_number", -1)) == selected.run_number, "producer artifact run number mismatch")
    _require(int(payload.get("run_attempt", -1)) == selected.run_attempt, "producer artifact attempt mismatch")
    _require(payload.get("status") == "completed", "producer artifact producer status is not completed")
    _require(payload.get("conclusion") == "success", "producer artifact producer conclusion is not success")
