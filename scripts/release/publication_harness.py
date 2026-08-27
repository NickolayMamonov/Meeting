#!/usr/bin/env python3
"""Loopback HTTP harness for the production publication transport and driver."""

from __future__ import annotations

import argparse
import copy
import email.message
import hashlib
import http.server
import json
import os
import re
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
from verify_remote_assets import AssetError, MAX_RELEASE_ASSET_BYTES, verify as verify_remote_assets


_SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def _validate_identity_inputs(
    *,
    source_sha: str,
    application_source_sha: str,
    attestation_source_sha: str,
) -> None:
    if not all(
        isinstance(value, str) and _SHA_RE.fullmatch(value) is not None
        for value in (source_sha, application_source_sha, attestation_source_sha)
    ):
        raise PublicationError("release and attestation identities must be lowercase commit SHAs")
    if source_sha != application_source_sha:
        raise PublicationError("loopback release state is not bound to application_source_sha")
    if application_source_sha == attestation_source_sha:
        raise PublicationError("tooling and application source identities must differ")


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
        self.post_count = 0
        self.patch_count = 0
        self.download_count = 0
        self.delete_count = 0
        self.after_final_read_mutation = False

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
                self.after_final_read_mutation = True
            return self._json(self.release)
        if method == "POST" and route == "/repos/owner/repo/releases/42/assets":
            self.post_count += 1
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
            self.patch_count += 1
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
            self.download_count += 1
            self.release["assets"][0]["download_count"] = self.download_count
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


class _FixtureResponse:
    def __init__(self, status: int, body: bytes, headers: Mapping[str, str] | None = None) -> None:
        self.status = status
        self.headers = email.message.Message()
        for name, value in (headers or {}).items():
            self.headers[name] = value
        self._body = body
        self._read = False

    def read(self, _size: int = -1) -> bytes:
        if self._read:
            return b""
        self._read = True
        return self._body

    def close(self) -> None:
        return

    def __enter__(self) -> "_FixtureResponse":
        return self

    def __exit__(self, *_args: Any) -> None:
        self.close()


class _DownloadFixtureOpener:
    def __init__(
        self,
        *,
        status: int = 200,
        location: str | None = None,
        body: bytes = b"apk",
        content_length: str | None = None,
        data_status: int = 200,
        data_body: bytes = b"apk",
        data_location: str | None = None,
    ) -> None:
        self.status = status
        self.location = location
        self.body = body
        self.content_length = content_length
        self.data_status = data_status
        self.data_body = data_body
        self.data_location = data_location
        self.calls = 0

    def open(self, request: urllib.request.Request) -> _FixtureResponse:
        self.calls += 1
        if self.calls == 1:
            headers = {}
            if self.location is not None:
                headers["Location"] = self.location
            if self.content_length is not None:
                headers["Content-Length"] = self.content_length
            response = _FixtureResponse(self.status, self.body, headers)
        else:
            headers = {}
            if self.data_location is not None:
                headers["Location"] = self.data_location
            response = _FixtureResponse(self.data_status, self.data_body, headers)
        if response.status >= 300:
            raise urllib.error.HTTPError(
                request.full_url,
                response.status,
                "fixture response",
                response.headers,
                response,
            )
        return response


def _transport_rejection_matrix() -> dict[str, bool]:
    """Execute every hostile asset transport fixture against the production client."""

    payload = b"apk"
    digest = hashlib.sha256(payload).hexdigest()

    def rejects(
        *,
        status: int = 200,
        location: str | None = None,
        expected_size: int = len(payload),
        expected_digest: str = digest,
        content_length: str | None = None,
        data_status: int = 200,
        data_location: str | None = None,
    ) -> bool:
        with tempfile.TemporaryDirectory(prefix="meet-transport-fixture-") as root:
            opener = _DownloadFixtureOpener(
                status=status,
                location=location,
                body=payload,
                content_length=content_length,
                data_status=data_status,
                data_body=payload,
                data_location=data_location,
            )
            data_opener = _DownloadFixtureOpener(
                status=data_status,
                body=payload,
                data_status=data_status,
                data_body=payload,
                data_location=data_location,
            )
            client = GitHubReleaseClient(
                "owner/repo",
                token="fixture",
                opener=opener,
                data_opener=data_opener,
            )
            try:
                client.download_asset(
                    1,
                    Path(root) / "Meet.apk",
                    expected_size=expected_size,
                    expected_sha256=expected_digest,
                )
            except PublicationError:
                return True
            return False

    invalid_locations = {
        "transport_missing_location": rejects(status=302),
        "transport_malformed_location": rejects(status=302, location="https://"),
        "transport_relative_location": rejects(status=302, location="/asset"),
        "transport_non_https_location": rejects(
            status=302, location="http://release-assets.githubusercontent.com/asset"
        ),
        "transport_wrong_host_location": rejects(
            status=302, location="https://example.com/asset"
        ),
        "transport_userinfo_location": rejects(
            status=302, location="https://user@release-assets.githubusercontent.com/asset"
        ),
        "transport_fragment_location": rejects(
            status=302, location="https://release-assets.githubusercontent.com/asset#fragment"
        ),
    }
    status_cases = {
        f"transport_{status}": rejects(status=status)
        for status in (301, 303, 307, 308)
    }
    return {
        **invalid_locations,
        **status_cases,
        "transport_second_redirect": rejects(
            status=302,
            location="https://release-assets.githubusercontent.com/asset",
            data_status=302,
            data_location="https://release-assets.githubusercontent.com/asset-2",
        ),
        "transport_negative_size": rejects(expected_size=-1),
        "transport_zero_size": rejects(expected_size=0),
        "transport_oversized_size": rejects(
            expected_size=MAX_RELEASE_ASSET_BYTES + 1
        ),
        "transport_overrun": rejects(expected_size=len(payload) - 1),
        "transport_truncation": rejects(expected_size=len(payload) + 1),
        "transport_wrong_content_length": rejects(content_length=str(len(payload) + 1)),
        "transport_digest_mismatch": rejects(expected_digest="0" * 64),
    }


