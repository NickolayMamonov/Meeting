#!/usr/bin/env python3
"""Collect verified GitHub Sigstore attestations into the package evidence schema."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import subprocess
from pathlib import Path
from typing import Any, Mapping


class CollectionError(ValueError):
    pass


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def run_gh(path: Path, repo: str, signer_workflow: str, source_ref: str, source_sha: str) -> list[dict[str, Any]]:
    command = [
        "gh",
        "attestation",
        "verify",
        str(path),
        "--repo",
        repo,
        "--format",
        "json",
        "--predicate-type",
        "https://slsa.dev/provenance/v1",
        "--signer-workflow",
        signer_workflow,
        "--source-ref",
        source_ref,
        "--source-digest",
        source_sha,
    ]
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        value = json.loads(result.stdout)
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        raise CollectionError(f"gh attestation verification failed for {path.name}") from error
    if not isinstance(value, list) or not value:
        raise CollectionError(f"no verified attestations were returned for {path.name}")
    return [item for item in value if isinstance(item, dict)]


def _find_raw_bytes(value: Any) -> str | None:
    if isinstance(value, dict):
        for key in ("rawBytes", "der_base64"):
            candidate = value.get(key)
            if isinstance(candidate, str) and candidate:
                return candidate
        for child in value.values():
            found = _find_raw_bytes(child)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = _find_raw_bytes(child)
            if found:
                return found
    return None


def _log_id(value: Any) -> str:
    if isinstance(value, dict):
        value = value.get("keyId", value.get("key_id"))
    if not isinstance(value, str) or not value:
        raise CollectionError("transparency log ID is missing")
    compact = value.removeprefix("0x").lower()
    if len(compact) == 64 and all(char in "0123456789abcdef" for char in compact):
        return compact
    try:
        decoded = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as error:
        raise CollectionError("transparency log ID is not hex or base64") from error
    return sha256_bytes(decoded)


def _rekor(bundle: Mapping[str, Any], verification: Mapping[str, Any]) -> dict[str, Any]:
    material = bundle.get("verificationMaterial", bundle.get("verification_material", {}))
    entries = material.get("tlogEntries", material.get("tlog_entries", []))
    if not entries:
        timestamps = verification.get("verifiedTimestamps", [])
        entries = timestamps
    if not isinstance(entries, list) or not entries:
        raise CollectionError("verified attestation has no Rekor entry")
    entry = entries[0]
    log_index = entry.get("logIndex", entry.get("log_index"))
    integrated_time = entry.get("integratedTime", entry.get("integrated_time"))
    if isinstance(log_index, str):
        log_index = int(log_index)
    if isinstance(integrated_time, str):
        integrated_time = int(integrated_time)
    if not isinstance(log_index, int) or not isinstance(integrated_time, int):
        raise CollectionError("Rekor entry is missing index or integrated time")
    return {
        "log_id": _log_id(entry.get("logId", entry.get("log_id"))),
        "log_index": log_index,
        "integrated_time": integrated_time,
    }


def _record(
    path: Path,
    verified: dict[str, Any],
    *,
    source_ref: str,
    source_sha: str,
    signer_workflow: str,
    run_id: int,
    run_attempt: int,
) -> dict[str, Any]:
    bundle_raw = verified.get("attestation", verified.get("bundle"))
    result = verified.get("verificationResult", verified.get("verification_result", {}))
    if not isinstance(bundle_raw, dict) or not isinstance(result, dict):
        raise CollectionError(f"malformed verified attestation for {path.name}")
    statement_raw = result.get("statement", {})
    if not isinstance(statement_raw, dict):
        raise CollectionError(f"verified statement is missing for {path.name}")
    subjects = statement_raw.get("subject", [])
    if not isinstance(subjects, list):
        raise CollectionError(f"verified statement subjects are malformed for {path.name}")
    expected_digest = sha256_bytes(path.read_bytes())
    if not any(
        isinstance(subject, dict)
        and isinstance(subject.get("digest"), dict)
        and subject["digest"].get("sha256") == expected_digest
        for subject in subjects
    ):
        raise CollectionError(f"verified attestation does not cover {path.name}")
    certificate_bytes = _find_raw_bytes(result.get("signature", {})) or _find_raw_bytes(bundle_raw)
    if not certificate_bytes:
        raise CollectionError(f"certificate DER is missing for {path.name}")
    try:
        base64.b64decode(certificate_bytes, validate=True)
    except (binascii.Error, ValueError) as error:
        raise CollectionError(f"certificate DER is invalid for {path.name}") from error
    certificate = {"der_base64": certificate_bytes}
    rekor = _rekor(bundle_raw, result)
    statement = {
        "subject": {"name": path.name, "sha256": expected_digest},
        "predicate": statement_raw.get("predicate", {}),
        "signer": signer_workflow,
        "source_ref": source_ref,
        "source_sha": source_sha,
        "run_id": run_id,
        "run_attempt": run_attempt,
        "certificate_sha256": sha256_bytes(base64.b64decode(certificate_bytes)),
        "rekor": rekor,
    }
    bundle = {
        "media_type": "application/vnd.dev.sigstore.bundle.v0.3+json",
        "statement": statement,
        "certificate": certificate,
        "rekor": rekor,
        "signature": bundle_raw,
    }
    producer = {
        "bundle": bundle,
        "statement": statement,
        "certificate": certificate,
        "rekor": rekor,
    }
    return {"subject": statement["subject"], "producer": producer, "authoritative": producer}


def collect(args: argparse.Namespace) -> None:
    files = sorted(path for path in Path(args.directory).iterdir() if path.is_file())
    records: list[dict[str, Any]] = []
    for path in files:
        verified = run_gh(path, args.repo, args.signer_workflow, args.source_ref, args.source_sha)
        records.append(
            _record(
                path,
                verified[0],
                source_ref=args.source_ref,
                source_sha=args.source_sha,
                signer_workflow=args.signer_workflow,
                run_id=args.run_id,
                run_attempt=args.run_attempt,
            )
        )
    Path(args.output).write_text(json.dumps({"records": records}), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--signer-workflow", required=True)
    parser.add_argument("--source-ref", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", required=True, type=int)
    parser.add_argument("--run-attempt", required=True, type=int)
    try:
        collect(parser.parse_args())
    except (CollectionError, OSError, ValueError, KeyError) as error:
        print(f"attestation evidence collection failed: {error}")
        return 1
    print("attestation evidence collection passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
