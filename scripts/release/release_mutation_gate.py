#!/usr/bin/env python3
"""Pure preflight checks for the one GitHub Release mutation boundary."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any, Mapping

from release_evidence import EvidenceError, verify_attestation_link


class MutationError(ValueError):
    pass


FIXED_RELEASE_FILENAMES = frozenset(
    {
        "release-authority.json",
        "release-manifest.json",
        "SHA256SUMS",
        "release-candidate.json",
        "attestation-index.json",
    }
)
RELEASE_ARTIFACT_TYPES = frozenset({"apk", "aab", "mapping", "native-symbols"})
PUBLIC_RELEASE_ASSET_NAMES = frozenset({"Meet.apk"})
MAX_RELEASE_ASSET_BYTES = 512 * 1024 * 1024
_COMMIT_SHA = re.compile(r"[0-9a-f]{40}\Z")
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise MutationError(message)


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_object(path: Path, description: str) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    _require(isinstance(value, dict), f"{description} is malformed")
    return value


def _require_sha256(value: Any, description: str) -> str:
    _require(isinstance(value, str) and _SHA256.fullmatch(value) is not None,
             f"{description} is invalid")
    return value


def _require_reference(
    value: Any,
    *,
    name: str,
    digest: str,
    description: str,
) -> None:
    _require(isinstance(value, Mapping), f"{description} is malformed")
    _require(value.get("name") == name, f"{description} name changed")
    _require(
        _require_sha256(value.get("sha256"), f"{description} digest") == digest,
        f"{description} digest changed",
    )


def _validate_attestation_index(
    directory: Path,
    *,
    index: Mapping[str, Any],
    authority_path: Path,
    candidate_path: Path,
    manifest_path: Path,
    checksums_path: Path,
    artifact_names: set[str],
) -> None:
    _require(
        index.get("schema") == 1
        and index.get("kind") == "attestation-index"
        and index.get("channel") == "release",
        "attestation index schema or kind is invalid",
    )
    _require_reference(
        index.get("candidate"),
        name=candidate_path.name,
        digest=_file_sha256(candidate_path),
        description="attestation index candidate reference",
    )
    _require_reference(
        index.get("authority"),
        name=authority_path.name,
        digest=_file_sha256(authority_path),
        description="attestation index authority reference",
    )
    _require(
        index.get("excluded_from_coverage")
        == ["release-candidate.json", "attestation-index.json"],
        "attestation index coverage exclusions are invalid",
    )
    references = index.get("attestations")
    _require(
        isinstance(references, list) and references,
        "attestation index references are missing",
    )

    expected_subjects = {
        authority_path.name,
        manifest_path.name,
        checksums_path.name,
        candidate_path.name,
        *artifact_names,
    }
    covered_subjects: set[str] = set()
    reference_names: set[str] = set()
    for reference in references:
        _require(isinstance(reference, Mapping), "attestation index reference is malformed")
        name = reference.get("name")
        _require(
            isinstance(name, str)
            and Path(name).name == name
            and name.endswith(".attestation.json"),
            "attestation index attestation name is invalid",
        )
        _require(name not in reference_names, "attestation index has duplicate attestations")
        reference_names.add(name)
        attestation_path = directory / name
        _require(attestation_path.is_file(), f"attestation evidence is missing for {name}")
        _require(
            _require_sha256(
                reference.get("sha256"),
                f"attestation index reference {name} digest",
            )
            == _file_sha256(attestation_path),
            f"attestation index reference digest changed for {name}",
        )
        attestation = _read_object(attestation_path, f"attestation {name}")
        _require(
            attestation.get("schema") == 1
            and attestation.get("kind") == "individual-attestation",
            f"attestation {name} schema or kind is invalid",
        )
        subject = attestation.get("subject")
        _require(isinstance(subject, Mapping), f"attestation {name} subject is malformed")
        subject_name = subject.get("name")
        _require(
            isinstance(subject_name, str)
            and Path(subject_name).name == subject_name,
            f"attestation {name} subject name is invalid",
        )
        _require(subject_name in expected_subjects, f"attestation {name} subject is unknown")
        _require(subject_name not in covered_subjects,
                 f"attestation coverage is duplicated for {subject_name}")
        subject_path = directory / subject_name
        _require(subject_path.is_file(), f"attestation subject is missing for {name}")
        subject_digest = _require_sha256(
            subject.get("sha256"),
            f"attestation {name} subject digest",
        )
        _require(subject_digest == _file_sha256(subject_path),
                 f"attestation subject digest changed for {name}")
        covered_subjects.add(subject_name)
        try:
            linked = verify_attestation_link(
                attestation["producer"],
                attestation["authoritative"],
            )
        except (EvidenceError, KeyError, TypeError, ValueError) as error:
            raise MutationError(f"attestation identity is invalid for {name}") from error
        for identity_name, identity_value in linked.items():
            _require(
                attestation.get(identity_name) == identity_value
                and reference.get(identity_name) == identity_value,
                f"attestation {identity_name} reference changed for {name}",
            )
    _require(covered_subjects == expected_subjects, "attestation coverage is not exact")


def admit_release_evidence(
    directory: Path,
    *,
    tag: str,
    source_sha: str,
    expected_source_branch: str | None = None,
) -> dict[str, Any]:
    """Validate the complete protected release evidence chain before mutation."""
    manifest_path = directory / "release-manifest.json"
    index_path = directory / "attestation-index.json"
    candidate_path = directory / "release-candidate.json"
    checksums_path = directory / "SHA256SUMS"
    manifest = _read_object(manifest_path, "release manifest")
    candidate = _read_object(candidate_path, "release candidate")
    index = _read_object(index_path, "attestation index")
    _require(_COMMIT_SHA.fullmatch(source_sha) is not None, "expected release source commit is invalid")
    _require(isinstance(tag, str) and tag, "expected release tag is invalid")
    authority_path = directory / "release-authority.json"
    authority = _read_object(authority_path, "release authority")
    source_branch = manifest.get("source_branch")
    _require(isinstance(source_branch, str) and source_branch, "release source branch is missing")
    if expected_source_branch is not None:
        _require(
            isinstance(expected_source_branch, str) and expected_source_branch,
            "expected source branch is invalid",
        )
        _require(source_branch == expected_source_branch, "release source branch changed")
    _require(
        authority.get("schema") == 1
        and authority.get("kind") == "release-authority"
        and authority.get("channel") == "release"
        and authority.get("tag") == tag
        and authority.get("commit") == source_sha
        and authority.get("source_branch") == source_branch,
        "release authority identity changed",
    )
    _require(
        manifest.get("schema") == 1
        and manifest.get("channel") == "release"
        and manifest.get("tag") == tag
        and manifest.get("commit") == source_sha
        and manifest.get("source_branch") == source_branch,
        "release manifest identity changed",
    )
    _require_reference(
        manifest.get("authority"),
        name=authority_path.name,
        digest=_file_sha256(authority_path),
        description="release manifest authority reference",
    )
    _require(
        candidate.get("schema") == 1
        and candidate.get("kind") == "release-candidate"
        and candidate.get("channel") == "release"
        and candidate.get("tag") == tag
        and candidate.get("commit") == source_sha
        and candidate.get("source_branch") == source_branch,
        "release candidate identity changed",
    )
    _require_reference(
        candidate.get("manifest"),
        name=manifest_path.name,
        digest=_file_sha256(manifest_path),
        description="release candidate manifest reference",
    )
    _require_reference(
        candidate.get("checksums"),
        name=checksums_path.name,
        digest=_file_sha256(checksums_path),
        description="release candidate checksum reference",
    )
    artifacts = manifest.get("artifacts")
    _require(isinstance(artifacts, list) and artifacts, "release manifest artifacts are missing")
    artifact_names: list[str] = []
    for artifact in artifacts:
        _require(isinstance(artifact, Mapping), "release manifest artifact is malformed")
        name = artifact.get("name")
        artifact_type = artifact.get("type")
        _require(
            isinstance(name, str) and Path(name).name == name and name,
            "release manifest artifact name is invalid",
        )
        _require(artifact_type in RELEASE_ARTIFACT_TYPES, "release manifest artifact type is invalid")
        _require_sha256(
            artifact.get("sha256"),
            f"release manifest artifact {name} digest",
        )
        artifact_size = artifact.get("size")
        _require(
            isinstance(artifact_size, int)
            and not isinstance(artifact_size, bool)
            and 0 < artifact_size <= MAX_RELEASE_ASSET_BYTES,
            f"release manifest artifact {name} size is invalid",
        )
        _require(name not in artifact_names, "release manifest contains duplicate artifacts")
        artifact_names.append(name)
    apk_artifacts = [item for item in artifacts if item["type"] == "apk"]
    aab_artifacts = [item for item in artifacts if item["type"] == "aab"]
    _require(len(apk_artifacts) == 1 and apk_artifacts[0]["name"] == "Meet.apk",
             "release manifest must contain exactly Meet.apk")
    _require(len(aab_artifacts) == 1, "release manifest must contain exactly one AAB")
    apk_path = directory / "Meet.apk"
    _require(apk_path.is_file() and apk_path.stat().st_size > 0, "Meet.apk is missing")
    local_size = apk_path.stat().st_size
    _require(local_size <= MAX_RELEASE_ASSET_BYTES, "Meet.apk exceeds the configured size cap")
    manifest_size = apk_artifacts[0].get("size")
    _require(
        isinstance(manifest_size, int)
        and not isinstance(manifest_size, bool)
        and 0 < manifest_size <= MAX_RELEASE_ASSET_BYTES,
        "Meet.apk manifest size is invalid",
    )
    _require(manifest_size == local_size, "Meet.apk size does not match manifest")
    digest = hashlib.sha256()
    with apk_path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    _require(digest.hexdigest() == apk_artifacts[0].get("sha256"),
             "Meet.apk digest does not match manifest")
    _validate_attestation_index(
        directory,
        index=index,
        authority_path=authority_path,
        candidate_path=candidate_path,
        manifest_path=manifest_path,
        checksums_path=checksums_path,
        artifact_names=set(artifact_names),
    )
    expected_candidate_digests = [
        {
            "name": item["name"],
            "sha256": item["sha256"],
            "split_digest": hashlib.sha256(
                b"release-candidate-split\x00" + item["sha256"].encode("ascii")
            ).hexdigest(),
        }
        for item in sorted(artifacts, key=lambda item: item["name"])
    ]
    _require(
        candidate.get("distributable_digests") == expected_candidate_digests,
        "release candidate distributable references changed",
    )
    attestation_names = [reference["name"] for reference in index["attestations"]]
    expected = set(FIXED_RELEASE_FILENAMES) | set(artifact_names) | set(attestation_names)
    actual = {path.name for path in directory.iterdir() if path.is_file()}
    _require(actual == expected, "release output contains unreferenced or missing files")
    return manifest


def validated_source_branch(directory: Path, *, tag: str, source_sha: str) -> str:
    """Return the branch from an admitted release fixture's provenance."""

    manifest = admit_release_evidence(directory, tag=tag, source_sha=source_sha)
    source_branch = manifest.get("source_branch")
    _require(isinstance(source_branch, str) and source_branch, "release source branch is missing")
    return source_branch


