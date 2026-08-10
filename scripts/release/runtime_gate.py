#!/usr/bin/env python3
"""Require runtime evidence bound to the exact verified stable release."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any, Mapping


CERTIFICATE_PATTERN = re.compile(r"^[0-9a-fA-F]{64}$")
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _certificate(value: Any, field: str) -> str:
    if not isinstance(value, str) or not CERTIFICATE_PATTERN.fullmatch(value):
        raise ValueError(f"{field} fingerprint is invalid")
    return value.lower()


def _pins(value: Any, field: str) -> list[str]:
    if (
        not isinstance(value, list)
        or len(value) < 2
        or any(not isinstance(pin, str) or not pin.strip() for pin in value)
        or len(value) != len(set(value))
    ):
        raise ValueError(f"{field} must contain unique non-empty pins")
    return list(value)


def verify(
    evidence: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    source_sha: str,
    candidate: Mapping[str, Any],
    manifest: Mapping[str, Any],
    candidate_sha256: str,
    manifest_sha256: str,
) -> None:
    """Validate runtime claims against the exact candidate and stable manifest."""

    if evidence.get("reset_device_state") is True:
        raise ValueError("runtime gate may not reset authenticated device state")
    if evidence.get("release_id") != release_id:
        raise ValueError("runtime evidence release ID does not match the verified release")
    if evidence.get("tag") != tag or evidence.get("source_sha") != source_sha:
        raise ValueError("runtime evidence release identity does not match the verified release")
    if evidence.get("candidate_sha256") != candidate_sha256:
        raise ValueError("runtime evidence candidate digest does not match verified evidence")
    if evidence.get("manifest_sha256") != manifest_sha256:
        raise ValueError("runtime evidence manifest digest does not match verified evidence")
    if candidate.get("tag") != tag or candidate.get("commit") != source_sha:
        raise ValueError("verified candidate identity does not match the release")
    candidate_manifest = candidate.get("manifest")
    if (
        not isinstance(candidate_manifest, Mapping)
        or candidate_manifest.get("sha256") != manifest_sha256
    ):
        raise ValueError("verified candidate manifest digest is inconsistent")
    if (
        manifest.get("schema") != 1
        or manifest.get("channel") != "release"
        or manifest.get("tag") != tag
        or manifest.get("commit") != source_sha
        or manifest.get("source_branch") != "dev"
    ):
        raise ValueError("verified stable manifest identity is invalid")
    if evidence.get("firebase_package") != manifest.get("application_id"):
        raise ValueError("runtime Firebase package does not match the stable manifest")

    runtime_certificate = _certificate(
        evidence.get("signing_certificate"), "runtime signing certificate"
    )
    manifest_certificate = _certificate(
        manifest.get("signing_fingerprint"), "manifest signing certificate"
    )
    if runtime_certificate != manifest_certificate:
        raise ValueError("runtime signing certificate does not match stable artifact")

    runtime_pins = _pins(evidence.get("tls_spki"), "runtime TLS/SPKI evidence")
    manifest_pins = _pins(
        manifest.get("spki_pin_digests"), "stable manifest TLS/SPKI configuration"
    )
    if set(runtime_pins) != set(manifest_pins):
        raise ValueError("runtime TLS/SPKI pins do not match stable configuration")

    if not isinstance(evidence.get("backend_revision"), str) or not evidence["backend_revision"]:
        raise ValueError("runtime backend revision is missing")
    device = evidence.get("authenticated_device")
    if (
        not isinstance(device, Mapping)
        or not device.get("serial")
        or device.get("authenticated_before") is not True
        or device.get("authenticated_after") is not True
        or device.get("state_preserved") is not True
    ):
        raise ValueError("authenticated device evidence is missing or state was not preserved")
    install = evidence.get("runtime_install")
    if (
        not isinstance(install, Mapping)
        or install.get("package") != manifest.get("application_id")
        or install.get("installed") is not True
        or install.get("state_preserved") is not True
    ):
        raise ValueError("runtime install evidence is incomplete")
    if evidence.get("runtime_authenticated_api") is not True:
        raise ValueError("authenticated runtime API evidence is missing")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--release-id", type=int, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    args = parser.parse_args()
    try:
        evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
        candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        if not all(isinstance(value, dict) for value in (evidence, candidate, manifest)):
            raise ValueError("runtime gate inputs must be objects")
        if not SHA_PATTERN.fullmatch(args.source_sha):
            raise ValueError("source SHA must be lowercase hexadecimal")
        verify(
            evidence,
            release_id=args.release_id,
            tag=args.tag,
            source_sha=args.source_sha,
            candidate=candidate,
            manifest=manifest,
            candidate_sha256=_sha256(args.candidate),
            manifest_sha256=_sha256(args.manifest),
        )
    except (OSError, ValueError, json.JSONDecodeError, KeyError, TypeError) as error:
        print(f"runtime publication gate failed: {error}")
        return 1
    print("runtime publication gate passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
