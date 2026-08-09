#!/usr/bin/env python3
"""Verify local release-package references before upload or attestation."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


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
    if "recovery-envelope.json" not in envelope.get("excluded_from_coverage", []):
        raise ChainError("recovery envelope is not excluded from coverage")
    for reference in envelope.get("attestations", []):
        attestation_path = directory / reference["name"]
        attestation = read(attestation_path)
        if reference.get("sha256") != digest(attestation_path):
            raise ChainError(f"attestation digest mismatch for {attestation_path.name}")
        subject = attestation.get("subject", {})
        subject_path = directory / subject["name"]
        if digest(subject_path) != subject.get("sha256"):
            raise ChainError(f"attestation subject mismatch for {subject_path.name}")


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