def expected_release_asset_names(
    directory: Path,
    *,
    tag: str,
    source_sha: str,
    expected_source_branch: str,
) -> set[str]:
    """Validate protected evidence and return the fixed public projection."""

    admit_release_evidence(
        directory,
        tag=tag,
        source_sha=source_sha,
        expected_source_branch=expected_source_branch,
    )
    return set(PUBLIC_RELEASE_ASSET_NAMES)


def verify_release_state(
    payload: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    allowed_names: set[str],
    source_sha: str | None = None,
    require_empty: bool = True,
) -> None:
    _require(isinstance(release_id, int) and not isinstance(release_id, bool) and release_id > 0,
             "release ID must be positive")
    _require(
        isinstance(payload.get("id"), int)
        and not isinstance(payload.get("id"), bool)
        and payload["id"] == release_id,
        "release ID changed",
    )
    _require(payload.get("tag_name") == tag, "release tag changed")
    _require(
        "target_commitish" in payload
        and "draft" in payload
        and "published_at" in payload
        and "name" in payload
        and "body" in payload
        and "prerelease" in payload,
        "release state fields are incomplete",
    )
    _require(isinstance(payload.get("target_commitish"), str) and payload["target_commitish"],
             "release source is missing")
    if source_sha is not None:
        _require(payload.get("target_commitish") == source_sha, "release source changed")
    _require(isinstance(payload.get("name"), str) and payload["name"], "release name is missing")
    _require(isinstance(payload.get("draft"), bool), "release draft state is malformed")
    _require(payload.get("draft") is True, "release is not a draft")
    _require(payload.get("published_at") is None, "release is already published")
    _require(isinstance(payload.get("body"), str), "release body is malformed")
    _require(isinstance(payload.get("prerelease"), bool), "release prerelease state is malformed")
    _require("assets" in payload, "release assets field is missing")
    assets = payload["assets"]
    _require(isinstance(assets, list), "release assets are malformed")
    if require_empty:
        _require(not assets, "release draft must be initially empty")
    names = [asset.get("name") for asset in assets if isinstance(asset, Mapping)]
    _require(len(names) == len(assets), "release asset is malformed")
    _require(all(isinstance(name, str) and name for name in names), "release asset name is malformed")
    _require(len(names) == len(set(names)), "release contains duplicate asset names")
    _require(set(names) <= allowed_names, "release contains an unknown asset")


