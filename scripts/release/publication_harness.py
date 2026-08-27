#!/usr/bin/env python3
"""Hermetic loopback publication harness for exact-head QA.

The harness intentionally implements the driver's transport interface in
memory.  It records the same control-plane/data-plane boundary that the
production client exposes, while making any external release, tag, or PR
request impossible.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from publish_release import PublicationError, run


@dataclass(frozen=True)
class TranscriptEntry:
    method: str
    path: str
    headers: tuple[str, ...]
    body_sha256: str | None = None

    def to_mapping(self) -> dict[str, Any]:
        return {
            "method": self.method,
            "path": self.path,
            "headers": list(self.headers),
            "body_sha256": self.body_sha256,
        }


class LoopbackPublicationClient:
    """A release client whose only authority is a loopback fixture."""

    def __init__(
        self,
        *,
        release: Mapping[str, Any],
        apk: Path,
        use_redirect: bool = False,
        inject_after_final_read: bool = False,
    ) -> None:
        self.release = json.loads(json.dumps(release))
        self.apk = apk
        self.use_redirect = use_redirect
        self.inject_after_final_read = inject_after_final_read
        self.transcript: list[TranscriptEntry] = []
        self._reads = 0
        self._asset_id = 9001

    def _record(self, method: str, path: str, headers: tuple[str, ...], body: bytes | None = None) -> None:
        self.transcript.append(
            TranscriptEntry(
                method,
                path,
                headers,
                None if body is None else hashlib.sha256(body).hexdigest(),
            )
        )

    def get_release(self, release_id: int) -> dict[str, Any]:
        self._reads += 1
        self._record("GET", f"/repos/owner/repo/releases/{release_id}", ("accept", "user-agent", "x-github-api-version", "authorization"))
        if self.inject_after_final_read and self._reads == 4:
            self.release["body"] = "out-of-band mutation"
        return json.loads(json.dumps(self.release))

    def create_asset(self, release_id: int, path: Path) -> dict[str, Any]:
        body = path.read_bytes()
        self._record(
            "POST",
            f"/repos/owner/repo/releases/{release_id}/assets?name=Meet.apk",
            ("accept", "user-agent", "x-github-api-version", "authorization", "content-type"),
            body,
        )
        self.release["assets"] = [{
            "id": self._asset_id,
            "name": "Meet.apk",
            "size": len(body),
            "digest": f"sha256:{hashlib.sha256(body).hexdigest()}",
        }]
        self._asset_bytes = body
        return dict(self.release["assets"][0])

    def download_asset(self, asset_id: int, destination: Path, *, expected_size: int, expected_sha256: str) -> None:
        if asset_id != self._asset_id:
            raise PublicationError("loopback asset ID changed")
        self._record("GET", f"/repos/owner/repo/releases/assets/{asset_id}", ("accept", "user-agent", "x-github-api-version", "authorization"))
        if self.use_redirect:
            self._record("302", "https://release-assets.githubusercontent.com/loopback/Meet.apk", ("accept", "user-agent"))
        else:
            self._record("200", f"/repos/owner/repo/releases/assets/{asset_id}", ("accept", "user-agent", "x-github-api-version", "authorization"))
        destination.write_bytes(self._asset_bytes)
        if len(self._asset_bytes) != expected_size or hashlib.sha256(self._asset_bytes).hexdigest() != expected_sha256:
            raise PublicationError("loopback asset bytes do not match")

    def patch_release(self, release_id: int, payload: Mapping[str, Any]) -> dict[str, Any]:
        body = json.dumps(dict(payload), sort_keys=True, separators=(",", ":")).encode()
        self._record("PATCH", f"/repos/owner/repo/releases/{release_id}", ("accept", "user-agent", "x-github-api-version", "authorization", "content-type"), body)
        self.release.update(payload)
        self.release["published_at"] = "2026-08-27T00:00:00Z"
        return json.loads(json.dumps(self.release))

    def assert_tag_absent(self, tag: str) -> None:
        self._record("GET", f"/repos/owner/repo/git/ref/tags/{tag}", ("accept", "user-agent", "x-github-api-version", "authorization"))


def run_harness(
    *,
    evidence_directory: Path,
    manifest_path: Path,
    release_body: str,
    tag: str,
    source_sha: str,
    attestation_repository: str | None = None,
    attestation_signer_workflow: str | None = None,
    attestation_source_ref: str | None = None,
    attestation_source_sha: str | None = None,
    attestation_token: str | None = None,
    apksigner: Path | None = None,
    apkanalyzer: Path | None = None,
    application_source_sha: str | None = None,
    use_redirect: bool = False,
    inject_after_final_read: bool = False,
) -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="meet-publication-harness-") as temporary:
        body_path = Path(temporary) / "release-body.md"
        body_path.write_text(release_body, encoding="utf-8")
        client = LoopbackPublicationClient(
            release={
                "id": 42,
                "name": "Meet v1.0.0",
                "body": release_body,
                "tag_name": tag,
                "target_commitish": source_sha,
                "draft": True,
                "published_at": None,
                "prerelease": False,
                "assets": [],
            },
            apk=evidence_directory / "Meet.apk",
            use_redirect=use_redirect,
            inject_after_final_read=inject_after_final_read,
        )
        verify_android = None
        verify_attestation = None
        if apksigner is not None and apkanalyzer is not None:
            from verify_android_artifacts import verify_apk
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            verify_android = lambda path: verify_apk(
                path, manifest, apksigner, apkanalyzer, False
            )
        if (
            attestation_repository
            and attestation_signer_workflow
            and attestation_source_ref
            and attestation_source_sha
            and attestation_token
        ):
            from github_attestation import AttestationPolicy, verify_file
            policy = AttestationPolicy(
                repository=attestation_repository,
                signer_workflow=attestation_signer_workflow,
                source_ref=attestation_source_ref,
                source_digest=attestation_source_sha,
                predicate_type="https://slsa.dev/provenance/v1",
                result_limit=100,
            )
            verify_attestation = lambda path: verify_file(
                path, policy, token=attestation_token
            )
        result = run(
            client=client,
            release_id=42,
            tag=tag,
            source_sha=source_sha,
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
            rendered_body_path=body_path,
            download_path=Path(temporary) / "Meet.apk",
            verify_local=verify_android,
            verify_downloaded=verify_android,
            verify_attestation_local=verify_attestation,
            verify_attestation_downloaded=verify_attestation,
        )
        result["transcript"] = [entry.to_mapping() for entry in client.transcript]
        result["external_hosts_contacted"] = []
        result["tooling_sha"] = attestation_source_sha or source_sha
        result["application_source_sha"] = application_source_sha
        result["attestation_policy"] = (
            None
            if attestation_repository is None
            else {
                "repository": attestation_repository,
                "signer_workflow": attestation_signer_workflow,
                "source_ref": attestation_source_ref,
                "source_digest": attestation_source_sha,
            }
        )
        return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-directory", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--release-body", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--application-source-sha")
    parser.add_argument("--attestation-repository")
    parser.add_argument("--attestation-signer-workflow")
    parser.add_argument("--attestation-source-ref")
    parser.add_argument("--attestation-source-sha")
    parser.add_argument("--apksigner", type=Path)
    parser.add_argument("--apkanalyzer", type=Path)
    parser.add_argument("--redirect", action="store_true")
    args = parser.parse_args()
    try:
        result = run_harness(
            evidence_directory=args.evidence_directory,
            manifest_path=args.manifest,
            release_body=args.release_body,
            tag=args.tag,
            source_sha=args.source_sha,
            application_source_sha=args.application_source_sha,
            attestation_repository=args.attestation_repository,
            attestation_signer_workflow=args.attestation_signer_workflow,
            attestation_source_ref=args.attestation_source_ref,
            attestation_source_sha=args.attestation_source_sha,
            attestation_token=os.environ.get("ATTESTATION_TOKEN"),
            apksigner=args.apksigner,
            apkanalyzer=args.apkanalyzer,
            use_redirect=args.redirect,
        )
        print(json.dumps(result, sort_keys=True))
        return 0
    except (PublicationError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"publication harness failed: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
