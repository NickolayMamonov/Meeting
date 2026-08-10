#!/usr/bin/env python3
"""Verify a release's remote assets against the exact local package output."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


class AssetError(ValueError):
    pass


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def verify(local_directory: Path, remote_json: Path, remote_directory: Path) -> None:
    local = {
        path.name: {"size": path.stat().st_size, "sha256": digest(path)}
        for path in local_directory.iterdir()
        if path.is_file()
    }
    payload = json.loads(remote_json.read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        assets = payload.get("assets", [])
    elif isinstance(payload, list):
        assets = payload
    else:
        assets = []
    if not isinstance(assets, list):
        raise AssetError("remote release assets must be a list")
    remote_names = [str(item.get("name", "")) for item in assets]
    if len(remote_names) != len(set(remote_names)):
        raise AssetError("remote release contains duplicate asset names")
    if set(remote_names) != set(local):
        raise AssetError("remote release asset names do not match the exact local allowlist")
    for item in assets:
        name = str(item["name"])
        path = remote_directory / name
        if not path.is_file():
            raise AssetError(f"remote asset was not downloaded: {name}")
        expected_size = int(item.get("size", -1))
        if path.stat().st_size != expected_size or path.stat().st_size != local[name]["size"]:
            raise AssetError(f"remote asset size mismatch: {name}")
        actual = digest(path)
        remote_digest = str(item.get("digest", "")).removeprefix("sha256:")
        if remote_digest and remote_digest != actual:
            raise AssetError(f"remote asset reported digest mismatch: {name}")
        if actual != local[name]["sha256"]:
            raise AssetError(f"remote asset SHA-256 mismatch: {name}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("local_directory", type=Path)
    parser.add_argument("remote_json", type=Path)
    parser.add_argument("remote_directory", type=Path)
    try:
        args = parser.parse_args()
        verify(args.local_directory, args.remote_json, args.remote_directory)
    except (AssetError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"remote asset verification failed: {error}")
        return 1
    print("remote asset verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
