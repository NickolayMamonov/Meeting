#!/usr/bin/env python3
"""Fail-closed recovery gate for reusing a Release Please draft."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping


WORKFLOW_PATH = ".github/workflows/release.yml"
STABLE_EVIDENCE_JOB = "Verify and attest stable artifacts"


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _artifact_digest(value: Any) -> str:
    digest = str(value or "")
    if digest.startswith("sha256:"):
        digest = digest.removeprefix("sha256:")
    _require(
        len(digest) == 64 and all(character in "0123456789abcdef" for character in digest.lower()),
        "producer artifact digest is not SHA-256",
    )
    return digest.lower()


def verify(
    state: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    source_sha: str,
    candidate: Mapping[str, Any],
) -> None:
    if int(state.get("id", -1)) != release_id:
        raise ValueError("recovery release ID mismatch")
    state_tag = state.get("tagName", state.get("tag_name"))
    is_draft = state.get("isDraft", state.get("draft"))
    published_at = state.get("publishedAt", state.get("published_at"))
    if state_tag != tag or is_draft is not True or published_at:
        raise ValueError("recovery requires the existing Release Please draft")
    if candidate.get("tag") != tag or candidate.get("commit") != source_sha:
        raise ValueError("recovery candidate is not bound to the requested tag/commit")
    if candidate.get("source_branch") != "dev":
        raise ValueError("recovery candidate source branch is not dev")


def verify_producer(
    *,
    run: Mapping[str, Any],
    jobs: Mapping[str, Any] | list[Mapping[str, Any]],
    artifact: Mapping[str, Any],
    archive: Path,
    evidence_directory: Path,
    repository: str,
    source_run_id: int,
    source_sha: str,
    tag: str,
    artifact_name: str,
    workflow_path: str = WORKFLOW_PATH,
) -> None:
    """Bind recovery to one successful stable-evidence producer execution.

    The archive digest is checked first, before any extracted release object is
    trusted.  All later claims are bound to the same run ID, attempt, commit,
    tag, workflow, job conclusion, and canonical attestation statements.
    """

    _require(archive.is_file(), "producer artifact ZIP is missing")
    _require(
        _sha256(archive) == _artifact_digest(artifact.get("digest")),
        "downloaded producer artifact ZIP digest mismatch",
    )

    _require(int(run.get("id", -1)) == source_run_id, "producer run ID mismatch")
    _require(run.get("path") == workflow_path, "producer workflow path mismatch")
    _require(run.get("event") == "push", "producer run event is not push")
    _require(run.get("head_branch") == "dev", "producer run branch is not dev")
    _require(run.get("head_sha") == source_sha, "producer run source SHA mismatch")
    _require(run.get("status") == "completed", "producer run is not completed")
    _require(run.get("conclusion") == "success", "producer run conclusion is not success")
    run_attempt = int(run.get("run_attempt", -1))
    _require(run_attempt > 0, "producer run attempt is missing")

    job_items = jobs.get("jobs", []) if isinstance(jobs, Mapping) else jobs
    _require(isinstance(job_items, list), "producer jobs response is malformed")
    stable_jobs = [
        job for job in job_items
        if isinstance(job, Mapping) and job.get("name") == STABLE_EVIDENCE_JOB
    ]
    _require(len(stable_jobs) == 1, "stable evidence producer job is missing or ambiguous")
    stable_job = stable_jobs[0]
    _require(stable_job.get("status") == "completed", "stable evidence job is not completed")
    _require(stable_job.get("conclusion") == "success", "stable evidence job did not succeed")
    _require(int(stable_job.get("run_id", -1)) == source_run_id, "stable evidence job run mismatch")
    _require(int(stable_job.get("run_attempt", -1)) == run_attempt, "stable evidence job attempt mismatch")

    _require(artifact.get("name") == artifact_name, "producer artifact name mismatch")
    _require(not artifact.get("expired", False), "producer artifact is expired")
    workflow_run = artifact.get("workflow_run", {})
    _require(isinstance(workflow_run, Mapping), "producer artifact workflow run is missing")
    _require(int(workflow_run.get("id", -1)) == source_run_id, "artifact workflow run mismatch")
    _require(int(artifact.get("id", -1)) > 0, "producer artifact ID is missing")

    candidate = json.loads((evidence_directory / "release-candidate.json").read_text(encoding="utf-8"))
    authority = json.loads((evidence_directory / "release-authority.json").read_text(encoding="utf-8"))
    _require(isinstance(candidate, Mapping), "producer candidate is malformed")
    _require(isinstance(authority, Mapping), "producer authority is malformed")
    _require(candidate.get("tag") == tag, "producer candidate tag mismatch")
    _require(candidate.get("commit") == source_sha, "producer candidate source SHA mismatch")
    _require(candidate.get("source_branch") == "dev", "producer candidate source branch mismatch")
    _require(authority.get("tag") == tag, "producer authority tag mismatch")
    _require(authority.get("commit") == source_sha, "producer authority source SHA mismatch")
    _require(authority.get("source_branch") == "dev", "producer authority source branch mismatch")

    envelope = json.loads((evidence_directory / "recovery-envelope.json").read_text(encoding="utf-8"))
    _require(isinstance(envelope, Mapping), "producer attestation envelope is malformed")
    references = envelope.get("attestations", [])
    _require(isinstance(references, list) and references, "producer attestation envelope is empty")
    signer = f"{repository}/{workflow_path}"
    for reference in references:
        name = reference.get("name") if isinstance(reference, Mapping) else None
        _require(isinstance(name, str) and Path(name).name == name, "producer attestation reference is invalid")
        attestation = json.loads((evidence_directory / name).read_text(encoding="utf-8"))
        _require(isinstance(attestation, Mapping), "producer attestation is malformed")
        for source in ("producer", "authoritative"):
            source_evidence = attestation.get(source, {})
            _require(isinstance(source_evidence, Mapping), "producer attestation evidence is malformed")
            statement = source_evidence.get("statement", {})
            _require(isinstance(statement, Mapping), "producer attestation statement is missing")
            _require(statement.get("signer") == signer, "producer attestation signer mismatch")
            _require(statement.get("source_ref") == "refs/heads/dev", "producer attestation ref mismatch")
            _require(statement.get("source_sha") == source_sha, "producer attestation SHA mismatch")
            _require(int(statement.get("run_id", -1)) == source_run_id, "producer attestation run ID mismatch")
            _require(
                int(statement.get("run_attempt", -1)) == run_attempt,
                "producer attestation attempt mismatch",
            )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--release-id", type=int, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-metadata", type=Path)
    parser.add_argument("--jobs", type=Path)
    parser.add_argument("--artifact-metadata", type=Path)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--evidence-directory", type=Path)
    parser.add_argument("--repository")
    parser.add_argument("--source-run-id", type=int)
    parser.add_argument("--artifact-name")
    parser.add_argument("--workflow-path", default=WORKFLOW_PATH)
    args = parser.parse_args()
    try:
        state = json.loads(args.state.read_text(encoding="utf-8"))
        candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
        if not isinstance(state, dict) or not isinstance(candidate, dict):
            raise ValueError("recovery inputs must be objects")
        verify(
            state,
            release_id=args.release_id,
            tag=args.tag,
            source_sha=args.source_sha,
            candidate=candidate,
        )
        producer_args = (
            args.run_metadata,
            args.jobs,
            args.artifact_metadata,
            args.archive,
            args.evidence_directory,
            args.repository,
            args.source_run_id,
            args.artifact_name,
        )
        if any(value is not None for value in producer_args):
            if not all(value is not None for value in producer_args):
                raise ValueError("all producer provenance inputs are required together")
            run = json.loads(args.run_metadata.read_text(encoding="utf-8"))
            jobs = json.loads(args.jobs.read_text(encoding="utf-8"))
            artifact = json.loads(args.artifact_metadata.read_text(encoding="utf-8"))
            if not all(isinstance(value, (dict, list)) for value in (run, jobs, artifact)):
                raise ValueError("producer provenance API responses are malformed")
            verify_producer(
                run=run,
                jobs=jobs,
                artifact=artifact,
                archive=args.archive,
                evidence_directory=args.evidence_directory,
                repository=args.repository,
                source_run_id=args.source_run_id,
                source_sha=args.source_sha,
                tag=args.tag,
                artifact_name=args.artifact_name,
                workflow_path=args.workflow_path,
            )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"recovery gate failed: {error}")
        return 1
    print("recovery gate passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
