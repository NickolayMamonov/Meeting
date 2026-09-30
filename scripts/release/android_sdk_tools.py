#!/usr/bin/env python3
"""Deterministically resolve and validate Android SDK release tools."""

from __future__ import annotations

import argparse
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
_PACKAGE_PATH = re.compile(r"Pkg\.Path\s*=\s*([^\s]+)\Z")
_APKANALYZER_PROBE_TIMEOUT_SECONDS = 30
_APKANALYZER_USAGE = re.compile(
    r"(?m)^Usage:\r?\n"
    r"^apkanalyzer \[global options\] <subject> <verb> \[options\] <apk> \[<apk2>\]\r?$"
)


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
    try:
        path = Path(value).resolve()
    except (OSError, RuntimeError) as error:
        raise AndroidSdkToolError(f"{label} cannot be canonicalized") from error
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


def _contained_path(path: Path, roots: tuple[Path, ...], label: str) -> Path:
    try:
        canonical = path.resolve()
    except (OSError, RuntimeError) as error:
        raise AndroidSdkToolError(f"{label} cannot be canonicalized") from error
    for root in roots:
        try:
            canonical.relative_to(root)
        except ValueError as error:
            raise AndroidSdkToolError(f"{label} escapes its SDK package") from error
    return canonical


def _package_revision(
    directory: Path,
    package_name: str = "build-tools",
    containment_roots: tuple[Path, ...] = (),
    require_matching_path: bool = False,
) -> tuple[int, ...]:
    properties = _contained_path(
        directory / "source.properties",
        containment_roots + (directory.resolve(),),
        f"Android SDK {package_name} package metadata",
    )
    if not properties.is_file():
        raise AndroidSdkToolError(
            f"Android SDK {package_name} package metadata is not a regular file"
        )
    try:
        lines = properties.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as error:
        raise AndroidSdkToolError(
            f"Android SDK {package_name} package metadata is unreadable"
        ) from error
    matches = []
    path_matches = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("Pkg.Revision"):
            match = _PACKAGE_REVISION.fullmatch(stripped)
            if match is None:
                raise AndroidSdkToolError(
                    f"Android SDK {package_name} package revision is malformed"
                )
            raw_revision = match.group(1)
            matches.append((parse_revision(raw_revision), raw_revision))
        if require_matching_path and stripped.startswith("Pkg.Path"):
            match = _PACKAGE_PATH.fullmatch(stripped)
            if match is None:
                raise AndroidSdkToolError(
                    f"Android SDK {package_name} package path is malformed"
                )
            path_matches.append(match.group(1))
    if len(matches) != 1:
        raise AndroidSdkToolError(
            f"Android SDK {package_name} package revision is not unique"
        )
    normalized_revision, raw_revision = matches[0]
    if require_matching_path:
        if len(path_matches) != 1:
            raise AndroidSdkToolError(
                f"Android SDK {package_name} package path is not unique"
            )
        if path_matches[0] != f"{package_name};{raw_revision}":
            raise AndroidSdkToolError(
                f"Android SDK {package_name} package path does not match expected identity"
            )
    return normalized_revision


def _validated_executable(
    directory: Path,
    tool: str = "apksigner",
    relative_path: str | None = None,
    containment_roots: tuple[Path, ...] = (),
    require_executable: bool = True,
) -> Path:
    executable = _contained_path(
        directory / (relative_path or tool),
        containment_roots + (directory.resolve(),),
        tool,
    )
    if not executable.is_file() or (
        require_executable and not os.access(executable, os.X_OK)
    ):
        raise AndroidSdkToolError(f"{tool} is not a regular executable file")
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
    if _package_revision(directory, containment_roots=(root,)) != revision:
        raise AndroidSdkToolError("Android SDK build-tools package revision does not match directory")
    launcher = "apksigner.bat" if sys.platform == "win32" else "apksigner"
    executable = _validated_executable(
        directory,
        relative_path=launcher,
        containment_roots=(root,),
        require_executable=sys.platform != "win32",
    )
    _validate_tool_version(executable)
    return executable


def _selected_cmdline_tools(root: Path) -> tuple[tuple[int, ...], Path]:
    packages_root = root / "cmdline-tools"
    if not packages_root.is_dir():
        raise AndroidSdkToolError("Android SDK cmdline-tools directory is missing")
    try:
        canonical_root = packages_root.resolve()
    except (OSError, RuntimeError) as error:
        raise AndroidSdkToolError(
            "Android SDK cmdline-tools directory cannot be canonicalized"
        ) from error
    try:
        canonical_root.relative_to(root.resolve())
    except ValueError as error:
        raise AndroidSdkToolError(
            "Android SDK cmdline-tools directory escapes its SDK root"
        ) from error
    candidates = []
    for child in packages_root.iterdir():
        if child.is_dir():
            package = _contained_path(
                child,
                (canonical_root, root),
                "Android SDK cmdline-tools package",
            )
            if not package.is_dir():
                raise AndroidSdkToolError(
                    "Android SDK cmdline-tools package is not a regular directory"
                )
            candidates.append(
                (
                    _package_revision(
                        package,
                        "cmdline-tools",
                        containment_roots=(canonical_root, root),
                        require_matching_path=True,
                    ),
                    package,
                )
            )
    if not candidates:
        raise AndroidSdkToolError("no Android SDK cmdline-tools package")
    highest = max(revision for revision, _ in candidates)
    selected = [(revision, path) for revision, path in candidates if revision == highest]
    if len(selected) != 1:
        raise AndroidSdkToolError("highest Android SDK cmdline-tools version is ambiguous")
    return selected[0]


def _probe_apkanalyzer(executable: Path) -> None:
    try:
        result = subprocess.run(
            [str(executable)],
            check=False,
            capture_output=True,
            text=True,
            timeout=_APKANALYZER_PROBE_TIMEOUT_SECONDS,
        )
    except (OSError, UnicodeError, subprocess.TimeoutExpired) as error:
        raise AndroidSdkToolError("apkanalyzer identity probe could not run") from error
    if (
        result.returncode != 0
        or result.stdout != ""
        or not _APKANALYZER_USAGE.search(result.stderr)
    ):
        raise AndroidSdkToolError("apkanalyzer identity probe failed")


def resolve_apkanalyzer(environment: Mapping[str, str] | None = None) -> Path:
    try:
        root = sdk_root(environment)
        _, directory = _selected_cmdline_tools(root)
        launcher = "bin/apkanalyzer.bat" if sys.platform == "win32" else "bin/apkanalyzer"
        executable = _validated_executable(
            directory,
            "apkanalyzer",
            launcher,
            containment_roots=(
                _contained_path(
                    root / "cmdline-tools",
                    (root,),
                    "Android SDK cmdline-tools directory",
                ),
                root,
            ),
            require_executable=sys.platform != "win32",
        )
        _probe_apkanalyzer(executable)
        return executable
    except (OSError, UnicodeError) as error:
        raise AndroidSdkToolError("apkanalyzer resolution could not inspect the SDK") from error


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "tool", nargs="?", choices=("apksigner", "apkanalyzer"), default="apksigner"
    )
    args = parser.parse_args()
    try:
        resolver = resolve_apksigner if args.tool == "apksigner" else resolve_apkanalyzer
        print(resolver())
    except (AndroidSdkToolError, OSError, UnicodeError) as error:
        print(f"Android SDK {args.tool} resolution failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
