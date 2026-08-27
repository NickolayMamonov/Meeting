#!/usr/bin/env python3
"""Loopback HTTP harness for the production publication transport and driver."""

from __future__ import annotations

import argparse
import hashlib
import http.server
import json
import os
import tempfile
import threading
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping

from publish_release import (
    GitHubReleaseClient,
    PublicationError,
    _NoRedirect,
    run,
)
from release_mutation_gate import MutationError, verify_release_state, verify_uploaded_assets
from verify_remote_assets import MAX_RELEASE_ASSET_BYTES


@dataclass(frozen=True)
class TranscriptEntry:
    method: str
    url: str
    path: str
    status: int
    headers: tuple[str, ...]
    response_headers: tuple[str, ...] = ()
    body_sha256: str | None = None

    def to_mapping(self) -> dict[str, Any]:
        return {
            "method": self.method,
            "url": self.url,
            "path": self.path,
            "status": self.status,
            "headers": list(self.headers),
            "response_headers": list(self.response_headers),
            "body_sha256": self.body_sha256,
        }


class _LoopbackService:
    def __init__(
        self,
        *,
        tag: str,
        release_body: str,
        source_sha: str,
        apk: bytes,
        redirect: bool,
        inject_after_final_read: bool,
        annotated_tag: bool = False,
    ) -> None:
        self.tag = tag
        self.source_sha = source_sha
        self.apk = apk
        self.redirect = redirect
        self.inject_after_final_read = inject_after_final_read
        self.annotated_tag = annotated_tag
        self.release: dict[str, Any] = {
            "id": 42,
            "name": "Meet v1.0.0",
            "body": release_body,
            "tag_name": tag,
            "target_commitish": source_sha,
            "draft": True,
            "published_at": None,
            "prerelease": False,
            "assets": [],
        }
        self.release_reads = 0
        self.tag_created = False
        self.asset_id = 9001

    def _json(self, value: Any) -> tuple[int, dict[str, str], bytes]:
        body = json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return 200, {"Content-Type": "application/json", "Content-Length": str(len(body))}, body

    def handle(
        self,
        method: str,
        path: str,
        headers: Mapping[str, str],
        body: bytes,
    ) -> tuple[int, dict[str, str], bytes]:
        parsed = urllib.parse.urlsplit(path)
        route = parsed.path
        if method == "GET" and route == "/repos/owner/repo/releases/42":
            self.release_reads += 1
            if self.inject_after_final_read and self.release_reads == 4:
                self.release["body"] = "out-of-band mutation"
            return self._json(self.release)
        if method == "POST" and route == "/repos/owner/repo/releases/42/assets":
            if urllib.parse.parse_qs(parsed.query).get("name") != ["Meet.apk"]:
                return 400, {}, b""
            self.apk = body
            self.release["assets"] = [{
                "id": self.asset_id,
                "name": "Meet.apk",
                "size": len(body),
                "digest": f"sha256:{hashlib.sha256(body).hexdigest()}",
            }]
            return self._json(self.release["assets"][0])
        if method == "PATCH" and route == "/repos/owner/repo/releases/42":
            try:
                payload = json.loads(body)
            except json.JSONDecodeError:
                return 400, {}, b""
            if not isinstance(payload, dict):
                return 400, {}, b""
            self.release.update(payload)
            self.release["published_at"] = "2026-08-27T00:00:00Z"
            self.tag_created = True
            return self._json(self.release)
        if method == "GET" and route == "/repos/owner/repo/releases/assets/9001":
            if self.redirect:
                return (
                    302,
                    {
                        "Location": "https://release-assets.githubusercontent.com/loopback/Meet.apk",
                        "Content-Length": "0",
                    },
                    b"",
                )
            return 200, {
                "Content-Type": "application/octet-stream",
                "Content-Length": str(len(self.apk)),
            }, self.apk
        if method == "GET" and route == "/loopback/Meet.apk":
            return 200, {
                "Content-Type": "application/octet-stream",
                "Content-Length": str(len(self.apk)),
            }, self.apk
        if method == "GET" and route == f"/repos/owner/repo/git/ref/tags/{self.tag}":
            if not self.tag_created:
                return 404, {}, b""
            if self.annotated_tag:
                return self._json({"object": {"sha": "b" * 40, "type": "tag"}})
            return self._json({"object": {"sha": self.source_sha, "type": "commit"}})
        if method == "GET" and route == f"/repos/owner/repo/git/tags/{'b' * 40}":
            return self._json({"object": {"sha": self.source_sha, "type": "commit"}})
        return 404, {}, b""


