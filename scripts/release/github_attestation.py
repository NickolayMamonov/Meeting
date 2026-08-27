#!/usr/bin/env python3
"""Typed, bounded verification of GitHub artifact attestations.

This module owns the command and subject-set contract shared by release
publication and protected evidence collection.  It deliberately does not
interpret or retain the CLI's stderr/stdout in exceptions: the attestation
token and signed response must never become log material.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping, MutableMapping

from release_evidence import AttestedSubject


MAX_ATTESTATION_JSON_BYTES = 8 * 1024 * 1024
SLSA_PROVENANCE_V1 = "https://slsa.dev/provenance/v1"

_REPOSITORY_RE = re.compile(r"^[^/\s]+/[^/\s]+$")
_WORKFLOW_RE = re.compile(r"^[^/\s]+/[^/\s]+/\.github/workflows/[^/\s]+\.yml$")
_SOURCE_REF_RE = re.compile(r"^refs/heads/[^\s/][^\s]*$")
_SOURCE_DIGEST_RE = re.compile(r"^[0-9a-f]{40}$")

_REMOVED_ENVIRONMENT_NAMES = frozenset(
    {
        "RELEASE_API_TOKEN",
        "ATTESTATION_TOKEN",
        "GH_TOKEN",
        "GITHUB_TOKEN",
    }
)


class AttestationError(ValueError):
    """Raised when an attestation policy, result, or subject set is invalid."""


# The release evidence module already defines the canonical local-subject
# value object.  Re-exporting it here keeps the shared verifier and the
# evidence schema on one name/digest representation.
__all__ = [
    "AttestationError",
    "AttestationPolicy",
    "AttestedSubject",
    "MAX_ATTESTATION_JSON_BYTES",
    "SLSA_PROVENANCE_V1",
    "VerifiedAttestation",
    "build_gh_command",
    "child_environment",
    "parse_verified_result",
    "verify_file",
    "verify_attestation",
]


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise AttestationError(message)


def _is_signing_environment_name(name: str) -> bool:
    """Identify signing inputs without copying secret values into this module."""

    upper = name.upper()
    return (
        "SIGNING" in upper
        or "KEYSTORE" in upper
        or "KEY_ALIAS" in upper
        or "PRIVATE_KEY" in upper
        or "CERTIFICATE" in upper
        or upper in {"ALIAS", "STORE_PASSWORD", "KEY_PASSWORD"}
        or upper.endswith(
            (
                "_ALIAS",
                "_CERT_SHA256",
                "_KEY_PASSWORD",
                "_SIGNING_PASSWORD",
                "_STORE_PASSWORD",
            )
        )
    )


@dataclass(frozen=True, slots=True)
class AttestationPolicy:
    """The complete immutable policy bound to one GitHub CLI query."""

    repository: str
    signer_workflow: str
    source_ref: str
    source_digest: str
    predicate_type: str
    result_limit: int

    def __post_init__(self) -> None:
        _require(
            isinstance(self.repository, str) and _REPOSITORY_RE.fullmatch(self.repository) is not None,
            "attestation repository must be owner/repo",
        )
        _require(
            isinstance(self.signer_workflow, str)
            and _WORKFLOW_RE.fullmatch(self.signer_workflow) is not None
            and self.signer_workflow.startswith(self.repository + "/"),
            "attestation signer workflow must be repository-qualified .github/workflows/*.yml",
        )
        _require(
            isinstance(self.source_ref, str) and _SOURCE_REF_RE.fullmatch(self.source_ref) is not None,
            "attestation source ref must be refs/heads/*",
        )
        _require(
            isinstance(self.source_digest, str)
            and _SOURCE_DIGEST_RE.fullmatch(self.source_digest) is not None,
            "attestation source digest must be 40 lowercase hexadecimal characters",
        )
        _require(
            self.predicate_type == SLSA_PROVENANCE_V1,
            "attestation predicate type must be https://slsa.dev/provenance/v1",
        )
        _require(
            isinstance(self.result_limit, int)
            and not isinstance(self.result_limit, bool)
            and self.result_limit == 100,
            "attestation result limit must be 100",
        )

    @property
    def source_sha(self) -> str:
        """Compatibility spelling used by the evidence collector."""

        return self.source_digest


@dataclass(frozen=True, slots=True)
class VerifiedAttestation:
    """One verified CLI result and its complete canonical subject group."""

    path: Path
    policy: AttestationPolicy
    raw_result: Mapping[str, Any]
    statement_subjects: tuple[AttestedSubject, ...]
    matched_subject: AttestedSubject
    file_sha256: str

    @property
    def verified_result(self) -> Mapping[str, Any]:
        return self.raw_result

    @property
    def result(self) -> Mapping[str, Any]:
        return self.raw_result

    @property
    def subjects(self) -> tuple[AttestedSubject, ...]:
        return self.statement_subjects

    @property
    def subject_digest(self) -> str:
        return self.file_sha256


def build_gh_command(path: Path, policy: AttestationPolicy) -> tuple[str, ...]:
    """Compose the only supported ``gh attestation verify`` command."""

    return (
        "gh",
        "attestation",
        "verify",
        str(path),
        "--repo",
        policy.repository,
        "--format",
        "json",
        "--predicate-type",
        policy.predicate_type,
        "--signer-workflow",
        policy.signer_workflow,
        "--source-ref",
        policy.source_ref,
        "--source-digest",
        policy.source_digest,
        "--limit",
        str(policy.result_limit),
    )


def child_environment(
    attestation_token: str,
    *,
    parent_environment: Mapping[str, str] | None = None,
) -> dict[str, str]:
    """Return the sanitized environment for the attestation child process."""

    _require(
        isinstance(attestation_token, str) and bool(attestation_token),
        "attestation token is missing",
    )
    source = os.environ if parent_environment is None else parent_environment
    environment: MutableMapping[str, str] = dict(source)
    for name in tuple(environment):
        if name.upper() in _REMOVED_ENVIRONMENT_NAMES or _is_signing_environment_name(name):
            environment.pop(name, None)
    environment["GH_TOKEN"] = attestation_token
    return dict(environment)


def _statement_from_result(result: Mapping[str, Any]) -> Mapping[str, Any]:
    verification_result = result.get("verificationResult", result.get("verification_result"))
    _require(
        isinstance(verification_result, Mapping),
        "verified attestation result has no verification statement",
    )
    statement = verification_result.get("statement")
    _require(
        isinstance(statement, Mapping),
        "verified attestation result has no verification statement",
    )
    return statement


def parse_verified_result(result: Mapping[str, Any]) -> tuple[AttestedSubject, ...]:
    """Canonicalize and validate the complete statement subject array."""

    _require(isinstance(result, Mapping), "verified attestation result is not an object")
    statement = _statement_from_result(result)
    raw_subjects = statement.get("subject")
    _require(
        isinstance(raw_subjects, list) and bool(raw_subjects),
        "verified attestation statement subjects are missing",
    )

    subjects: list[AttestedSubject] = []
    names: set[str] = set()
    pairs: set[tuple[str, str]] = set()
    for raw_subject in raw_subjects:
        _require(
            isinstance(raw_subject, Mapping),
            "verified attestation statement subject is malformed",
        )
        digest = raw_subject.get("digest")
        _require(
            isinstance(digest, Mapping) and set(digest) == {"sha256"},
            "verified attestation statement subject digest is malformed",
        )
        try:
            subject = AttestedSubject(raw_subject.get("name"), digest["sha256"])
        except (TypeError, ValueError) as error:
            raise AttestationError(
                "verified attestation statement subject is malformed"
            ) from error
        pair = (subject.name, subject.sha256)
        _require(pair not in pairs, "verified attestation statement has duplicate subjects")
        _require(subject.name not in names, "verified attestation statement has duplicate names")
        pairs.add(pair)
        names.add(subject.name)
        subjects.append(subject)
    return tuple(sorted(subjects, key=lambda item: (item.name, item.sha256)))


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as source:
            for block in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(block)
    except OSError as error:
        raise AttestationError(f"attestation subject file cannot be read: {path.name}") from error
    return digest.hexdigest()


def _stdout_size(value: Any) -> int:
    if isinstance(value, bytes):
        return len(value)
    if isinstance(value, str):
        return len(value.encode("utf-8"))
    raise AttestationError("gh attestation verification returned no JSON output")


def _decode_stdout(value: Any) -> Any:
    if isinstance(value, bytes):
        try:
            return json.loads(value.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise AttestationError("gh attestation verification returned invalid JSON") from error
    if isinstance(value, str):
        try:
            return json.loads(value)
        except json.JSONDecodeError as error:
            raise AttestationError("gh attestation verification returned invalid JSON") from error
    raise AttestationError("gh attestation verification returned no JSON output")


def _run_bounded_command(
    command: tuple[str, ...],
    *,
    env: Mapping[str, str],
    stdout_limit: int,
) -> bytes:
    """Run gh while retaining no more than one byte beyond the JSON cap."""

    try:
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=dict(env),
        )
    except OSError as error:
        raise AttestationError("gh attestation verification could not start") from error

    assert process.stdout is not None
    try:
        stdout = process.stdout.read(stdout_limit + 1)
    except OSError as error:
        process.kill()
        process.wait()
        raise AttestationError("gh attestation verification output could not be read") from error
    finally:
        process.stdout.close()

    if len(stdout) > stdout_limit:
        process.kill()
        process.wait()
        raise AttestationError(f"gh attestation JSON exceeded {stdout_limit} bytes")
    if process.wait() != 0:
        raise AttestationError("gh attestation verification failed")
    return stdout


def verify_file(
    path: Path,
    policy: AttestationPolicy,
    *,
    token: str,
    runner: Callable[..., Any] | None = None,
) -> VerifiedAttestation:
    """Run the exact bounded CLI query and verify target membership."""

    if not isinstance(path, Path):
        path = Path(path)
    _require(path.is_file(), f"attestation subject file is missing: {path.name}")
    expected_digest = _file_sha256(path)
    command = build_gh_command(path, policy)
    environment = child_environment(token)
    try:
        if runner is None:
            stdout = _run_bounded_command(
                command,
                env=environment,
                stdout_limit=MAX_ATTESTATION_JSON_BYTES,
            )
        else:
            output = runner(
                command,
                env=environment,
                stdout_limit=MAX_ATTESTATION_JSON_BYTES,
            )
            stdout = getattr(output, "stdout", output)
    except (OSError, subprocess.CalledProcessError, TypeError) as error:
        raise AttestationError(
            f"gh attestation verification failed for {path.name} "
            f"(repository={policy.repository}, source_ref={policy.source_ref}, "
            f"source_digest={policy.source_digest}, subject_digest={expected_digest})"
        ) from error

    if _stdout_size(stdout) > MAX_ATTESTATION_JSON_BYTES:
        raise AttestationError(
            f"gh attestation JSON exceeded {MAX_ATTESTATION_JSON_BYTES} bytes "
            f"for {path.name}"
        )
    value = _decode_stdout(stdout)
    if not isinstance(value, list):
        raise AttestationError(f"gh attestation result is not an array for {path.name}")
    if len(value) >= policy.result_limit:
        raise AttestationError(
            f"gh attestation result reached its pagination limit for {path.name}"
        )
    if len(value) != 1 or not isinstance(value[0], Mapping):
        raise AttestationError(
            f"gh attestation result must contain exactly one object for {path.name}"
        )

    verified_result = value[0]
    subjects = parse_verified_result(verified_result)
    matches = tuple(
        subject
        for subject in subjects
        if subject.name == path.name and subject.sha256 == expected_digest
    )
    if len(matches) != 1:
        raise AttestationError(
            f"gh attestation result does not contain exactly one matching "
            f"{path.name} subject (subject_digest={expected_digest})"
        )
    return VerifiedAttestation(
        path=path,
        policy=policy,
        raw_result=verified_result,
        statement_subjects=subjects,
        matched_subject=matches[0],
        file_sha256=expected_digest,
    )


def verify_attestation(
    path: Path,
    policy: AttestationPolicy,
    attestation_token: str,
    *,
    runner: Callable[..., Any] | None = None,
) -> VerifiedAttestation:
    """Compatibility spelling for callers that use the older token name."""

    return verify_file(path, policy, token=attestation_token, runner=runner)