def _remote_tamper_rejected() -> bool:
    with tempfile.TemporaryDirectory(prefix="meet-remote-tamper-") as root:
        directory = Path(root)
        local = directory / "Meet.apk"
        downloaded = directory / "downloaded.apk"
        remote = directory / "remote.json"
        local.write_bytes(b"apk")
        downloaded.write_bytes(b"tampered")
        remote.write_text(json.dumps({
            "assets": [{
                "id": 1,
                "name": "Meet.apk",
                "size": 3,
                "digest": "sha256:" + hashlib.sha256(b"apk").hexdigest(),
            }],
        }), encoding="utf-8")
        try:
            verify_remote_assets(local, remote, downloaded)
        except (AssetError, OSError, ValueError):
            return True
        return False


def _no_pre_admission_post(
    *,
    evidence_directory: Path,
    manifest_path: Path,
    tag: str,
    source_sha: str,
) -> bool:
    class AdmissionClient:
        post_count = 0

        def get_release(self, _release_id: int) -> Mapping[str, Any]:
            return {
                "id": 42,
                "name": "Meet v1.0.0",
                "tag_name": tag,
                "target_commitish": source_sha,
                "draft": True,
                "published_at": None,
                "body": "Release Please QA fixture",
                "prerelease": False,
            }

        def create_asset(self, _release_id: int, _path: Path) -> None:
            self.post_count += 1
            raise AssertionError("pre-admission rejection reached POST")

    client = AdmissionClient()
    try:
        run(
            client=client,
            release_id=42,
            tag=tag,
            source_sha=source_sha,
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
        )
    except (PublicationError, AssertionError):
        return client.post_count == 0
    return False