class _LoopbackRequestHandler(http.server.BaseHTTPRequestHandler):
    server: "_LoopbackHTTPServer"

    def do_GET(self) -> None:
        self._dispatch()

    def do_POST(self) -> None:
        self._dispatch()

    def do_PATCH(self) -> None:
        self._dispatch()

    def _dispatch(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        status, response_headers, response_body = self.server.service.handle(
            self.command,
            self.path,
            {key: value for key, value in self.headers.items()},
            body,
        )
        self.send_response(status)
        for key, value in response_headers.items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(response_body)

    def log_message(self, _format: str, *_args: Any) -> None:
        return


class _LoopbackHTTPServer(http.server.ThreadingHTTPServer):
    def __init__(self, service: _LoopbackService) -> None:
        super().__init__(("127.0.0.1", 0), _LoopbackRequestHandler)
        self.service = service


class _LoopbackOpener:
    """Map production GitHub URLs to the local server without changing them in the transcript."""

    def __init__(self, server: _LoopbackHTTPServer, transcript: list[TranscriptEntry]) -> None:
        self.server = server
        self.transcript = transcript
        self._opener = urllib.request.build_opener(_NoRedirect, urllib.request.ProxyHandler({}))

    def open(self, request: urllib.request.Request) -> Any:
        original = urllib.parse.urlsplit(request.full_url)
        local_url = urllib.parse.urlunsplit(
            (
                "http",
                f"127.0.0.1:{self.server.server_port}",
                original.path,
                original.query,
                "",
            )
        )
        headers = dict(request.header_items())
        mapped = urllib.request.Request(
            local_url,
            data=request.data,
            headers=headers,
            method=request.get_method(),
        )
        body = request.data
        try:
            response = self._opener.open(mapped)
        except urllib.error.HTTPError as error:
            self.transcript.append(
                TranscriptEntry(
                    method=request.get_method(),
                    url=request.full_url,
                    path=original.path,
                    status=error.code,
                    headers=tuple(sorted(name.lower() for name in headers)),
                    response_headers=tuple(
                        sorted(name.lower() for name in error.headers or {})
                    ),
                    body_sha256=None if body is None else hashlib.sha256(body).hexdigest(),
                )
            )
            raise
        self.transcript.append(
            TranscriptEntry(
                method=request.get_method(),
                url=request.full_url,
                path=original.path,
                status=getattr(response, "status", 200),
                headers=tuple(sorted(name.lower() for name in headers)),
                response_headers=tuple(
                    sorted(name.lower() for name in response.headers)
                ),
                body_sha256=None if body is None else hashlib.sha256(body).hexdigest(),
            )
        )
        return response


def _rejection_matrix(*, tag: str, source_sha: str, asset_size: int) -> dict[str, bool]:
    """Run the fail-closed REST fixtures without touching any network."""

    base: dict[str, Any] = {
        "id": 42,
        "name": "Meet v1.0.0",
        "tag_name": tag,
        "target_commitish": source_sha,
        "draft": True,
        "published_at": None,
        "body": "Release Please QA fixture",
        "prerelease": False,
        "assets": [],
    }

    def expect_rejection(name: str, state: Mapping[str, Any], *, release_id: int = 42) -> tuple[str, bool]:
        try:
            verify_release_state(
                state,
                release_id=release_id,
                tag=tag,
                allowed_names={"Meet.apk"},
            )
        except MutationError:
            return name, True
        return name, False

    missing = dict(base)
    del missing["assets"]
    non_list = {**base, "assets": {}}
    duplicate = {**base, "assets": [{"id": 1, "name": "Meet.apk"}, {"id": 2, "name": "Meet.apk"}]}
    unknown = {**base, "assets": [{"id": 1, "name": "release-manifest.json"}]}
    wrong_size = {
        **base,
        "assets": [{
            "id": 1,
            "name": "Meet.apk",
            "size": asset_size + 1,
            "digest": "sha256:" + ("0" * 64),
        }],
    }
    results = dict([
        expect_rejection("missing_assets", missing),
        expect_rejection("non_list_assets", non_list),
        expect_rejection("invalid_release_id", base, release_id=0),
        expect_rejection("duplicate_asset_names", duplicate),
        expect_rejection("unknown_asset_name", unknown),
    ])
    try:
        verify_uploaded_assets(
            wrong_size,
            release_id=42,
            tag=tag,
            expected_names={"Meet.apk"},
            expected_size=asset_size,
        )
    except MutationError:
        results["rest_size_mismatch"] = True
    else:
        results["rest_size_mismatch"] = False
    results["asset_cap_constant"] = MAX_RELEASE_ASSET_BYTES == 512 * 1024 * 1024
    return results


def run_harness(
    *,
    evidence_directory: Path,
    manifest_path: Path,
    release_body: str,
    tag: str,
    source_sha: str,
    application_source_sha: str,
    attestation_repository: str,
    attestation_signer_workflow: str,
    attestation_source_ref: str,
    attestation_source_sha: str,
    attestation_token: str,
    apksigner: Path | None = None,
    apkanalyzer: Path | None = None,
    android_verifier: Callable[[Path], Any] | None = None,
    attestation_verifier: Callable[[Path], Any] | None = None,
    use_redirect: bool = False,
    inject_after_final_read: bool = False,
) -> dict[str, Any]:
    if application_source_sha != source_sha:
        raise PublicationError("loopback release state is not bound to application_source_sha")
    if application_source_sha == attestation_source_sha:
        raise PublicationError("tooling and application source identities must differ")
    if not all(
        isinstance(value, str) and value
        for value in (
            attestation_repository,
            attestation_signer_workflow,
            attestation_source_ref,
            attestation_source_sha,
            attestation_token,
        )
    ):
        raise PublicationError("complete attestation policy and token are required")
    if android_verifier is None and (apksigner is None or apkanalyzer is None):
        raise PublicationError("Android verifier inputs are required")
    if attestation_verifier is None:
        from github_attestation import AttestationPolicy, verify_file

        policy = AttestationPolicy(
            repository=attestation_repository,
            signer_workflow=attestation_signer_workflow,
            source_ref=attestation_source_ref,
            source_digest=attestation_source_sha,
            predicate_type="https://slsa.dev/provenance/v1",
            result_limit=100,
        )
        attestation_verifier = lambda path: verify_file(path, policy, token=attestation_token)
    if android_verifier is None:
        from verify_android_artifacts import verify_apk

        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        android_verifier = lambda path: verify_apk(
            path, manifest, apksigner, apkanalyzer, False
        )

    apk = (evidence_directory / "Meet.apk").read_bytes()
    service = _LoopbackService(
        tag=tag,
        release_body=release_body,
        source_sha=source_sha,
        apk=apk,
        redirect=use_redirect,
        inject_after_final_read=inject_after_final_read,
    )
    transcript: list[TranscriptEntry] = []
    server = _LoopbackHTTPServer(service)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        opener = _LoopbackOpener(server, transcript)
        data_opener = _LoopbackOpener(server, transcript)
        client = GitHubReleaseClient(
            "owner/repo",
            token="loopback-release-token",
            opener=opener,
            data_opener=data_opener,
        )
        with tempfile.TemporaryDirectory(prefix="meet-publication-harness-") as temporary:
            body_path = Path(temporary) / "release-body.md"
            result = run(
                client=client,
                release_id=42,
                tag=tag,
                source_sha=source_sha,
                evidence_directory=evidence_directory,
                manifest_path=manifest_path,
                rendered_body_path=body_path,
                download_path=Path(temporary) / "Meet.apk",
                verify_local=android_verifier,
                verify_downloaded=android_verifier,
                verify_attestation_local=attestation_verifier,
                verify_attestation_downloaded=attestation_verifier,
            )
        result["transcript"] = [entry.to_mapping() for entry in transcript]
        result["external_hosts_contacted"] = sorted({
            urllib.parse.urlsplit(entry.url).hostname
            for entry in transcript
            if urllib.parse.urlsplit(entry.url).hostname
            not in {"api.github.com", "uploads.github.com", "release-assets.githubusercontent.com"}
        })
        result["tooling_sha"] = attestation_source_sha
        result["application_source_sha"] = application_source_sha
        result["identities"] = {
            "tooling_sha": attestation_source_sha,
            "application_source_sha": application_source_sha,
            "source_sha": source_sha,
            "sha_inequality": attestation_source_sha != application_source_sha,
        }
        result["attestation_policy"] = {
            "repository": attestation_repository,
            "signer_workflow": attestation_signer_workflow,
            "source_ref": attestation_source_ref,
            "source_digest": attestation_source_sha,
            "predicate_type": "https://slsa.dev/provenance/v1",
            "result_limit": 100,
        }
        result["transport"] = {
            "mode": "redirect" if use_redirect else "direct",
            "loopback_http": True,
            "asset_cap_bytes": MAX_RELEASE_ASSET_BYTES,
            "content_length_observed": all(
                "content-length" in entry["response_headers"]
                for entry in result["transcript"]
                if entry["url"].endswith("/Meet.apk")
                or "/releases/assets/" in entry["url"]
            ),
            "single_redirect": sum(
                entry["status"] == 302 for entry in result["transcript"]
            ) == (1 if use_redirect else 0),
            "credential_free_data_leg": all(
                "authorization" not in entry["headers"]
                for entry in result["transcript"]
                if entry["url"].startswith("https://release-assets.githubusercontent.com/")
            ),
        }
        result["rejection_matrix"] = _rejection_matrix(
            tag=tag,
            source_sha=source_sha,
            asset_size=(evidence_directory / "Meet.apk").stat().st_size,
        )
        return result
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-directory", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--release-body", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--application-source-sha", required=True)
    parser.add_argument("--attestation-repository", required=True)
    parser.add_argument("--attestation-signer-workflow", required=True)
    parser.add_argument("--attestation-source-ref", required=True)
    parser.add_argument("--attestation-source-sha", required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--apkanalyzer", type=Path, required=True)
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
            attestation_token=os.environ.get("ATTESTATION_TOKEN", ""),
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
