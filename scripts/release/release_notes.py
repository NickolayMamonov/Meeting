#!/usr/bin/env python3
"""Render the workflow-owned Android verification section in release notes."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Mapping


class ReleaseNotesError(ValueError):
    pass


START_MARKER = "<!-- meet-android-verification:start -->"
END_MARKER = "<!-- meet-android-verification:end -->"
_DIGEST = re.compile(r"^[0-9a-fA-F]{64}$")


def _digest(value: Any, field: str) -> str:
    if not isinstance(value, str) or not _DIGEST.fullmatch(value):
        raise ReleaseNotesError(f"{field} must be a SHA-256 digest")
    return value.lower()


def _manifest_values(manifest: Mapping[str, Any]) -> tuple[str, int, str, str]:
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise ReleaseNotesError("manifest artifacts are missing")
    apks = [item for item in artifacts if isinstance(item, Mapping) and item.get("type") == "apk"]
    if len(apks) != 1 or apks[0].get("name") != "Meet.apk":
        raise ReleaseNotesError("manifest must contain exactly one Meet.apk")
    apk = apks[0]
    version = manifest.get("version_name", manifest.get("versionName"))
    code = manifest.get("version_code", manifest.get("versionCode"))
    if not isinstance(version, str) or not version:
        raise ReleaseNotesError("manifest version name is missing")
    if isinstance(code, bool) or not isinstance(code, int) or code <= 0:
        raise ReleaseNotesError("manifest version code is invalid")
    installer_digest = _digest(apk.get("sha256"), "Meet.apk digest")
    fingerprint = _digest(
        manifest.get("signing_fingerprint", manifest.get("signingFingerprint")),
        "signing fingerprint",
    )
    return version, code, installer_digest, fingerprint


def _section(manifest: Mapping[str, Any]) -> str:
    version, code, installer_digest, fingerprint = _manifest_values(manifest)
    return "\n".join(
        (
            START_MARKER,
            "### Android verification",
            f"Version: {version} (code {code})",
            f"Meet.apk SHA-256: {installer_digest}",
            f"Signing certificate SHA-256: {fingerprint}",
            END_MARKER,
        )
    )


def render_release_notes(existing_body: str, manifest: Mapping[str, Any]) -> str:
    if not isinstance(existing_body, str):
        raise ReleaseNotesError("release body must be UTF-8 text")
    section = _section(manifest)
    starts = [match.start() for match in re.finditer(re.escape(START_MARKER), existing_body)]
    ends = [match.start() for match in re.finditer(re.escape(END_MARKER), existing_body)]
    if len(starts) > 1 or len(ends) > 1:
        raise ReleaseNotesError("release body contains duplicate verification markers")
    if bool(starts) != bool(ends) or (starts and ends and ends[0] < starts[0]):
        raise ReleaseNotesError("release body verification markers are unbalanced")
    if starts:
        end = ends[0] + len(END_MARKER)
        prefix = existing_body[: starts[0]]
        suffix = existing_body[end:]
        # Remove only the separators and terminal newline owned by a prior
        # rendering.  All caller text, including trailing whitespace, stays
        # byte-for-byte intact.
        if prefix.endswith("\n\n"):
            prefix = prefix[:-2]
        if suffix.startswith("\n"):
            suffix = suffix[1:]
        body = prefix + suffix
    else:
        body = existing_body
    # The caller owns every byte outside the marker section, including
    # trailing spaces and newlines.  Only the separator introduced by this
    # renderer is normalized.
    if body:
        return f"{body}\n\n{section}\n"
    return f"{section}\n"


def render_release_notes_file(existing_body_path: Path, manifest_path: Path, output_path: Path) -> None:
    body = existing_body_path.read_text(encoding="utf-8")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(manifest, Mapping):
        raise ReleaseNotesError("manifest must be an object")
    output_path.write_text(render_release_notes(body, manifest), encoding="utf-8")
