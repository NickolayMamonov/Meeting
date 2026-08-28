#!/usr/bin/env python3
"""Verify local release-package references before upload or attestation."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping

from release_evidence import (
    EvidenceError,
    verify_attestation_group,
    verify_attestation_groups,
    verify_attestation_link,
)


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


def require_reference(
    value: Any,
    *,
    name: str,
    path: Path,
    description: str,
) -> None:
    if not isinstance(value, Mapping):
        raise ChainError(f"{description} is malformed")
    if value.get("name") != name:
        raise ChainError(f"{description} name mismatch")
    if value.get("sha256") != digest(path):
        raise ChainError(f"{description} digest mismatch")


def verify(directory: Path) -> None:
    manifest_name = "snapshot-manifest.json" if (directory / "snapshot-manifest.json").exists() else "release-manifest.json"
    manifest_path = directory / manifest_name
    manifest = read(manifest_path)
    authority_path = directory / "release-authority.json"
    authority = read(authority_path)
    channel = manifest.get("channel")
    if manifest.get("schema") != 1 or channel not in {"release", "snapshot"}:
        raise ChainError("manifest schema or channel is invalid")
    if (
        authority.get("schema") != 1
        or authority.get("kind") != "release-authority"
        or authority.get("channel") != channel
        or authority.get("tag") != manifest.get("tag")
        or authority.get("commit") != manifest.get("commit")
        or authority.get("source_branch") != manifest.get("source_branch")
    ):
        raise ChainError("release authority identity mismatch")
    checksums_path = directory / "SHA256SUMS"
    checksum_lines = checksums_path.read_text(encoding="utf-8").splitlines()
    checksum_names = [line.split("  ", 1)[1] for line in checksum_lines]
    if checksum_names != sorted(checksum_names):
        raise ChainError("SHA256SUMS names are not byte-order sorted")
    if any(name in checksum_names for name in ("release-candidate.json", "attestation-index.json")):
        raise ChainError("candidate/index must be excluded from checksums")
    for line in checksum_lines:
        expected, name = line.split("  ", 1)
        actual = digest(directory / name)
        if actual != expected:
            raise ChainError(f"checksum mismatch for {name}")
    require_reference(
        manifest.get("authority"),
        name=authority_path.name,
        path=authority_path,
        description="manifest authority reference",
    )
    for item in manifest.get("artifacts", []):
        artifact = directory / item["name"]
        if not artifact.is_file() or digest(artifact) != item["sha256"] or artifact.stat().st_size != item["size"]:
            raise ChainError(f"manifest artifact mismatch for {item['name']}")
    candidate = read(directory / "release-candidate.json")
    if (
        candidate.get("schema") != 1
        or candidate.get("kind") != "release-candidate"
        or candidate.get("channel") != channel
        or candidate.get("tag") != manifest.get("tag")
        or candidate.get("commit") != manifest.get("commit")
        or candidate.get("source_branch") != manifest.get("source_branch")
    ):
        raise ChainError("release candidate schema or identity mismatch")
    require_reference(
        candidate.get("manifest"),
        name=manifest_path.name,
        path=manifest_path,
        description="candidate manifest reference",
    )
    require_reference(
        candidate.get("checksums"),
        name=checksums_path.name,
        path=checksums_path,
        description="candidate checksum reference",
    )
    index = read(directory / "attestation-index.json")
    if (
        index.get("schema") != 1
        or index.get("kind") != "attestation-index"
        or index.get("channel") != channel
    ):
        raise ChainError("attestation index schema, kind, or channel is invalid")
    require_reference(
        index.get("candidate"),
        name="release-candidate.json",
        path=directory / "release-candidate.json",
        description="attestation index candidate reference",
    )
    require_reference(
        index.get("authority"),
        name=authority_path.name,
        path=authority_path,
        description="attestation index authority reference",
    )
    if index.get("excluded_from_coverage") != ["release-candidate.json", "attestation-index.json"]:
        raise ChainError("attestation index is not excluded from coverage")
    references = index.get("attestations", [])
    if not isinstance(references, list):
        raise ChainError("attestation references are not a list")
    if not all(isinstance(reference, Mapping) for reference in references):
        raise ChainError("attestation reference is malformed")
    reference_names = [reference["name"] for reference in references]
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
    identities: set[str] = set()
    group_records: list[dict] = []
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
        has_attestation_group = "attestation_group" in attestation
        has_reference_group = "attestation_group" in reference
        attestation_group = attestation.get("attestation_group")
        reference_group = reference.get("attestation_group")
        if has_attestation_group != has_reference_group:
            raise ChainError(f"attestation/reference group presence mismatch for {attestation_path.name}")
        if has_attestation_group:
            if attestation_group is None or reference_group is None:
                raise ChainError(f"attestation group is null for {attestation_path.name}")
            if attestation_group != reference_group:
                raise ChainError(f"attestation/reference group mismatch for {attestation_path.name}")
            try:
                producer_group = verify_attestation_group(
                    attestation_group,
                    attestation["producer"]["bundle"],
                    attestation["producer"]["statement"],
                    attestation["producer"]["certificate"],
                    attestation["producer"]["rekor"],
                )
                authoritative_group = verify_attestation_group(
                    attestation_group,
                    attestation["authoritative"]["bundle"],
                    attestation["authoritative"]["statement"],
                    attestation["authoritative"]["certificate"],
                    attestation["authoritative"]["rekor"],
                )
            except (EvidenceError, KeyError, TypeError, ValueError) as error:
                raise ChainError(
                    f"invalid attestation group for {attestation_path.name}: {error}"
                ) from error
            if producer_group != authoritative_group:
                raise ChainError(f"producer/authoritative group mismatch for {attestation_path.name}")
        if linked["canonical_bundle_sha256"] in canonical_bundles:
            raise ChainError("duplicate canonical attestation bundle")
        if linked["statement_sha256"] in statements:
            raise ChainError("duplicate canonical attestation statement")
        if linked["attestation_identity"] in identities:
            raise ChainError("duplicate canonical attestation identity")
        canonical_bundles.add(linked["canonical_bundle_sha256"])
        statements.add(linked["statement_sha256"])
        identities.add(linked["attestation_identity"])
        group_records.append({
            "subject": {
                "name": subject["name"],
                "sha256": subject["sha256"],
            },
            "rekor_identity": linked["rekor_identity"],
            "attestation_group": attestation_group,
        })
    try:
        verify_attestation_groups(group_records)
    except (EvidenceError, KeyError, TypeError, ValueError) as error:
        raise ChainError(f"invalid attestation group cardinality: {error}") from error
    if covered_subjects != expected_subjects:
        raise ChainError("attestation coverage is not exact")
    expected_files = {
        "release-authority.json",
        manifest_path.name,
        "SHA256SUMS",
        "release-candidate.json",
        "attestation-index.json",
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
