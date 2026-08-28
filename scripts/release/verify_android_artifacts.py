#!/usr/bin/env python3
"""Verify Android artifact identity against canonical build metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Sequence

from release_roles import ReleaseRolesError, validate_canonical_branch


class ArtifactError(ValueError):
    pass


_COMMIT_SHA = re.compile(r"[0-9a-f]{40}\Z")


def parse_expected_debuggable(value: str) -> bool:
    if value == "true":
        return True
    if value == "false":
        return False
    raise argparse.ArgumentTypeError(
        "expected debuggable must be exactly lowercase 'true' or 'false'"
    )


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


def _metadata_aliases(metadata: dict, *names: str) -> list[tuple[str, object]]:
    if not isinstance(metadata, dict):
        raise ArtifactError("metadata must be an object")
    present = [(name, metadata[name]) for name in names if name in metadata]
    if not present:
        raise ArtifactError(f"metadata field is missing: {names[0]}")
    return present


def metadata_value(metadata: dict, *names: str) -> str:
    present = _metadata_aliases(metadata, *names)
    if any(not isinstance(value, str) or not value for _, value in present):
        raise ArtifactError(f"metadata field is invalid: {names[0]}")
    if len({value for _, value in present}) != 1:
        raise ArtifactError(f"metadata aliases conflict: {', '.join(names)}")
    return present[0][1]


def metadata_version_code(metadata: dict) -> str:
    present = _metadata_aliases(metadata, "versionCode", "version_code")
    if any(isinstance(value, bool) or not isinstance(value, int) for _, value in present):
        raise ArtifactError("metadata field is invalid: versionCode")
    if len({value for _, value in present}) != 1:
        raise ArtifactError("metadata aliases conflict: versionCode, version_code")
    return str(present[0][1])


def canonical_identity_branch(value: object, *, description: str) -> str:
    try:
        return validate_canonical_branch(value)
    except ReleaseRolesError as error:
        raise ArtifactError(f"{description} is invalid") from error


def metadata_identity(metadata: dict) -> tuple[str, str]:
    commit = metadata_value(metadata, "commitSha", "commit")
    if _COMMIT_SHA.fullmatch(commit) is None:
        raise ArtifactError("metadata commit must be exactly 40 lowercase hexadecimal characters")
    branch = metadata_value(metadata, "sourceBranch", "source_branch")
    return commit, canonical_identity_branch(branch, description="metadata source branch")


def verify_expected_identity(
    metadata: dict,
    *,
    expected_commit: str,
    expected_source_branch: str,
) -> None:
    if not isinstance(expected_commit, str) or _COMMIT_SHA.fullmatch(expected_commit) is None:
        raise ArtifactError("expected commit must be exactly 40 lowercase hexadecimal characters")
    expected_source_branch = canonical_identity_branch(
        expected_source_branch,
        description="expected source branch",
    )
    commit, branch = metadata_identity(metadata)
    if commit != expected_commit:
        raise ArtifactError("expected commit does not match canonical metadata")
    if branch != expected_source_branch:
        raise ArtifactError("expected source branch does not match canonical metadata")


def normalized_digest(value: str) -> str:
    digest = re.sub(r"[^0-9a-f]", "", value.lower())
    if len(digest) != 64:
        raise ArtifactError("certificate digest is not a SHA-256 value")
    return digest


def verify_rsa4096_signer(output: str) -> None:
    """Require the release signer invariant, not only its certificate hash."""

    normalized = output.lower()
    keytool_format = re.search(
        r"public key algorithm:\s*(\d+)-bit\s+([a-z0-9]+)\s+key",
        normalized,
    )
    if keytool_format is not None:
        if keytool_format.group(1) != "4096" or keytool_format.group(2) != "rsa":
            raise ArtifactError("release signer RSA-4096 identity is missing")
        return
    algorithm = re.search(r"key algorithm:\s*([a-z0-9]+)", normalized)
    key_size = re.search(r"key size(?:\s*\(bits\))?:\s*(\d+)", normalized)
    if algorithm is not None or key_size is not None:
        if algorithm is None or algorithm.group(1) != "rsa":
            raise ArtifactError("release signer key algorithm is not RSA")
        if key_size is None or key_size.group(1) != "4096":
            raise ArtifactError("release signer key size is not RSA-4096")
        return
    raise ArtifactError("release signer RSA-4096 identity is missing")


def decode_debuggable_output(output: str) -> bool:
    value = output.strip().lower()
    if value == "true":
        return True
    if value == "false":
        return False
    raise ArtifactError("apkanalyzer returned an invalid debuggable value")


def verify_apk(
    apk: Path,
    metadata: dict,
    apksigner: Path,
    apkanalyzer: Path,
    expected_debuggable: bool,
    expected_commit: str | None = None,
    expected_source_branch: str | None = None,
) -> None:
    metadata_identity(metadata)
    if (expected_commit is None) != (expected_source_branch is None):
        raise ArtifactError("expected commit and source branch must be provided together")
    if expected_commit is not None:
        verify_expected_identity(
            metadata,
            expected_commit=expected_commit,
            expected_source_branch=expected_source_branch,
        )
    output = run([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)])
    verify_rsa4096_signer(output)
    digests = re.findall(r"SHA-256 digest:\s*([0-9a-f: ]+)", output, flags=re.IGNORECASE)
    if not digests:
        raise ArtifactError("APK signer output did not contain a SHA-256 certificate digest")
    expected = normalized_digest(
        metadata_value(
            metadata,
            "expectedCertificateSha256",
            "signingFingerprint",
            "signing_fingerprint",
        )
    )
    if normalized_digest(digests[0]) != expected:
        raise ArtifactError("APK certificate does not match canonical metadata")

    verify_apk_identity(apk, metadata, apkanalyzer, expected_debuggable)


def verify_apk_identity(
    apk: Path,
    metadata: dict,
    apkanalyzer: Path,
    expected_debuggable: bool,
) -> None:
    metadata_identity(metadata)
    analyzer = str(apkanalyzer)
    application_id = run([analyzer, "manifest", "application-id", str(apk)]).strip()
    version_name = run([analyzer, "manifest", "version-name", str(apk)]).strip()
    version_code = run([analyzer, "manifest", "version-code", str(apk)]).strip()
    if application_id != metadata_value(metadata, "applicationId", "application_id"):
        raise ArtifactError("APK application ID does not match canonical metadata")
    if version_name != metadata_value(metadata, "versionName", "version_name"):
        raise ArtifactError("APK version name does not match canonical metadata")
    if version_code != metadata_version_code(metadata):
        raise ArtifactError("APK version code does not match canonical metadata")
    actual_debuggable = decode_debuggable_output(
        run([analyzer, "manifest", "debuggable", str(apk)])
    )
    if actual_debuggable != expected_debuggable:
        raise ArtifactError(
            "APK debuggable value does not match expected value: "
            f"expected {expected_debuggable}, actual {actual_debuggable}"
        )


def verify_unsigned_apk(
    apk: Path,
    metadata: dict,
    apkanalyzer: Path,
    expected_debuggable: bool,
    *,
    expected_commit: str,
    expected_source_branch: str,
) -> None:
    verify_expected_identity(
        metadata,
        expected_commit=expected_commit,
        expected_source_branch=expected_source_branch,
    )
    verify_apk_identity(apk, metadata, apkanalyzer, expected_debuggable)


_JARSIGNER_VERIFIED = re.compile(r"(?im)^\s*jar verified\.\s*$")
_JARSIGNER_UNSIGNED = re.compile(
    r"(?i)(?:\bjar\s+is\s+unsigned\b|\bunsigned[-\s]+entries?\b|\bjar[-\s]unsigned\b)"
)


def verify_jarsigner_bundle(aab: Path) -> str:
    """Verify an AAB while allowing the repository's self-signed release cert."""

    output = run(["jarsigner", "-verify", "-verbose", "-certs", str(aab)])
    if not _JARSIGNER_VERIFIED.search(output):
        raise ArtifactError("jarsigner did not report a verified JAR")
    if _JARSIGNER_UNSIGNED.search(output):
        raise ArtifactError("jarsigner reported unsigned entries")
    return output


