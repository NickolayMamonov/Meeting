#!/usr/bin/env python3
"""Verify the one public Meet.apk against local and downloaded bytes."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping

from release_mutation_gate import MAX_RELEASE_ASSET_BYTES


class AssetError(ValueError):
    pass


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def _asset(payload: Any) -> Mapping[str, Any]:
    if not isinstance(payload, Mapping):
        raise AssetError("remote release state must be an object")
    assets = payload.get("assets")
    if not isinstance(assets, list) or len(assets) != 1:
        raise AssetError("remote release must contain exactly one asset")
    item = assets[0]
    if not isinstance(item, Mapping) or item.get("name") != "Meet.apk":
        raise AssetError("remote release asset must be Meet.apk")
    if isinstance(item.get("id"), bool) or not isinstance(item.get("id"), int) or item["id"] <= 0:
        raise AssetError("remote Meet.apk asset ID is invalid")
    return item


def verify(local_apk: Path, remote_json: Path, downloaded_apk: Path) -> None:
    if local_apk.name != "Meet.apk" or not local_apk.is_file():
        raise AssetError("local canonical installer must be Meet.apk")
    if not downloaded_apk.is_file() or downloaded_apk.name != "Meet.apk":
        raise AssetError("downloaded canonical installer must be Meet.apk")
    local_size = local_apk.stat().st_size
    if local_size <= 0 or local_size > MAX_RELEASE_ASSET_BYTES:
        raise AssetError("local Meet.apk size is outside the bounded range")
    item = _asset(json.loads(remote_json.read_text(encoding="utf-8")))
    try:
        expected_size = item["size"]
    except KeyError as error:
        raise AssetError("remote Meet.apk size is invalid") from error
    if (
        isinstance(expected_size, bool)
        or not isinstance(expected_size, int)
        or expected_size <= 0
        or expected_size > MAX_RELEASE_ASSET_BYTES
    ):
        raise AssetError("remote Meet.apk size is invalid")
    if expected_size != local_size or downloaded_apk.stat().st_size != expected_size:
        raise AssetError("remote Meet.apk size mismatch")
    actual = digest(downloaded_apk)
    local_digest = digest(local_apk)
    if actual != local_digest:
        raise AssetError("remote Meet.apk SHA-256 mismatch")
    reported = item.get("digest")
    if reported not in (None, ""):
        if not isinstance(reported, str) or not reported.startswith("sha256:"):
            raise AssetError("remote Meet.apk reported digest is malformed")
        if reported.removeprefix("sha256:") != actual:
            raise AssetError("remote Meet.apk reported digest mismatch")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("local_apk", type=Path)
    parser.add_argument("remote_json", type=Path)
    parser.add_argument("downloaded_apk", type=Path)
    try:
        verify(*vars(parser.parse_args()).values())
    except (AssetError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"remote asset verification failed: {error}")
        return 1
    print("remote asset verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