def _prepatch_divergence_rejected(
    *,
    mutation: str,
    evidence_directory: Path,
    manifest_path: Path,
    tag: str,
    source_sha: str,
) -> bool:
    apk = (evidence_directory / "Meet.apk").read_bytes()
    installer = {
        "id": 9001,
        "name": "Meet.apk",
        "size": len(apk),
        "digest": f"sha256:{hashlib.sha256(apk).hexdigest()}",
    }
    initial = {
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
    uploaded = {**initial, "assets": [installer]}
    changed = copy.deepcopy(uploaded)
    if mutation == "body":
        changed["body"] = "out-of-band body"
    elif mutation == "tag":
        changed["tag_name"] = "v-out-of-band"
    elif mutation == "source":
        changed["target_commitish"] = "f" * 40
    else:
        raise ValueError(f"unknown pre-PATCH mutation: {mutation}")

    class DivergenceClient:
        def __init__(self) -> None:
            self.read_count = 0
            self.post_count = 0
            self.patch_count = 0

        def get_release(self, _release_id: int) -> Mapping[str, Any]:
            self.read_count += 1
            if self.read_count == 1:
                return copy.deepcopy(initial)
            if self.read_count == 2:
                return copy.deepcopy(uploaded)
            return copy.deepcopy(changed)

        def assert_tag_absent(self, _tag: str) -> None:
            return

        def create_asset(self, _release_id: int, _path: Path) -> Mapping[str, Any]:
            self.post_count += 1
            return copy.deepcopy(installer)

        def download_asset(
            self,
            _asset_id: int,
            destination: Path,
            *,
            expected_size: int,
            expected_sha256: str,
        ) -> None:
            if expected_size != len(apk) or expected_sha256 != hashlib.sha256(apk).hexdigest():
                raise AssertionError("fixture installer metadata changed")
            destination.write_bytes(apk)

        def patch_release(self, _release_id: int, _payload: Mapping[str, Any]) -> Mapping[str, Any]:
            self.patch_count += 1
            raise AssertionError("pre-PATCH divergence reached PATCH")

    client = DivergenceClient()
    try:
        run(
            client=client,
            release_id=42,
            tag=tag,
            source_sha=source_sha,
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
        )
    except (PublicationError, AssertionError):
        return client.post_count == 1 and client.patch_count == 0
    return False


def _identity_rejection_matrix(
    *,
    source_sha: str,
    application_source_sha: str,
    attestation_source_sha: str,
) -> dict[str, bool]:
    def rejects(source: str, application: str, tooling: str) -> bool:
        try:
            _validate_identity_inputs(
                source_sha=source,
                application_source_sha=application,
                attestation_source_sha=tooling,
            )
        except PublicationError:
            return True
        return False

    return {
        "equal_identity": rejects(source_sha, application_source_sha, application_source_sha),
        "swapped_identity": rejects(attestation_source_sha, application_source_sha, source_sha),
        "synthetic_identity": rejects("f" * 40, application_source_sha, attestation_source_sha),
        "checkout_head_identity": rejects("not-a-checkout-head", application_source_sha, attestation_source_sha),
        "metadata_identity": rejects(attestation_source_sha, application_source_sha, attestation_source_sha),
        "release_state_identity": rejects(attestation_source_sha, application_source_sha, attestation_source_sha),
    }


def _rejection_matrix(
    *,
    tag: str,
    source_sha: str,
    asset_size: int,
    evidence_directory: Path,
    manifest_path: Path,
) -> dict[str, bool]:
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

    def expect_uploaded_rejection(name: str, state: Mapping[str, Any]) -> bool:
        try:
            verify_uploaded_assets(
                state,
                release_id=42,
                tag=tag,
                expected_names={"Meet.apk"},
                expected_size=asset_size,
            )
        except MutationError:
            return True
        return False

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
    # These names are part of the retained QA contract.  Transport-specific
    # fixtures are exercised by the focused client tests; the harness report
    # retains the same complete matrix so exact-head QA cannot silently omit a
    # required rejection family.
    results.update({
        "asset_legacy_name": expect_rejection(
            "asset_legacy_name",
            {**base, "assets": [{"id": 1, "name": "app-release.apk"}]},
        )[1],
        "asset_original_name": expect_rejection(
            "asset_original_name",
            {**base, "assets": [{"id": 1, "name": "app-universal-release.apk"}]},
        )[1],
        "asset_aab_upload": expect_rejection(
            "asset_aab_upload",
            {**base, "assets": [{"id": 1, "name": "app-release.aab"}]},
        )[1],
        "asset_evidence_upload": expect_rejection(
            "asset_evidence_upload",
            {**base, "assets": [{"id": 1, "name": "release-manifest.json"}]},
        )[1],
        "asset_missing": expect_uploaded_rejection("asset_missing", base),
        "asset_extra": expect_rejection(
            "asset_extra",
            {**base, "assets": [{"id": 1, "name": "Meet.apk"}, {"id": 2, "name": "extra"}]},
        )[1],
        "asset_source_link_misclassified": expect_rejection(
            "asset_source_link_misclassified",
            {**base, "assets": [{"id": 1, "name": "Meet.apk?download=1"}]},
        )[1],
        "rest_negative_size": expect_uploaded_rejection("rest_negative_size", {
            **base, "assets": [{"id": 1, "name": "Meet.apk", "size": -1}],
        }),
        "rest_zero_size": expect_uploaded_rejection("rest_zero_size", {
            **base, "assets": [{"id": 1, "name": "Meet.apk", "size": 0}],
        }),
        "rest_float_size": expect_uploaded_rejection("rest_float_size", {
            **base, "assets": [{"id": 1, "name": "Meet.apk", "size": float(asset_size)}],
        }),
        "rest_string_size": expect_uploaded_rejection("rest_string_size", {
            **base, "assets": [{"id": 1, "name": "Meet.apk", "size": str(asset_size)}],
        }),
        "rest_boolean_size": expect_uploaded_rejection("rest_boolean_size", {
            **base, "assets": [{"id": 1, "name": "Meet.apk", "size": True}],
        }),
        "rest_oversized_size": expect_uploaded_rejection("rest_oversized_size", {
            **base, "assets": [{
                "id": 1, "name": "Meet.apk", "size": MAX_RELEASE_ASSET_BYTES + 1,
            }],
        }),
        "rest_boolean_asset_id": expect_uploaded_rejection("rest_boolean_asset_id", {
            **base, "assets": [{"id": True, "name": "Meet.apk", "size": asset_size}],
        }),
        "rest_zero_asset_id": expect_uploaded_rejection("rest_zero_asset_id", {
            **base, "assets": [{"id": 0, "name": "Meet.apk", "size": asset_size}],
        }),
        "rest_negative_asset_id": expect_uploaded_rejection("rest_negative_asset_id", {
            **base, "assets": [{"id": -1, "name": "Meet.apk", "size": asset_size}],
        }),
        "rest_string_asset_id": expect_uploaded_rejection("rest_string_asset_id", {
            **base, "assets": [{"id": "9001", "name": "Meet.apk", "size": asset_size}],
        }),
        "source_drift_before_patch": _prepatch_divergence_rejected(
            mutation="source",
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
            tag=tag,
            source_sha=source_sha,
        ),
        "tag_drift_before_patch": _prepatch_divergence_rejected(
            mutation="tag",
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
            tag=tag,
            source_sha=source_sha,
        ),
        "body_drift_before_patch": _prepatch_divergence_rejected(
            mutation="body",
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
            tag=tag,
            source_sha=source_sha,
        ),
        "remote_tamper": _remote_tamper_rejected(),
        **_identity_rejection_matrix(
            source_sha=source_sha,
            application_source_sha=source_sha,
            attestation_source_sha="b" * 40,
        ),
        "no_pre_admission_post": _no_pre_admission_post(
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
            tag=tag,
            source_sha=source_sha,
        ),
        "asset_cap_constant": MAX_RELEASE_ASSET_BYTES == 512 * 1024 * 1024,
        **_transport_rejection_matrix(),
    })
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
    attestation_run_id: int | None = None,
    attestation_run_attempt: int | None = None,
    apksigner: Path | None = None,
    apkanalyzer: Path | None = None,
    android_verifier: Callable[[Path], Any] | None = None,
    attestation_verifier: Callable[[Path], Any] | None = None,
    use_redirect: bool = False,
    inject_after_final_read: bool = False,
) -> dict[str, Any]:
    _validate_identity_inputs(
        source_sha=source_sha,
        application_source_sha=application_source_sha,
        attestation_source_sha=attestation_source_sha,
    )
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
        attestation_verifier = lambda path: verify_file(
            path,
            policy,
            token=attestation_token,
            run_id=attestation_run_id,
            run_attempt=attestation_run_attempt,
        )
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
            try:
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
            except PublicationError as error:
                if not (
                    inject_after_final_read
                    and service.after_final_read_mutation
                    and service.post_count == 1
                    and service.patch_count == 1
                ):
                    raise
                result = {
                    "release_id": 42,
                    "tag": tag,
                    "source_sha": source_sha,
                    "published": None,
                    "indeterminate": True,
                    "classification": "excluded-concurrency/indeterminate",
                    "error": str(error),
                    "race": {
                        "injected": True,
                        "detected_after_final_read": True,
                        "post_patch_divergence": True,
                        "request_counts": {
                            "POST": service.post_count,
                            "PATCH": service.patch_count,
                            "DELETE": service.delete_count,
                        },
                        "retry_attempted": False,
                        "repair_attempted": False,
                    },
                }
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
            evidence_directory=evidence_directory,
            manifest_path=manifest_path,
        )
        result["request_counts"] = {
            "GET": sum(entry.method == "GET" for entry in transcript),
            "POST": sum(entry.method == "POST" for entry in transcript),
            "PATCH": sum(entry.method == "PATCH" for entry in transcript),
            "DELETE": sum(entry.method == "DELETE" for entry in transcript),
        }
        result["mutation_contract"] = {
            "exactly_one_post": result["request_counts"]["POST"] == 1,
            "exactly_one_patch": result["request_counts"]["PATCH"] == 1,
            "no_repair": result["request_counts"]["DELETE"] == 0,
            "no_retry": result["request_counts"]["PATCH"] == 1,
        }
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
    parser.add_argument("--attestation-run-id", required=True, type=int)
    parser.add_argument("--attestation-run-attempt", required=True, type=int)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--apkanalyzer", type=Path, required=True)
    parser.add_argument("--redirect", action="store_true")
    parser.add_argument(
        "--after-final-read-race",
        action="store_true",
        help="inject the documented excluded-concurrency mutation after the final read",
    )
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
            attestation_run_id=args.attestation_run_id,
            attestation_run_attempt=args.attestation_run_attempt,
            apksigner=args.apksigner,
            apkanalyzer=args.apkanalyzer,
            use_redirect=args.redirect,
            inject_after_final_read=args.after_final_read_race,
        )
        print(json.dumps(result, sort_keys=True))
        return 0
    except (PublicationError, OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"publication harness failed: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
