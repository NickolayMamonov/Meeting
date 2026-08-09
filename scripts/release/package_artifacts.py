#!/usr/bin/env python3
"""Create deterministic Android release evidence in an acyclic hierarchy."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path
from typing import Any

from release_evidence import canonical_json, sha256_bytes


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, value: Any) -> bytes:
    data = canonical_json(value)
    path.write_bytes(data)
    return data


def _digest_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _artifact(path: Path, artifact_type: str) -> dict[str, Any]:
    return {
        "name": path.name,
        "type": artifact_type,
        "size": path.stat().st_size,
        "sha256": _digest_file(path),
    }


def package(args: argparse.Namespace) -> None:
    out = Path(args.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    metadata = _read_json(Path(args.metadata))
    for owned in (
        "release-authority.json",
        "snapshot-manifest.json",
        "release-manifest.json",
        "SHA256SUMS",
        "release-candidate.json",
        "recovery-envelope.json",
    ):
        (out / owned).unlink(missing_ok=True)
    commit = args.commit or metadata.get("commit", metadata.get("commitSha"))
    source_branch = args.source_branch or metadata.get("source_branch", metadata.get("sourceBranch", "dev"))
    workflow = args.workflow or metadata.get("workflow", "local")
    signing_fingerprint = metadata.get(
        "signing_fingerprint",
        metadata.get("signingFingerprint", metadata.get("expectedCertificateSha256")),
    )
    release_host = metadata.get("release_host", metadata.get("releaseHost"))
    pin_values = metadata.get("spki_pin_digests", metadata.get("spkiPins", []))
    application_id = metadata.get("application_id", metadata.get("applicationId"))
    version_name = metadata.get("version_name", metadata.get("versionName"))
    version_code = metadata.get("version_code", metadata.get("versionCode"))
    variant = metadata.get("variant")
    authority = {
        "schema": 1,
        "kind": "release-authority",
        "channel": metadata["channel"],
        "tag": args.tag or metadata.get("tag"),
        "commit": commit,
        "source_branch": source_branch,
        "workflow": workflow,
    }
    authority_path = out / "release-authority.json"
    authority_bytes = _write_json(authority_path, authority)

    outputs: list[dict[str, Any]] = []
    for source, artifact_type in (
        (args.apk, "apk"),
        (args.aab, "aab"),
        (args.mapping, "mapping"),
        (args.symbols, "native-symbols"),
    ):
        if not source:
            continue
        source_path = Path(source)
        if not source_path.is_file():
            raise SystemExit(f"missing declared artifact: {source}")
        target = out / source_path.name
        target.unlink(missing_ok=True)
        shutil.copyfile(source_path, target)
        outputs.append(_artifact(target, artifact_type))
    required_types = {"apk"} if metadata["channel"] == "snapshot" else {"apk", "aab"}
    if required_types - {item["type"] for item in outputs}:
        raise SystemExit("required distributable artifact is missing")

    optional = {
        "mapping": any(item["type"] == "mapping" for item in outputs),
        "native-symbols": any(item["type"] == "native-symbols" for item in outputs),
    }
    manifest = {
        "schema": 1,
        "channel": metadata["channel"],
        "tag": args.tag or metadata.get("tag"),
        "commit": commit,
        "source_branch": source_branch,
        "application_id": application_id,
        "version_name": version_name,
        "version_code": version_code,
        "variant": variant,
        "toolchain": metadata.get("toolchain", {}),
        "release_host": release_host,
        "spki_pin_digests": pin_values,
        "signing_fingerprint": signing_fingerprint,
        "workflow": workflow,
        "authority": {
            "name": authority_path.name,
            "sha256": sha256_bytes(authority_bytes),
        },
        "optional_outputs": {
            name: {"produced": value}
            for name, value in sorted(optional.items())
        },
        "artifacts": sorted(outputs, key=lambda item: item["name"]),
    }
    manifest_path = out / (
        "snapshot-manifest.json" if metadata["channel"] == "snapshot" else "release-manifest.json"
    )
    manifest_bytes = _write_json(manifest_path, manifest)

    checksum_items = [
        (item["sha256"], item["name"])
        for item in sorted(outputs, key=lambda item: item["name"])
    ]
    checksum_items.append((sha256_bytes(authority_bytes), authority_path.name))
    checksum_items.append((sha256_bytes(manifest_bytes), manifest_path.name))
    checksum_path = out / "SHA256SUMS"
    checksum_path.write_text(
        "".join(f"{digest}  {name}\n" for digest, name in sorted(checksum_items, key=lambda item: item[1])),
        encoding="utf-8",
    )
    checksum_digest = _digest_file(checksum_path)

    candidate = {
        "schema": 1,
        "kind": "release-candidate",
        "manifest": {"name": manifest_path.name, "sha256": sha256_bytes(manifest_bytes)},
        "checksums": {"name": checksum_path.name, "sha256": checksum_digest},
        "distributable_digests": [
            {
                "name": item["name"],
                "sha256": item["sha256"],
                "split_digest": sha256_bytes(
                    b"release-candidate-split\x00" + item["sha256"].encode("ascii")
                ),
            }
            for item in sorted(outputs, key=lambda item: item["name"])
        ],
    }
    candidate_path = out / "release-candidate.json"
    candidate_bytes = _write_json(candidate_path, candidate)

    attestations = []
    attested_subjects = [
        *sorted(outputs, key=lambda item: item["name"]),
        _artifact(manifest_path, "manifest"),
        _artifact(checksum_path, "checksums"),
        _artifact(candidate_path, "candidate"),
    ]
    for item in attested_subjects:
        attestation = {
            "schema": 1,
            "kind": "individual-attestation",
            "subject": item,
            "bundle_identity": hashlib.sha256(
                b"bundle\x00" + item["sha256"].encode("ascii")
            ).hexdigest(),
            "statement_identity": hashlib.sha256(
                b"statement\x00" + item["name"].encode("utf-8") + b"\x00" + item["sha256"].encode("ascii")
            ).hexdigest(),
            "certificate_identity": metadata.get(
                "certificate_identity",
                metadata.get("signing_fingerprint", metadata.get("signingFingerprint")),
            ),
            "rekor_identity": metadata.get("rekor_identity", "local-unpublished"),
        }
        attestation_path = out / f"{item['name']}.attestation.json"
        attestation_path.unlink(missing_ok=True)
        attestation_bytes = _write_json(attestation_path, attestation)
        attestations.append({
            "name": attestation_path.name,
            "sha256": sha256_bytes(attestation_bytes),
            "bundle_identity": attestation["bundle_identity"],
            "statement_identity": attestation["statement_identity"],
        })

    envelope = {
        "schema": 1,
        "kind": "recovery-envelope",
        "candidate": {"name": candidate_path.name, "sha256": sha256_bytes(candidate_bytes)},
        "attestations": attestations,
        "authority": {"name": authority_path.name, "sha256": sha256_bytes(authority_bytes)},
        "excluded_from_coverage": ["release-candidate.json", "recovery-envelope.json"],
    }
    _write_json(out / "recovery-envelope.json", envelope)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--tag")
    parser.add_argument("--commit")
    parser.add_argument("--source-branch")
    parser.add_argument("--workflow")
    parser.add_argument("--aab")
    parser.add_argument("--mapping")
    parser.add_argument("--symbols")
    package(parser.parse_args())


if __name__ == "__main__":
    main()