def verify_bundle_identity(aab: Path, metadata: dict, bundletool_jar: Path) -> None:
    metadata_identity(metadata)
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
    if root.attrib.get(android_namespace + "versionCode") != metadata_version_code(metadata):
        raise ArtifactError("AAB version code does not match canonical metadata")
    if root.attrib.get(android_namespace + "debuggable", "false").lower() == "true":
        raise ArtifactError("AAB is debuggable")


def verify_bundle(aab: Path, metadata: dict, bundletool_jar: Path) -> None:
    metadata_identity(metadata)
    verify_jarsigner_bundle(aab)
    signer_output = run(["keytool", "-printcert", "-jarfile", str(aab)])
    verify_rsa4096_signer(signer_output)
    signer_digests = re.findall(
        r"SHA256:\s*([0-9a-f: ]+)", signer_output, flags=re.IGNORECASE
    )
    if not signer_digests:
        raise ArtifactError("AAB signer output did not contain a SHA-256 certificate digest")
    expected = normalized_digest(
        metadata_value(
            metadata,
            "expectedCertificateSha256",
            "signingFingerprint",
            "signing_fingerprint",
        )
    )
    if normalized_digest(signer_digests[0]) != expected:
        raise ArtifactError("AAB certificate does not match canonical metadata")
    verify_bundle_identity(aab, metadata, bundletool_jar)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--apksigner")
    parser.add_argument("--apkanalyzer", required=True)
    parser.add_argument(
        "--expected-debuggable", type=parse_expected_debuggable, required=True
    )
    parser.add_argument("--expected-commit")
    parser.add_argument("--expected-source-branch")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--unsigned-apk", action="store_true")
    mode.add_argument("--unsigned-release", action="store_true")
    parser.add_argument("--aab", type=Path)
    parser.add_argument("--bundletool-jar", type=Path)
    parser.add_argument("--bundletool-sha256")
    try:
        args = parser.parse_args()
        if args.unsigned_apk:
            if (
                args.apksigner is not None
                or args.aab is not None
                or args.bundletool_jar is not None
                or args.bundletool_sha256 is not None
            ):
                raise ArtifactError("--unsigned-apk forbids signer, AAB, and Bundletool inputs")
        elif args.unsigned_release:
            if args.apksigner is not None:
                raise ArtifactError("--unsigned-release forbids signer input")
            if args.aab is None:
                raise ArtifactError("--unsigned-release requires an AAB")
            if args.bundletool_jar is None or args.bundletool_sha256 is None:
                raise ArtifactError(
                    "--unsigned-release requires Bundletool inputs"
                )
        elif args.apksigner == "":
            raise ArtifactError("--apksigner must not be empty")
        elif args.apksigner is None:
            raise ArtifactError("--apksigner is required for signed APK verification")
        if args.apkanalyzer == "":
            raise ArtifactError("--apkanalyzer must not be empty")
        if args.expected_commit is None or args.expected_source_branch is None:
            raise ArtifactError("--expected-commit and --expected-source-branch are required")
        metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
        if not isinstance(metadata, dict):
            raise ArtifactError("metadata must be an object")
        verify_expected_identity(
            metadata,
            expected_commit=args.expected_commit,
            expected_source_branch=args.expected_source_branch,
        )
        if args.unsigned_apk:
            verify_apk_identity(
                args.apk, metadata, Path(args.apkanalyzer), args.expected_debuggable
            )
        elif args.unsigned_release:
            if metadata.get("channel") != "release":
                raise ArtifactError("--unsigned-release requires release metadata")
            verify_apk_identity(
                args.apk, metadata, Path(args.apkanalyzer), args.expected_debuggable
            )
            expected_bundletool_digest = normalized_digest(args.bundletool_sha256)
            if file_digest(args.bundletool_jar) != expected_bundletool_digest:
                raise ArtifactError("Bundletool digest does not match the pinned SHA-256")
            verify_bundle_identity(args.aab, metadata, args.bundletool_jar)
        else:
            verify_apk(
                args.apk,
                metadata,
                Path(args.apksigner),
                Path(args.apkanalyzer),
                args.expected_debuggable,
                args.expected_commit,
                args.expected_source_branch,
            )
        if args.aab is not None and not args.unsigned_release:
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
