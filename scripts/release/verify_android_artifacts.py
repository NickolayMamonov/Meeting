#!/usr/bin/env python3
"""Verify stable Android artifact identity against canonical build metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Sequence


class ArtifactError(ValueError):
    pass


def run(command: Sequence[str]) -> str:
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
    except (OSError, subprocess.CalledProcessError) as error:
        raise ArtifactError(f"command failed: {' '.join(command)}") from error
    return result.stdout + result.stderr


def file_digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def metadata_value(metadata: dict, *names: str) -> str:
    for name in names:
        value = metadata.get(name)
        if value is not None and str(value):
            return str(value)
    raise ArtifactError(f"metadata field is missing: {names[0]}")


def normalized_digest(value: str) -> str:
    digest = re.sub(r"[^0-9a-f]", "", value.lower())
    if len(digest) != 64:
        raise ArtifactError("certificate digest is not a SHA-256 value")
    return digest


def verify_apk(apk: Path, metadata: dict) -> None:
    output = run(["apksigner", "verify", "--verbose", "--print-certs", str(apk)])
    digests = re.findall(r"SHA-256 digest:\s*([0-9a-f: ]+)", output, flags=re.IGNORECASE)
    if not digests:
        raise ArtifactError("APK signer output did not contain a SHA-256 certificate digest")
    expected = normalized_digest(metadata_value(metadata, "expectedCertificateSha256", "signingFingerprint"))
    if normalized_digest(digests[0]) != expected:
        raise ArtifactError("APK certificate does not match canonical metadata")

    application_id = run(["apkanalyzer", "manifest", "application-id", str(apk)]).strip()
    version_name = run(["apkanalyzer", "manifest", "version-name", str(apk)]).strip()
    version_code = run(["apkanalyzer", "manifest", "version-code", str(apk)]).strip()
    if application_id != metadata_value(metadata, "applicationId", "application_id"):
        raise ArtifactError("APK application ID does not match canonical metadata")
    if version_name != metadata_value(metadata, "versionName", "version_name"):
        raise ArtifactError("APK version name does not match canonical metadata")
    if version_code != metadata_value(metadata, "versionCode", "version_code"):
        raise ArtifactError("APK version code does not match canonical metadata")
    debuggable = run(["apkanalyzer", "manifest", "debuggable", str(apk)]).strip().lower()
    if debuggable == "true":
        raise ArtifactError("APK is debuggable")


def verify_bundle(aab: Path, metadata: dict, bundletool_jar: Path) -> None:
    run(["jarsigner", "-verify", "-strict", str(aab)])
    signer_output = run(["keytool", "-printcert", "-jarfile", str(aab)])
    signer_digests = re.findall(
        r"SHA256:\s*([0-9a-f: ]+)", signer_output, flags=re.IGNORECASE
    )
    if not signer_digests:
        raise ArtifactError("AAB signer output did not contain a SHA-256 certificate digest")
    expected = normalized_digest(metadata_value(metadata, "expectedCertificateSha256", "signingFingerprint"))
    if normalized_digest(signer_digests[0]) != expected:
        raise ArtifactError("AAB certificate does not match canonical metadata")
    output = run(["java", "-jar", str(bundletool_jar), "dump", "manifest", f"--bundle={aab}"])
    start = output.find("<manifest")
    end = output.rfind("</manifest>")
    if start < 0 or end < start:
        raise ArtifactError("Bundletool did not return an Android manifest")
    root = ET.fromstring(output[start : end + len("</manifest>")])
    android_namespace = "{http://schemas.android.com/apk/res/android}"
    if root.attrib.get("package") != metadata_value(metadata, "applicationId", "application_id"):
        raise ArtifactError("AAB application ID does not match canonical metadata")
    if root.attrib.get(android_namespace + "versionName") != metadata_value(
        metadata, "versionName", "version_name"
    ):
        raise ArtifactError("AAB version name does not match canonical metadata")
    if root.attrib.get(android_namespace + "versionCode") != metadata_value(
        metadata, "versionCode", "version_code"
    ):
        raise ArtifactError("AAB version code does not match canonical metadata")
    if root.attrib.get(android_namespace + "debuggable", "false").lower() == "true":
        raise ArtifactError("AAB is debuggable")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aab", type=Path)
    parser.add_argument("--bundletool-jar", type=Path)
    parser.add_argument("--bundletool-sha256")
    try:
        args = parser.parse_args()
        metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
        if not isinstance(metadata, dict):
            raise ArtifactError("metadata must be an object")
        verify_apk(args.apk, metadata)
        if args.aab is not None:
            if args.bundletool_jar is None:
                raise ArtifactError("bundletool JAR is required for AAB verification")
            if args.bundletool_sha256 is None:
                raise ArtifactError("bundletool SHA-256 is required for AAB verification")
            expected_bundletool_digest = normalized_digest(args.bundletool_sha256)
            if file_digest(args.bundletool_jar) != expected_bundletool_digest:
                raise ArtifactError("bundletool digest does not match the pinned SHA-256")
            verify_bundle(args.aab, metadata, args.bundletool_jar)
    except (ArtifactError, OSError, ValueError, ET.ParseError, KeyError, json.JSONDecodeError) as error:
        print(f"Android artifact verification failed: {error}")
        return 1
    print("Android artifact verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
