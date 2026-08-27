#!/usr/bin/env python3
"""Collect verified GitHub Sigstore attestations into the package evidence schema."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
import ssl
import subprocess
from pathlib import Path
from typing import Any, Mapping

from release_evidence import (
    AttestedSubject,
    EvidenceError,
    attestation_group_identity,
    verify_attestation_groups,
)
from github_attestation import (
    AttestationError,
    AttestationPolicy,
    SLSA_PROVENANCE_V1,
    parse_verified_result,
    verify_file,
)


class CollectionError(ValueError):
    pass


_DIRECT_BUNDLE_FIELDS = frozenset(
    {
        "certificate",
        "dsseEnvelope",
        "dsse_envelope",
        "mediaType",
        "media_type",
        "rekor",
        "signature",
        "statement",
        "verificationMaterial",
        "verification_material",
    }
)


def _aliased_value(
    value: Mapping[str, Any],
    names: tuple[str, ...],
    description: str,
) -> Any:
    present = [name for name in names if name in value]
    if len(present) > 1:
        first = value[present[0]]
        if any(value[name] != first for name in present[1:]):
            raise CollectionError(f"{description} has conflicting aliases")
    return value[present[0]] if present else None


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def run_gh(
    path: Path,
    repo: str,
    signer_workflow: str,
    source_ref: str,
    source_sha: str,
    *,
    attestation_token: str | None = None,
    run_id: int | None = None,
    run_attempt: int | None = None,
) -> dict[str, Any]:
    """Compatibility adapter returning the raw one-result CLI record."""

    try:
        policy = AttestationPolicy(
            repository=repo,
            signer_workflow=signer_workflow,
            source_ref=source_ref,
            source_digest=source_sha,
            predicate_type=SLSA_PROVENANCE_V1,
            result_limit=100,
        )
        verified = verify_file(
            path,
            policy,
            token=(
                os.environ.get("ATTESTATION_TOKEN")
                or os.environ.get("GH_TOKEN", "")
                if attestation_token is None
                else attestation_token
            ),
            run_id=run_id,
            run_attempt=run_attempt,
        )
    except AttestationError as error:
        raise CollectionError(str(error)) from error
    return dict(verified.raw_result)


def _certificate_base64(value: Any) -> str:
    if not isinstance(value, Mapping):
        raise CollectionError("X.509 certificate object is missing")
    candidate = _aliased_value(value, ("rawBytes", "der_base64"), "X.509 certificate")
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
) -> str:
    certificate = bundle.get("certificate")
    material = _aliased_value(
        bundle,
        ("verificationMaterial", "verification_material"),
        "verification material",
    )
    if material is None:
        material = {}
    if not isinstance(material, Mapping):
        raise CollectionError("verification material is malformed")
    material_certificate = material.get("certificate")
    if certificate is not None and material_certificate is not None:
        certificate_bytes = _certificate_base64(certificate)
        material_certificate_bytes = _certificate_base64(material_certificate)
        if certificate_bytes != material_certificate_bytes:
            raise CollectionError("certificate has conflicting locations")
        return certificate_bytes
    if certificate is None:
        certificate = material_certificate
    if isinstance(certificate, Mapping):
        return _certificate_base64(certificate)
    raise CollectionError("canonical attestation bundle has no certificate")


def _payload_from_bundle(bundle: Mapping[str, Any]) -> tuple[dict[str, Any], bytes]:
    statement = bundle.get("statement")
    envelope = _aliased_value(
        bundle,
        ("dsseEnvelope", "dsse_envelope"),
        "DSSE envelope",
    )

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

    raise CollectionError("attestation DSSE envelope is missing")


def _canonical_dsse_envelope(bundle: Mapping[str, Any]) -> bytes:
    """Return the defined canonical representation of a bundle's DSSE envelope.

    The parsed envelope is serialized with sorted keys and compact JSON.  The
    payload's encoded string is therefore compared exactly, while any extra or
    changed envelope fields remain visible to the comparison.
    """
    envelope = _aliased_value(
        bundle,
        ("dsseEnvelope", "dsse_envelope"),
        "DSSE envelope",
    )
    if not isinstance(envelope, Mapping):
        raise CollectionError("attestation DSSE envelope is malformed")
    return json.dumps(
        envelope,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def _log_id(value: Any) -> str:
    if isinstance(value, dict):
        value = _aliased_value(value, ("keyId", "key_id"), "transparency log ID")
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


def _rekor(
    bundle: Mapping[str, Any],
    verification: Mapping[str, Any],
    *,
    allow_verification_fallback: bool = True,
) -> dict[str, Any]:
    material = _aliased_value(
        bundle,
        ("verificationMaterial", "verification_material"),
        "verification material",
    )
    if material is None:
        material = {}
    if not isinstance(material, Mapping):
        raise CollectionError("verification material is malformed")
    entries = _aliased_value(material, ("tlogEntries", "tlog_entries"), "Rekor entries")
    if entries is None:
        entries = []
    if not entries and allow_verification_fallback:
        timestamps = _aliased_value(
            verification,
            ("verifiedTimestamps", "verified_timestamps"),
            "verified timestamps",
        )
        if timestamps is None:
            timestamps = []
        entries = timestamps
    if not isinstance(entries, list) or not entries:
        raise CollectionError("verified attestation has no Rekor entry")
    if len(entries) != 1:
        raise CollectionError("verified attestation has ambiguous Rekor entries")
    entry = entries[0]
    if not isinstance(entry, Mapping):
        raise CollectionError("Rekor entry is malformed")
    log_index = _aliased_value(entry, ("logIndex", "log_index"), "Rekor log index")
    integrated_time = _aliased_value(
        entry,
        ("integratedTime", "integrated_time"),
        "Rekor integrated time",
    )
    try:
        if isinstance(log_index, str):
            log_index = int(log_index)
        if isinstance(integrated_time, str):
            integrated_time = int(integrated_time)
    except (TypeError, ValueError) as error:
        raise CollectionError("Rekor entry index or integrated time is malformed") from error
    if (
        isinstance(log_index, bool)
        or isinstance(integrated_time, bool)
        or not isinstance(log_index, int)
        or not isinstance(integrated_time, int)
    ):
        raise CollectionError("Rekor entry is missing index or integrated time")
    return {
        "log_id": _log_id(_aliased_value(entry, ("logId", "log_id"), "Rekor log ID")),
        "log_index": log_index,
        "integrated_time": integrated_time,
    }


def _is_direct_bundle(value: Any) -> bool:
    """Recognize only the tested signed Sigstore bundle compatibility shape."""
    if not isinstance(value, Mapping) or not value:
        return False
    if "bundle" in value:
        return False
    if "dsseEnvelope" not in value or "dsse_envelope" in value:
        return False
    if "verificationMaterial" not in value or "verification_material" in value:
        return False
    media_type = _aliased_value(value, ("mediaType", "media_type"), "bundle media type")
    if media_type != "application/vnd.dev.sigstore.bundle.v0.3+json":
        return False
    if "statement" in value or "signature" in value or "certificate" in value:
        return False
    envelope = value["dsseEnvelope"]
    material = value["verificationMaterial"]
    return (
        isinstance(envelope, Mapping)
        and isinstance(envelope.get("payload"), str)
        and bool(envelope["payload"])
        and isinstance(material, Mapping)
        and isinstance(material.get("certificate"), Mapping)
        and isinstance(material.get("tlogEntries"), list)
        and bool(material["tlogEntries"])
    )


def _bundle_from_verified_record(verified: Mapping[str, Any]) -> dict[str, Any]:
    """Normalize one verified GitHub CLI record without fallback or merging."""
    if not isinstance(verified, Mapping):
        raise CollectionError("verified attestation record is malformed")
    has_attestation = "attestation" in verified
    has_bundle = "bundle" in verified
    if has_attestation and has_bundle:
        raise CollectionError("verified attestation has conflicting bundle representations")

    if has_bundle:
        candidate = verified["bundle"]
        if not _is_direct_bundle(candidate):
            raise CollectionError("verified attestation bundle is missing or malformed")
        return dict(candidate)

    if not has_attestation:
        raise CollectionError("verified attestation bundle is missing")
    attestation = verified["attestation"]
    if not isinstance(attestation, Mapping) or not attestation:
        raise CollectionError("verified attestation is missing or malformed")

    if "bundle" in attestation:
        candidate = attestation["bundle"]
        if not _is_direct_bundle(candidate):
            raise CollectionError("verified attestation.bundle is missing or malformed")
        # A wrapper must not also carry direct bundle material.
        if _DIRECT_BUNDLE_FIELDS.intersection(attestation):
            raise CollectionError("verified attestation wrapper is hybrid")
        return dict(candidate)

    raise CollectionError("verified attestation.bundle is missing or malformed")


def _authoritative_bundle_from_verified_record(
    verified: Mapping[str, Any],
    result: Mapping[str, Any],
) -> dict[str, Any] | None:
    """Normalize every supported authoritative representation before fallback.

    GitHub CLI responses and older callers can expose authoritative evidence in
    more than one location.  Presence is intentionally tested with ``in``:
    an explicit null is a supplied, malformed representation, not an absent
    one that may re-enter the split-response fallback.
    """
    sources: list[tuple[str, Any]] = []
    if "authoritative" in verified:
        sources.append(("top-level authoritative", verified["authoritative"]))
    if "authoritativeBundle" in result:
        sources.append(
            ("verificationResult.authoritativeBundle", result["authoritativeBundle"])
        )
    if "authoritative" in result:
        sources.append(("verificationResult.authoritative", result["authoritative"]))
    if not sources:
        return None

    normalized: list[tuple[str, dict[str, Any], tuple[Any, ...]]] = []
    for name, candidate in sources:
        if not isinstance(candidate, Mapping) or not _is_direct_bundle(candidate):
            raise CollectionError(f"{name} is missing or malformed")
        bundle = dict(candidate)
        statement, payload = _payload_from_bundle(bundle)
        certificate = _certificate_from_bundle(bundle)
        rekor = _rekor(bundle, {}, allow_verification_fallback=False)
        media_type = _aliased_value(
            bundle,
            ("mediaType", "media_type"),
            "bundle media type",
        )
        identity = (
            media_type,
            payload,
            _canonical_dsse_envelope(bundle),
            json.dumps(statement, sort_keys=True, separators=(",", ":")),
            certificate,
            json.dumps(rekor, sort_keys=True, separators=(",", ":")),
        )
        normalized.append((name, bundle, identity))

    first_name, first_bundle, first_identity = normalized[0]
    for name, _, identity in normalized[1:]:
        if identity != first_identity:
            raise CollectionError(
                f"conflicting authoritative attestation representations: "
                f"{first_name} and {name}"
            )
    return first_bundle


def _record_unwrapped(
    path: Path,
    verified: dict[str, Any],
    *,
    source_repository: str,
    source_ref: str,
    source_sha: str,
    signer_workflow: str,
    run_id: int,
    run_attempt: int,
) -> dict[str, Any]:
    bundle_raw = _bundle_from_verified_record(verified)
    result = _aliased_value(
        verified,
        ("verificationResult", "verification_result"),
        "verification result",
    )
    if result is None:
        result = {}
    if not isinstance(bundle_raw, dict) or not isinstance(result, dict):
        raise CollectionError(f"malformed verified attestation for {path.name}")
    authoritative_raw = _authoritative_bundle_from_verified_record(verified, result)
    has_explicit_authoritative = authoritative_raw is not None
    if authoritative_raw is None:
        # GitHub CLI 2.93 emits the signed bundle and parsed verification
        # result as separate representations.  The latter contains certificate
        # identity metadata, never cryptographic DER.  In the absence of an
        # explicit authoritative bundle, the validated signed bundle remains
        # the sole authority for certificate and Rekor evidence.
        authoritative_raw = bundle_raw
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
    try:
        canonical_subjects = parse_verified_result(verified)
    except AttestationError as error:
        raise CollectionError(f"verified statement subjects are malformed for {path.name}") from error
    expected_digest = sha256_bytes(path.read_bytes())
    expected_subject = AttestedSubject(path.name, expected_digest)
    if expected_subject not in canonical_subjects:
        raise CollectionError(f"verified attestation does not cover {path.name}")
    signature = result.get("signature", {})
    if not isinstance(signature, Mapping):
        raise CollectionError(f"verified X.509 signature is missing for {path.name}")
    if not isinstance(signature.get("certificate"), Mapping):
        raise CollectionError(f"parsed verified X.509 certificate is missing for {path.name}")
    certificate_bytes = _certificate_from_bundle(bundle_raw)
    authoritative_certificate_bytes = _certificate_from_bundle(authoritative_raw)
    if certificate_bytes != authoritative_certificate_bytes:
        raise CollectionError(f"producer and authoritative certificates differ for {path.name}")
    certificate = {"der_base64": certificate_bytes}
    rekor = _rekor(
        bundle_raw,
        result,
        allow_verification_fallback=not has_explicit_authoritative,
    )
    authoritative_rekor = _rekor(
        authoritative_raw,
        result,
        allow_verification_fallback=not has_explicit_authoritative,
    )
    if rekor != authoritative_rekor:
        raise CollectionError(f"producer and authoritative Rekor entries differ for {path.name}")
    authoritative_statement, _ = _payload_from_bundle(authoritative_raw)
    if not isinstance(authoritative_statement, Mapping):
        raise CollectionError(f"authoritative statement is missing for {path.name}")
    if has_explicit_authoritative and _canonical_dsse_envelope(
        bundle_raw
    ) != _canonical_dsse_envelope(authoritative_raw):
        raise CollectionError(
            f"producer and authoritative DSSE envelopes differ for {path.name}"
        )
    if has_explicit_authoritative and dict(authoritative_statement) != statement_raw:
        raise CollectionError(f"producer and authoritative statements differ for {path.name}")
    if authoritative_statement.get("subject") != statement_raw.get("subject"):
        raise CollectionError(f"producer and authoritative subjects differ for {path.name}")
    statement = {
        "subject": {"name": path.name, "sha256": expected_digest},
        "predicate": statement_raw.get("predicate", {}),
        "signer": signer_workflow,
        "source_repository": source_repository,
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
        "signature": bundle_raw["dsseEnvelope"],
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
    group = attestation_group_identity(
        bundle,
        statement,
        certificate,
        rekor,
        source_repository=source_repository,
    )
    return {
        "subject": statement["subject"],
        "rekor_identity": group.rekor_identity,
        "attestation_group": group.to_mapping(),
        "producer": producer,
        "authoritative": authoritative,
    }


def _record(
    path: Path,
    verified: dict[str, Any],
    *,
    source_repository: str = "owner/repo",
    source_ref: str,
    source_sha: str,
    signer_workflow: str,
    run_id: int,
    run_attempt: int,
) -> dict[str, Any]:
    try:
        return _record_unwrapped(
            path,
            verified,
            source_repository=source_repository,
            source_ref=source_ref,
            source_sha=source_sha,
            signer_workflow=signer_workflow,
            run_id=run_id,
            run_attempt=run_attempt,
        )
    except EvidenceError as error:
        raise CollectionError(f"invalid attestation evidence for {path.name}") from error


def collect(args: argparse.Namespace) -> None:
    files = sorted(path for path in Path(args.directory).iterdir() if path.is_file())
    records: list[dict[str, Any]] = []
    for path in files:
        verified = run_gh(
            path,
            args.repo,
            args.signer_workflow,
            args.source_ref,
            args.source_sha,
            run_id=args.run_id,
            run_attempt=args.run_attempt,
        )
        records.append(
            _record(
                path,
                verified,
                source_repository=args.repo,
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
    try:
        verify_attestation_groups(records)
    except EvidenceError as error:
        raise CollectionError("attestation group verification failed") from error
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
