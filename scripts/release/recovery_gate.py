#!/usr/bin/env python3
"""Fail-closed recovery gate for reusing a Release Please draft."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Mapping


def verify(
    state: Mapping[str, Any],
    *,
    release_id: int,
    tag: str,
    source_sha: str,
    candidate: Mapping[str, Any],
) -> None:
    if int(state.get("id", -1)) != release_id:
        raise ValueError("recovery release ID mismatch")
    if state.get("tagName") != tag or state.get("isDraft") is not True or state.get("publishedAt"):
        raise ValueError("recovery requires the existing Release Please draft")
    if candidate.get("tag") != tag or candidate.get("commit") != source_sha:
        raise ValueError("recovery candidate is not bound to the requested tag/commit")
    if candidate.get("source_branch") != "dev":
        raise ValueError("recovery candidate source branch is not dev")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--release-id", type=int, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    args = parser.parse_args()
    try:
        state = json.loads(args.state.read_text(encoding="utf-8"))
        candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
        if not isinstance(state, dict) or not isinstance(candidate, dict):
            raise ValueError("recovery inputs must be objects")
        verify(
            state,
            release_id=args.release_id,
            tag=args.tag,
            source_sha=args.source_sha,
            candidate=candidate,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"recovery gate failed: {error}")
        return 1
    print("recovery gate passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
