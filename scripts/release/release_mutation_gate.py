#!/usr/bin/env python3
"""Pure preflight checks for the one GitHub Release mutation boundary."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Mapping


class MutationError(ValueError):
    pass


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise MutationError(message)


def verify_release_state(
    payload: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    uploader: str,
    allowed_names: set[str],
    release_author: str | None = None,
) -> None:
    _require(int(payload.get("id", -1)) == release_id, "release ID changed")
    _require(payload.get("tagName") == tag, "release tag changed")
    _require(payload.get("isDraft") is True, "release is not a draft")
    _require(not payload.get("publishedAt"), "release is already published")
    author = payload.get("author", {})
    _require(isinstance(author, Mapping) and isinstance(author.get("login"), str), "release author is missing")
    if release_author is not None:
        _require(author.get("login") == release_author, "Release Please author changed")
    assets = payload.get("assets", [])
    _require(isinstance(assets, list), "release assets are malformed")
    names = [str(asset.get("name", "")) for asset in assets]
    _require(len(names) == len(set(names)), "release contains duplicate asset names")
    _require(set(names) <= allowed_names, "release contains an unknown asset")
    for asset in assets:
        asset_uploader = asset.get("uploader", {})
        _require(
            isinstance(asset_uploader, Mapping) and asset_uploader.get("login") == uploader,
            "existing pipeline asset has a different uploader",
        )


def verify_uploaded_assets(
    payload: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    uploader: str,
    expected_names: set[str],
    release_author: str | None = None,
) -> None:
    verify_release_state(
        payload,
        release_id=release_id,
        tag=tag,
        uploader=uploader,
        allowed_names=expected_names,
        release_author=release_author,
    )
    names = {str(asset.get("name", "")) for asset in payload.get("assets", [])}
    _require(names == expected_names, "uploaded release assets are not exact")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--release-id", type=int, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--uploader", required=True)
    parser.add_argument("--release-author", required=True)
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
            uploader=args.uploader,
            allowed_names=set(args.expected_name),
            release_author=args.release_author,
        )
    except (MutationError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"release mutation gate failed: {error}")
        return 1
    print("release mutation gate passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
