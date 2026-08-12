#!/usr/bin/env python3
"""Deterministically resolve and validate the Android SDK apksigner tool."""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Mapping


class AndroidSdkToolError(ValueError):
    """Raised when the installed SDK does not satisfy the release contract."""


_REVISION = re.compile(r"[0-9]+(?:\.[0-9]+)*\Z")
_PACKAGE_REVISION = re.compile(r"Pkg\.Revision\s*=\s*([^\s]+)\Z")


def parse_revision(value: str) -> tuple[int, ...]:
    if _REVISION.fullmatch(value) is None:
        raise AndroidSdkToolError("version is not numeric and dotted")
    components = tuple(int(component, 10) for component in value.split("."))
    normalized = components
    while len(normalized) > 1 and normalized[-1] == 0:
        normalized = normalized[:-1]
    return normalized


def _canonical_directory(value: str, label: str) -> Path:
    if not value:
        raise AndroidSdkToolError(f"{label} is empty")
    path = Path(value).resolve()
    if not path.is_dir():
        raise AndroidSdkToolError(f"{label} is not a directory")
    return path


def sdk_root(environment: Mapping[str, str] | None = None) -> Path:
    environment = os.environ if environment is None else environment
    configured = [
        (name, environment.get(name))
        for name in ("ANDROID_SDK_ROOT", "ANDROID_HOME")
        if environment.get(name) is not None
    ]
    if not configured:
        raise AndroidSdkToolError("Android SDK root is not configured")
    roots = [_canonical_directory(value or "", name) for name, value in configured]
    if len(roots) == 2 and roots[0] != roots[1]:
        raise AndroidSdkToolError("Android SDK roots conflict")
    return roots[0]


def _numeric_candidates(build_tools: Path) -> list[tuple[tuple[int, ...], Path]]:
    if not build_tools.is_dir():
        raise AndroidSdkToolError("Android SDK build-tools directory is missing")
    candidates = []
    for child in build_tools.iterdir():
        if child.is_dir() and _REVISION.fullmatch(child.name):
            candidates.append((parse_revision(child.name), child))
    if not candidates:
        raise AndroidSdkToolError("no stable numeric Android SDK build-tools version")
    return candidates


def _selected_build_tools(root: Path) -> tuple[tuple[int, ...], Path]:
    candidates = _numeric_candidates(root / "build-tools")
    highest = max(revision for revision, _ in candidates)
    selected = [(revision, path) for revision, path in candidates if revision == highest]
    if len(selected) != 1:
        raise AndroidSdkToolError("highest Android SDK build-tools version is ambiguous")
    return selected[0]


def _package_revision(directory: Path) -> tuple[int, ...]:
    properties = directory / "source.properties"
    try:
        lines = properties.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as error:
        raise AndroidSdkToolError("Android SDK build-tools package metadata is unreadable") from error
    matches = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("Pkg.Revision"):
            match = _PACKAGE_REVISION.fullmatch(stripped)
            if match is None:
                raise AndroidSdkToolError("Android SDK build-tools package revision is malformed")
            matches.append(parse_revision(match.group(1)))
    if len(matches) != 1:
        raise AndroidSdkToolError("Android SDK build-tools package revision is not unique")
    return matches[0]


def _validated_executable(directory: Path) -> Path:
    executable = (directory / "apksigner").resolve()
    try:
        executable.relative_to(directory.resolve())
    except ValueError as error:
        raise AndroidSdkToolError("apksigner escapes its build-tools package") from error
    if not executable.is_file() or not os.access(executable, os.X_OK):
        raise AndroidSdkToolError("apksigner is not a regular executable file")
    return executable


def _validate_tool_version(executable: Path) -> None:
    try:
        result = subprocess.run(
            [str(executable), "version"],
            check=False,
            capture_output=True,
            text=True,
        )
    except (OSError, UnicodeError) as error:
        raise AndroidSdkToolError("apksigner version check could not run") from error
    if result.returncode != 0 or result.stderr != "":
        raise AndroidSdkToolError("apksigner version check failed")
    output = result.stdout[:-1] if result.stdout.endswith("\n") else result.stdout
    if _REVISION.fullmatch(output) is None:
        raise AndroidSdkToolError("apksigner version output is not one numeric dotted line")
    parse_revision(output)


def resolve_apksigner(environment: Mapping[str, str] | None = None) -> Path:
    root = sdk_root(environment)
    revision, directory = _selected_build_tools(root)
    if _package_revision(directory) != revision:
        raise AndroidSdkToolError("Android SDK build-tools package revision does not match directory")
    executable = _validated_executable(directory)
    _validate_tool_version(executable)
    return executable


def main() -> int:
    try:
        print(resolve_apksigner())
    except AndroidSdkToolError as error:
        print(f"Android SDK apksigner resolution failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
