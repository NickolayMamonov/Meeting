#!/usr/bin/env python3
"""Collect verified GitHub Sigstore attestations into the package evidence schema."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import ssl
import subprocess
from pathlib import Path
from typing import Any, Mapping


class CollectionError(ValueError):
    pass


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def run_gh(path: Path, repo: str, signer_workflow: str, source_ref: str, source_sha: str) -> dict[str, Any]:
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
        "--limit",
        "100",
    ]
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        value = json.loads(result.stdout)
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        raise CollectionError(f"gh attestation verification failed for {path.name}") from error
    if not isinstance(value, list) or not value:
        raise CollectionError(f"no verified attestations were returned for {path.name}")
    verified = [item for item in value if isinstance(item, dict)]
    if len(verified) != 1:
        raise CollectionError(
            f"ambiguous verified attestations were returned for {path.name}: {len(verified)}"
        )
    if len(value) >= 100:
        raise CollectionError(f"attestation result reached its pagination limit for {path.name}")
    return next(iter(verified))


def _certificate_base64(value: Any) -> str:
    if not isinstance(value, Mapping):
        raise CollectionError("X.509 certificate object is missing")
    candidate = value.get("rawBytes", value.get("der_base64"))
    if not isinstance(candidate, str) or not candidate:
        raise CollectionError("X.509 certificate rawBytes are missing")
    try:
        der = base64.b64decode(candidate, validate=True)
    except (binascii.Error, ValueError) as error:
        raise CollectionError("X.509 certificate DER is not valid base64") from error
    if not der:
        raise CollectionError("X.509 certificate DER is empty")
    try:
        ssl.DER_cert_to_PEM_cert(der)
        subprocess.run(
            ["openssl", "x509", "-inform", "DER", "-noout"],
            input=der,
            capture_output=True,
            check=True,
        )
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        raise CollectionError("X.509 certificate DER could not be parsed") from error
    return base64.b64encode(der).decode("ascii")


def _certificate_from_bundle(
    bundle: Mapping[str, Any],
    fallback: Mapping[str, Any] | None = None,
) -> str:
    certificate = bundle.get("certificate")
    material = bundle.get("verificationMaterial", bundle.get("verification_material", {}))
    if certificate is None and isinstance(material, Mapping):
        certificate = material.get("certificate")
    if certificate is None and isinstance(fallback, Mapping):
        signature = fallback.get("signature", {})
        certificate = signature.get("certificate") if isinstance(signature, Mapping) else None
    if isinstance(certificate, Mapping):
        return _certificate_base64(certificate)
    raise CollectionError("canonical attestation bundle has no certificate")


def _payload_from_bundle(bundle: Mapping[str, Any]) -> tuple[dict[str, Any], bytes]:
    statement = bundle.get("statement")
    envelope = bundle.get("dsseEnvelope", bundle.get("dsse_envelope"))

    # The DSSE payload is signed evidence and therefore takes precedence
    # whenever an envelope is present.  A separately materialized statement
    # is only an equivalent representation, never an alternate source of
    # truth.  This prevents a caller from hiding a payload conflict behind a
    # convenient top-level field.
    if envelope is not None:
        if not isinstance(envelope, Mapping):
            raise CollectionError("attestation DSSE envelope is malformed")
        encoded = envelope.get("payload")
        if not isinstance(encoded, str) or not encoded:
            raise CollectionError("attestation DSSE payload is missing")
        try:
            payload = base64.b64decode(encoded, validate=True)
            decoded = json.loads(payload)
        except (binascii.Error, ValueError, json.JSONDecodeError) as error:
            raise CollectionError("attestation DSSE payload is invalid") from error
        if not isinstance(decoded, dict):
            raise CollectionError("attestation DSSE payload is not an object")
        if statement is not None:
            if not isinstance(statement, Mapping) or dict(statement) != decoded:
                raise CollectionError("top-level statement conflicts with DSSE payload")
        return decoded, payload

    if not isinstance(statement, Mapping):
        raise CollectionError("attestation statement is missing")
    payload = json.dumps(
        statement, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return dict(statement), payload


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
    if not isinstance(material, Mapping):
        material = {}
    entries = material.get("tlogEntries", material.get("tlog_entries", []))
    if not entries:
        timestamps = verification.get("verifiedTimestamps", [])
        entries = timestamps
    if not isinstance(entries, list) or not entries:
        raise CollectionError("verified attestation has no Rekor entry")
    if len(entries) != 1:
        raise CollectionError("verified attestation has ambiguous Rekor entries")
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
    authoritative_raw = verified.get("authoritative")
    if authoritative_raw is None and isinstance(result, Mapping):
        authoritative_raw = result.get("authoritativeBundle", result.get("authoritative"))
    if authoritative_raw is None and isinstance(result, Mapping):
        authoritative_raw = {
            "media_type": bundle_raw.get("media_type") if isinstance(bundle_raw, Mapping) else None,
            "statement": result.get("statement"),
            "certificate": (
                result.get("signature", {}).get("certificate")
                if isinstance(result.get("signature"), Mapping)
                else None
            ),
            "verificationMaterial": bundle_raw.get("verificationMaterial")
            if isinstance(bundle_raw, Mapping)
            else None,
            "signature": result.get("signature", {}),
        }
    if not isinstance(bundle_raw, dict) or not isinstance(authoritative_raw, dict) or not isinstance(result, dict):
        raise CollectionError(f"malformed verified attestation for {path.name}")
    statement_raw, payload_bytes = _payload_from_bundle(bundle_raw)
    if not isinstance(statement_raw, dict):
        raise CollectionError(f"verified statement is missing for {path.name}")
    verified_statement = result.get("statement", {})
    if not isinstance(verified_statement, Mapping):
        raise CollectionError(f"parsed verified statement is missing for {path.name}")
    if json.dumps(statement_raw, sort_keys=True, separators=(",", ":")) != json.dumps(
        verified_statement, sort_keys=True, separators=(",", ":")
    ):
        raise CollectionError(f"DSSE payload and parsed statement differ for {path.name}")
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
    signature = result.get("signature", {})
    if not isinstance(signature, Mapping):
        raise CollectionError(f"verified X.509 signature is missing for {path.name}")
    certificate_bytes = _certificate_from_bundle(
        {"certificate": signature.get("certificate")},
        result,
    )
    bundle_certificate_bytes = _certificate_from_bundle(bundle_raw, result)
    if bundle_certificate_bytes != certificate_bytes:
        raise CollectionError(f"bundle and X.509 certificate evidence differ for {path.name}")
    authoritative_certificate_bytes = _certificate_from_bundle(authoritative_raw, result)
    if certificate_bytes != authoritative_certificate_bytes:
        raise CollectionError(f"producer and authoritative certificates differ for {path.name}")
    certificate = {"der_base64": certificate_bytes}
    rekor = _rekor(bundle_raw, result)
    authoritative_rekor = _rekor(authoritative_raw, result)
    if rekor != authoritative_rekor:
        raise CollectionError(f"producer and authoritative Rekor entries differ for {path.name}")
    authoritative_statement = authoritative_raw.get("statement")
    if not isinstance(authoritative_statement, dict):
        raise CollectionError(f"authoritative statement is missing for {path.name}")
    if authoritative_statement.get("subject") != statement_raw.get("subject"):
        raise CollectionError(f"producer and authoritative subjects differ for {path.name}")
    statement = {
        "subject": {"name": path.name, "sha256": expected_digest},
        "predicate": statement_raw.get("predicate", {}),
        "signer": signer_workflow,
        "source_ref": source_ref,
        "source_sha": source_sha,
        "run_id": run_id,
        "run_attempt": run_attempt,
        "payload_sha256": sha256_bytes(payload_bytes),
        "certificate_sha256": sha256_bytes(base64.b64decode(certificate_bytes)),
        "rekor": rekor,
    }
    bundle = {
        "media_type": "application/vnd.dev.sigstore.bundle.v0.3+json",
        "statement": statement,
        "certificate": certificate,
        "rekor": rekor,
        "signature": bundle_raw.get(
            "dsseEnvelope",
            bundle_raw.get("signature", {}),
        ),
    }
    authoritative_bundle = {
        "media_type": "application/vnd.dev.sigstore.bundle.v0.3+json",
        "statement": statement,
        "certificate": certificate,
        "rekor": authoritative_rekor,
        "signature": bundle["signature"],
    }
    producer = {
        "bundle": bundle,
        "statement": statement,
        "certificate": certificate,
        "rekor": rekor,
    }
    authoritative_statement = dict(statement)
    authoritative_bundle["statement"] = authoritative_statement
    authoritative = {
        "bundle": authoritative_bundle,
        "statement": authoritative_statement,
        "certificate": certificate,
        "rekor": authoritative_rekor,
    }
    return {
        "subject": statement["subject"],
        "producer": producer,
        "authoritative": authoritative,
    }


def collect(args: argparse.Namespace) -> None:
    files = sorted(path for path in Path(args.directory).iterdir() if path.is_file())
    records: list[dict[str, Any]] = []
    for path in files:
        verified = run_gh(path, args.repo, args.signer_workflow, args.source_ref, args.source_sha)
        records.append(
            _record(
                path,
                verified,
                source_ref=args.source_ref,
                source_sha=args.source_sha,
                signer_workflow=args.signer_workflow,
                run_id=args.run_id,
                run_attempt=args.run_attempt,
            )
        )
    subjects = [record["subject"]["name"] for record in records]
    if len(subjects) != len(set(subjects)):
        raise CollectionError("attestation collection returned duplicate local subjects")
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
