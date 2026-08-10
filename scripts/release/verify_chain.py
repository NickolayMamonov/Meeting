#!/usr/bin/env python3
"""Verify local release-package references before upload or attestation."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from release_evidence import EvidenceError, verify_attestation_link


class ChainError(ValueError):
    pass


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def read(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ChainError(f"{path.name} is not an object")
    return value


def verify(directory: Path) -> None:
    manifest_name = "snapshot-manifest.json" if (directory / "snapshot-manifest.json").exists() else "release-manifest.json"
    manifest_path = directory / manifest_name
    manifest = read(manifest_path)
    authority_path = directory / "release-authority.json"
    read(authority_path)
    checksums_path = directory / "SHA256SUMS"
    checksum_lines = checksums_path.read_text(encoding="utf-8").splitlines()
    checksum_names = [line.split("  ", 1)[1] for line in checksum_lines]
    if checksum_names != sorted(checksum_names):
        raise ChainError("SHA256SUMS names are not byte-order sorted")
    if any(name in checksum_names for name in ("release-candidate.json", "recovery-envelope.json")):
        raise ChainError("candidate/envelope must be excluded from checksums")
    for line in checksum_lines:
        expected, name = line.split("  ", 1)
        actual = digest(directory / name)
        if actual != expected:
            raise ChainError(f"checksum mismatch for {name}")
    authority_ref = manifest.get("authority", {})
    if authority_ref.get("name") != authority_path.name or authority_ref.get("sha256") != digest(authority_path):
        raise ChainError("manifest authority reference mismatch")
    for item in manifest.get("artifacts", []):
        artifact = directory / item["name"]
        if not artifact.is_file() or digest(artifact) != item["sha256"] or artifact.stat().st_size != item["size"]:
            raise ChainError(f"manifest artifact mismatch for {item['name']}")
    candidate = read(directory / "release-candidate.json")
    if candidate.get("manifest", {}).get("sha256") != digest(manifest_path):
        raise ChainError("candidate manifest reference mismatch")
    if candidate.get("checksums", {}).get("sha256") != digest(checksums_path):
        raise ChainError("candidate checksum reference mismatch")
    envelope = read(directory / "recovery-envelope.json")
    if envelope.get("candidate", {}).get("sha256") != digest(directory / "release-candidate.json"):
        raise ChainError("recovery envelope candidate reference mismatch")
    if envelope.get("authority", {}).get("name") != authority_path.name or envelope.get("authority", {}).get("sha256") != digest(authority_path):
        raise ChainError("recovery envelope authority reference mismatch")
    if "recovery-envelope.json" not in envelope.get("excluded_from_coverage", []):
        raise ChainError("recovery envelope is not excluded from coverage")
    references = envelope.get("attestations", [])
    if not isinstance(references, list):
        raise ChainError("attestation references are not a list")
    reference_names = [reference.get("name") for reference in references]
    if len(reference_names) != len(set(reference_names)):
        raise ChainError("duplicate attestation reference")
    expected_subjects = {
        authority_path.name,
        manifest_path.name,
        checksums_path.name,
        "release-candidate.json",
        *(item["name"] for item in manifest.get("artifacts", [])),
    }
    covered_subjects: set[str] = set()
    canonical_bundles: set[str] = set()
    statements: set[str] = set()
    rekor_identities: set[str] = set()
    identities: set[str] = set()
    for reference in references:
        reference_name = reference["name"]
        if Path(reference_name).name != reference_name:
            raise ChainError("attestation reference is not a local file name")
        attestation_path = directory / reference_name
        attestation = read(attestation_path)
        if reference.get("sha256") != digest(attestation_path):
            raise ChainError(f"attestation digest mismatch for {attestation_path.name}")
        subject = attestation.get("subject", {})
        if Path(subject["name"]).name != subject["name"]:
            raise ChainError("attestation subject is not a local file name")
        subject_path = directory / subject["name"]
        if not subject_path.is_file() or digest(subject_path) != subject.get("sha256"):
            raise ChainError(f"attestation subject mismatch for {subject_path.name}")
        if subject["name"] in covered_subjects:
            raise ChainError(f"duplicate attestation coverage for {subject_path.name}")
        covered_subjects.add(subject["name"])
        try:
            linked = verify_attestation_link(
                attestation["producer"],
                attestation["authoritative"],
            )
        except (EvidenceError, KeyError, TypeError) as error:
            raise ChainError(f"invalid attestation identity for {attestation_path.name}: {error}") from error
        for source in ("producer", "authoritative"):
            if attestation[source]["statement"].get("subject") != {
                "name": subject["name"],
                "sha256": subject["sha256"],
            }:
                raise ChainError(
                    f"{source} attestation subject mismatch for {attestation_path.name}"
                )
        for name, value in linked.items():
            if attestation.get(name) != value or reference.get(name) != value:
                raise ChainError(f"attestation {name} mismatch for {attestation_path.name}")
        if linked["canonical_bundle_sha256"] in canonical_bundles:
            raise ChainError("duplicate canonical attestation bundle")
        if linked["statement_sha256"] in statements:
            raise ChainError("duplicate canonical attestation statement")
        if linked["rekor_identity"] in rekor_identities:
            raise ChainError("duplicate canonical Rekor identity")
        if linked["attestation_identity"] in identities:
            raise ChainError("duplicate canonical attestation identity")
        canonical_bundles.add(linked["canonical_bundle_sha256"])
        statements.add(linked["statement_sha256"])
        rekor_identities.add(linked["rekor_identity"])
        identities.add(linked["attestation_identity"])
    if covered_subjects != expected_subjects:
        raise ChainError("attestation coverage is not exact")
    expected_files = {
        "release-authority.json",
        manifest_path.name,
        "SHA256SUMS",
        "release-candidate.json",
        "recovery-envelope.json",
        *(item["name"] for item in manifest.get("artifacts", [])),
        *(reference["name"] for reference in references),
    }
    actual_files = {path.name for path in directory.iterdir() if path.is_file()}
    if actual_files != expected_files:
        raise ChainError("release evidence contains unreferenced or missing files")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    try:
        verify(parser.parse_args().directory)
    except (ChainError, KeyError, OSError, ValueError) as error:
        print(f"release-chain verification failed: {error}")
        return 1
    print("release-chain verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
