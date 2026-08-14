#!/usr/bin/env python3
"""Pure preflight checks for the one GitHub Release mutation boundary."""

from __future__ import annotations

import argparse
import json
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


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise MutationError(message)


def expected_release_asset_names(
    directory: Path,
    *,
    tag: str,
    source_sha: str,
) -> set[str]:
    """Return the only release asset names permitted at the mutation boundary.

    The immutable manifest and attestation index are the authority for variable
    distributable and attestation names.  Files merely present in the
    directory never expand the allowlist.
    """

    manifest_path = directory / "release-manifest.json"
    index_path = directory / "attestation-index.json"
    candidate_path = directory / "release-candidate.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
    index = json.loads(index_path.read_text(encoding="utf-8"))
    _require(isinstance(manifest, Mapping), "release manifest is malformed")
    _require(isinstance(candidate, Mapping), "release candidate is malformed")
    _require(isinstance(index, Mapping), "attestation index is malformed")
    _require(manifest.get("schema") == 1 and manifest.get("channel") == "release",
             "release manifest schema is invalid")
    _require(manifest.get("tag") == tag and manifest.get("commit") == source_sha,
             "release manifest identity changed")
    _require(candidate.get("tag") == tag and candidate.get("commit") == source_sha,
             "release candidate identity changed")
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
    _require({"apk", "aab"} <= artifact_types, "release manifest lacks APK or AAB")
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
    return expected


def verify_release_state(
    payload: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    allowed_names: set[str],
    require_empty: bool = True,
) -> None:
    _require(int(payload.get("id", -1)) == release_id, "release ID changed")
    _require(payload.get("tagName") == tag, "release tag changed")
    _require(payload.get("isDraft") is True, "release is not a draft")
    _require(not payload.get("publishedAt"), "release is already published")
    assets = payload.get("assets", [])
    _require(isinstance(assets, list), "release assets are malformed")
    if require_empty:
        _require(not assets, "release draft must be initially empty")
    names = [str(asset.get("name", "")) for asset in assets]
    _require(len(names) == len(set(names)), "release contains duplicate asset names")
    _require(set(names) <= allowed_names, "release contains an unknown asset")


def verify_uploaded_assets(
    payload: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    expected_names: set[str],
) -> None:
    verify_release_state(
        payload,
        release_id=release_id,
        tag=tag,
        allowed_names=expected_names,
        require_empty=False,
    )
    names = {str(asset.get("name", "")) for asset in payload.get("assets", [])}
    _require(names == expected_names, "uploaded release assets are not exact")


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
