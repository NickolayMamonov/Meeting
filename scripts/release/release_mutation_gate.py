#!/usr/bin/env python3
"""Pure preflight checks for the one GitHub Release mutation boundary."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any, Mapping


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


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise MutationError(message)


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def expected_release_asset_names(
    directory: Path,
    *,
    tag: str,
    source_sha: str,
    expected_source_branch: str,
) -> set[str]:
    """Validate protected evidence and return the fixed public projection."""

    manifest_path = directory / "release-manifest.json"
    index_path = directory / "attestation-index.json"
    candidate_path = directory / "release-candidate.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
    index = json.loads(index_path.read_text(encoding="utf-8"))
    _require(isinstance(manifest, Mapping), "release manifest is malformed")
    _require(isinstance(candidate, Mapping), "release candidate is malformed")
    _require(isinstance(index, Mapping), "attestation index is malformed")
    _require(_COMMIT_SHA.fullmatch(source_sha) is not None, "expected release source commit is invalid")
    _require(isinstance(expected_source_branch, str) and expected_source_branch, "expected source branch is invalid")
    authority_path = directory / "release-authority.json"
    authority = json.loads(authority_path.read_text(encoding="utf-8"))
    _require(isinstance(authority, Mapping), "release authority is malformed")
    _require(
        authority.get("schema") == 1
        and authority.get("kind") == "release-authority"
        and authority.get("channel") == "release"
        and authority.get("tag") == tag
        and authority.get("commit") == source_sha
        and authority.get("source_branch") == expected_source_branch,
        "release authority identity changed",
    )
    _require(manifest.get("schema") == 1 and manifest.get("channel") == "release",
             "release manifest schema is invalid")
    _require(manifest.get("tag") == tag and manifest.get("commit") == source_sha,
             "release manifest identity changed")
    _require(manifest.get("source_branch") == expected_source_branch,
             "release manifest source branch changed")
    _require(candidate.get("tag") == tag and candidate.get("commit") == source_sha,
             "release candidate identity changed")
    _require(candidate.get("source_branch") == expected_source_branch,
             "release candidate source branch changed")
    authority_reference = index.get("authority")
    _require(
        isinstance(authority_reference, Mapping)
        and authority_reference.get("name") == authority_path.name
        and authority_reference.get("sha256") == _file_sha256(authority_path),
        "attestation authority reference changed",
    )
    artifacts = manifest.get("artifacts")
    _require(isinstance(artifacts, list) and artifacts, "release manifest artifacts are missing")
    artifact_names: list[str] = []
    artifact_types: set[str] = set()
    for artifact in artifacts:
        _require(isinstance(artifact, Mapping), "release manifest artifact is malformed")
        name = artifact.get("name")
        artifact_type = artifact.get("type")
        _require(
            isinstance(name, str) and Path(name).name == name and name,
            "release manifest artifact name is invalid",
        )
        _require(artifact_type in RELEASE_ARTIFACT_TYPES, "release manifest artifact type is invalid")
        _require(name not in artifact_names, "release manifest contains duplicate artifacts")
        artifact_names.append(name)
        artifact_types.add(str(artifact_type))
    _require(artifact_types <= RELEASE_ARTIFACT_TYPES, "release manifest artifact type is invalid")
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
    references = index.get("attestations")
    _require(isinstance(references, list) and references, "attestation index references are missing")
    attestation_names: list[str] = []
    for reference in references:
        _require(isinstance(reference, Mapping), "attestation index reference is malformed")
        name = reference.get("name")
        _require(
            isinstance(name, str)
            and Path(name).name == name
            and name.endswith(".attestation.json"),
            "attestation index attestation name is invalid",
        )
        _require(name not in attestation_names, "attestation index has duplicate attestations")
        attestation_names.append(name)
    expected = set(FIXED_RELEASE_FILENAMES) | set(artifact_names) | set(attestation_names)
    actual = {path.name for path in directory.iterdir() if path.is_file()}
    _require(actual == expected, "release output contains unreferenced or missing files")
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