def verify_uploaded_assets(
    payload: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    expected_names: set[str],
    source_sha: str | None = None,
    expected_size: int | None = None,
    expected_sha256: str | None = None,
) -> None:
    verify_release_state(
        payload,
        release_id=release_id,
        tag=tag,
        allowed_names=expected_names,
        source_sha=source_sha,
        require_empty=False,
    )
    assets = payload["assets"]
    _require(len(assets) == 1, "uploaded release must contain exactly one asset")
    _require(isinstance(assets[0], Mapping), "uploaded release asset is malformed")
    _require(
        isinstance(assets[0].get("id"), int)
        and not isinstance(assets[0]["id"], bool)
        and assets[0]["id"] > 0,
        "uploaded release asset ID is invalid",
    )
    names = {str(asset.get("name", "")) for asset in assets}
    _require(names == expected_names, "uploaded release assets are not exact")
    asset = assets[0]
    _require(
        isinstance(asset.get("size"), int)
        and not isinstance(asset["size"], bool)
        and 0 < asset["size"] <= MAX_RELEASE_ASSET_BYTES,
        "uploaded release asset size is invalid",
    )
    if expected_size is not None:
        _require(
            isinstance(expected_size, int) and not isinstance(expected_size, bool)
            and 0 < expected_size <= MAX_RELEASE_ASSET_BYTES,
            "expected release asset size is invalid",
        )
        _require(asset.get("size") == expected_size, "uploaded release asset size is not exact")
    if expected_sha256 is not None:
        _require(
            isinstance(expected_sha256, str)
            and len(expected_sha256) == 64
            and all(character in "0123456789abcdef" for character in expected_sha256),
            "expected release asset digest is invalid",
        )
        reported = asset.get("digest")
        _require(
            reported == f"sha256:{expected_sha256}",
            "uploaded release asset digest is not exact",
        )


def verify_public_release_state(
    payload: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    source_sha: str,
    body: str,
    name: str | None = None,
    prerelease: bool | None = None,
) -> None:
    _require(
        isinstance(payload.get("id"), int)
        and not isinstance(payload.get("id"), bool)
        and payload["id"] == release_id,
        "published release ID changed",
    )
    _require(payload.get("tag_name") == tag, "published release tag changed")
    _require(payload.get("target_commitish") == source_sha,
             "published release source changed")
    _require(payload.get("draft") is False, "release is still a draft")
    _require(isinstance(payload.get("published_at"), str) and payload["published_at"],
             "release publication timestamp is missing")
    _require(payload.get("body") == body, "published release body changed")
    _require(isinstance(payload.get("name"), str) and payload["name"], "published release name is missing")
    _require(isinstance(payload.get("prerelease"), bool), "published release prerelease state is malformed")
    if name is not None:
        _require(payload["name"] == name, "published release name changed")
    if prerelease is not None:
        _require(payload["prerelease"] is prerelease, "published release prerelease state changed")
    assets = payload.get("assets")
    _require(isinstance(assets, list) and len(assets) == 1, "published release assets are not exact")
    _require(
        isinstance(assets[0], Mapping)
        and assets[0].get("name") == "Meet.apk"
        and len({asset.get("name") for asset in assets if isinstance(asset, Mapping)}) == len(assets),
        "published release assets are not unique",
    )
    asset = assets[0]
    _require(
        isinstance(asset, Mapping)
        and asset.get("name") == "Meet.apk"
        and isinstance(asset.get("id"), int)
        and not isinstance(asset.get("id"), bool)
        and asset["id"] > 0,
        "published release installer is not exact",
    )
    _require(
        isinstance(asset.get("size"), int)
        and not isinstance(asset["size"], bool)
        and 0 < asset["size"] <= MAX_RELEASE_ASSET_BYTES,
        "published release installer size is invalid",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--release-id", type=int, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--expected-name", action="append", required=True)
    args = parser.parse_args()
    try:
        payload = json.loads(args.state.read_text(encoding="utf-8"))
        if not isinstance(payload, dict):
            raise MutationError("release state must be an object")
        verify_release_state(
            payload,
            release_id=args.release_id,
            tag=args.tag,
            allowed_names=set(args.expected_name),
        )
    except (MutationError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"release mutation gate failed: {error}")
        return 1
    print("release mutation gate passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
