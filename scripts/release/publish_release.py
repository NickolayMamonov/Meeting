#!/usr/bin/env python3
"""The single, testable publication driver for stable Android releases."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping

from release_mutation_gate import (
    MutationError,
    expected_release_asset_names,
    verify_release_state,
    verify_uploaded_assets,
)
from release_notes import render_release_notes
from verify_remote_assets import MAX_RELEASE_ASSET_BYTES, verify as verify_remote_assets


MAX_JSON_BYTES = 8 * 1024 * 1024
CHUNK_BYTES = 1024 * 1024
API_HOST = "api.github.com"
UPLOAD_HOST = "uploads.github.com"
ASSET_HOST = "release-assets.githubusercontent.com"
API_VERSION = "2022-11-28"


class PublicationError(RuntimeError):
    pass


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _bounded_read(response: Any, limit: int) -> bytes:
    body = bytearray()
    while True:
        chunk = response.read(min(CHUNK_BYTES, limit - len(body) + 1))
        if not chunk:
            return bytes(body)
        body.extend(chunk)
        if len(body) > limit:
            raise PublicationError("response body exceeds configured bound")


@dataclass(frozen=True)
class ReleaseSnapshot:
    id: int
    name: str
    body: str
    tag_name: str
    target_commitish: str
    prerelease: bool


class GitHubReleaseClient:
    """Fixed-host GitHub REST client with no automatic redirect or retry."""

    def __init__(self, repository: str, *, token: str | None = None) -> None:
        if "/" not in repository or repository.count("/") != 1:
            raise PublicationError("repository must be owner/repo")
        self.repository = repository
        self.token = token if token is not None else os.environ.get("RELEASE_API_TOKEN", "")
        if not self.token:
            raise PublicationError("RELEASE_API_TOKEN is required")
        self._opener = urllib.request.build_opener(_NoRedirect, urllib.request.ProxyHandler({}))
        self._data_opener = urllib.request.build_opener(_NoRedirect, urllib.request.ProxyHandler({}))

    def _open(
        self,
        opener: urllib.request.OpenerDirector,
        url: str,
        *,
        method: str = "GET",
        body: bytes | None = None,
        headers: Mapping[str, str] | None = None,
    ) -> Any:
        request_headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "meet-release-tooling",
        }
        if opener is self._opener:
            request_headers["X-GitHub-Api-Version"] = API_VERSION
            request_headers["Authorization"] = f"Bearer {self.token}"
        if headers:
            request_headers.update(headers)
        request = urllib.request.Request(url, data=body, headers=request_headers, method=method)
        try:
            return opener.open(request)
        except urllib.error.HTTPError as error:
            raise PublicationError(f"GitHub REST {method} failed with HTTP {error.code}") from error
        except OSError as error:
            raise PublicationError(f"GitHub REST {method} transport failed") from error

    def _api_url(self, path: str) -> str:
        return f"https://{API_HOST}/repos/{self.repository}/{path.lstrip('/')}"

    def get_release(self, release_id: int) -> dict[str, Any]:
        with self._open(self._opener, self._api_url(f"releases/{release_id}")) as response:
            try:
                value = json.loads(_bounded_read(response, MAX_JSON_BYTES))
            except (json.JSONDecodeError, PublicationError) as error:
                raise PublicationError("GitHub release response is malformed") from error
        if not isinstance(value, dict):
            raise PublicationError("GitHub release response is not an object")
        return value

    def assert_tag_absent(self, tag: str) -> None:
        url = self._api_url(f"git/ref/tags/{urllib.parse.quote(tag, safe='')}")
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "User-Agent": "meet-release-tooling",
                "X-GitHub-Api-Version": API_VERSION,
                "Authorization": f"Bearer {self.token}",
            },
        )
        try:
            response = self._opener.open(request)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return
            raise PublicationError(f"tag authority lookup failed with HTTP {error.code}") from error
        except OSError as error:
            raise PublicationError("tag authority lookup failed") from error
        response.close()
        raise PublicationError(f"release tag already exists: {tag}")

    def create_asset(self, release_id: int, path: Path) -> dict[str, Any]:
        query_name = urllib.parse.quote(path.name, safe="")
        url = f"https://{UPLOAD_HOST}/repos/{self.repository}/releases/{release_id}/assets?name={query_name}"
        with path.open("rb") as source:
            body = source.read()
        with self._open(
            self._opener,
            url,
            method="POST",
            body=body,
            headers={"Content-Type": "application/octet-stream"},
        ) as response:
            try:
                value = json.loads(_bounded_read(response, MAX_JSON_BYTES))
            except (json.JSONDecodeError, PublicationError) as error:
                raise PublicationError("GitHub upload response is malformed") from error
        if not isinstance(value, dict):
            raise PublicationError("GitHub upload response is not an object")
        return value

    def patch_release(self, release_id: int, payload: Mapping[str, Any]) -> dict[str, Any]:
        body = json.dumps(dict(payload), ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        with self._open(
            self._opener,
            self._api_url(f"releases/{release_id}"),
            method="PATCH",
            body=body,
            headers={"Content-Type": "application/json"},
        ) as response:
            try:
                value = json.loads(_bounded_read(response, MAX_JSON_BYTES))
            except (json.JSONDecodeError, PublicationError) as error:
                raise PublicationError("GitHub patch response is malformed") from error
        if not isinstance(value, dict):
            raise PublicationError("GitHub patch response is not an object")
        return value

    def download_asset(self, asset_id: int, destination: Path, *, expected_size: int, expected_sha256: str) -> None:
        if expected_size <= 0 or expected_size > MAX_RELEASE_ASSET_BYTES:
            raise PublicationError("declared asset size is outside the configured bound")
        if len(expected_sha256) != 64 or any(c not in "0123456789abcdef" for c in expected_sha256):
            raise PublicationError("declared asset digest is not canonical")
        url = self._api_url(f"releases/assets/{asset_id}")
        request_headers = {
            "Accept": "application/octet-stream",
            "User-Agent": "meet-release-tooling",
            "X-GitHub-Api-Version": API_VERSION,
        }
        request = urllib.request.Request(url, headers={**request_headers, "Authorization": f"Bearer {self.token}"})
        try:
            response = self._opener.open(request)
        except urllib.error.HTTPError as error:
            if error.code != 302:
                raise PublicationError(f"asset download failed with HTTP {error.code}") from error
            response = error
        except OSError as error:
            raise PublicationError("asset download transport failed") from error
        location = response.headers.get("Location")
        if getattr(response, "status", None) == 302 or location:
            if not location:
                raise PublicationError("asset redirect has no Location")
            parsed = urllib.parse.urlsplit(location)
            if (
                parsed.scheme != "https"
                or parsed.hostname != ASSET_HOST
                or parsed.port not in (None, 443)
                or not parsed.path
                or parsed.username is not None
                or parsed.password is not None
                or parsed.fragment
            ):
                raise PublicationError("asset redirect location is not allowed")
            response.close()
            try:
                response = self._open(
                    self._data_opener,
                    location,
                    headers={"Accept": "application/octet-stream"},
                )
            except PublicationError:
                raise
        if getattr(response, "status", 200) != 200:
            response.close()
            raise PublicationError("asset data response is not HTTP 200")
        content_length = response.headers.get("Content-Length")
        if content_length is not None:
            try:
                if int(content_length) != expected_size:
                    raise PublicationError("asset Content-Length does not match declared size")
            except ValueError as error:
                raise PublicationError("asset Content-Length is malformed") from error
        destination.parent.mkdir(parents=True, exist_ok=True)
        partial = destination.with_name(destination.name + ".part")
        partial.unlink(missing_ok=True)
        count = 0
        hasher = hashlib.sha256()
        try:
            with response, partial.open("xb") as output:
                while True:
                    block = response.read(CHUNK_BYTES)
                    if not block:
                        break
                    count += len(block)
                    if count > expected_size or count > MAX_RELEASE_ASSET_BYTES:
                        raise PublicationError("asset download exceeds declared size")
                    hasher.update(block)
                    output.write(block)
            if count != expected_size:
                raise PublicationError("asset download is truncated")
            if hasher.hexdigest() != expected_sha256:
                raise PublicationError("asset download digest does not match")
            partial.replace(destination)
        except Exception:
            partial.unlink(missing_ok=True)
            raise


def _snapshot(payload: Mapping[str, Any]) -> ReleaseSnapshot:
    try:
        return ReleaseSnapshot(
            id=int(payload["id"]),
            name=payload["name"],
            body=payload["body"],
            tag_name=payload["tag_name"],
            target_commitish=payload["target_commitish"],
            prerelease=payload["prerelease"],
        )
    except (KeyError, TypeError, ValueError) as error:
        raise PublicationError("release snapshot is malformed") from error


def _validate_snapshot(snapshot: ReleaseSnapshot, *, release_id: int, tag: str, source_sha: str) -> None:
    if (
        snapshot.id != release_id
        or not isinstance(snapshot.name, str)
        or not snapshot.name
        or snapshot.tag_name != tag
        or snapshot.target_commitish != source_sha
        or not isinstance(snapshot.body, str)
        or not isinstance(snapshot.prerelease, bool)
    ):
        raise PublicationError("release snapshot identity changed")


def run(
    *,
    client: Any,
    release_id: int,
    tag: str,
    source_sha: str,
    evidence_directory: Path,
    manifest_path: Path,
    rendered_body_path: Path | None = None,
    verify_local: Callable[[Path], None] | None = None,
    verify_downloaded: Callable[[Path], None] | None = None,
    verify_attestation_local: Callable[[Path], Any] | None = None,
    verify_attestation_downloaded: Callable[[Path], Any] | None = None,
    assert_tag_absent: Callable[[str], None] | None = None,
    download_path: Path | None = None,
) -> dict[str, Any]:
    """Execute the exact one-POST/one-PATCH publication state machine."""
    try:
        allowed = expected_release_asset_names(evidence_directory, tag=tag, source_sha=source_sha)
        if allowed != {"Meet.apk"}:
            raise PublicationError("public asset projection is not exactly Meet.apk")
        from verify_chain import verify as verify_chain
        verify_chain(evidence_directory)
    except (MutationError, OSError, ValueError) as error:
        raise PublicationError(f"protected evidence admission failed: {error}") from error
    state = client.get_release(release_id)
    try:
        verify_release_state(state, release_id=release_id, tag=tag, allowed_names={"Meet.apk"})
    except MutationError as error:
        raise PublicationError(f"empty draft admission failed: {error}") from error
    original = _snapshot(state)
    _validate_snapshot(original, release_id=release_id, tag=tag, source_sha=source_sha)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    body = render_release_notes(original.body, manifest)
    if rendered_body_path is None:
        rendered_body_path = Path(tempfile.mkstemp(prefix="meet-release-notes-", suffix=".md")[1])
    rendered_body_path.write_text(body, encoding="utf-8")
    apk = evidence_directory / "Meet.apk"
    if verify_local:
        verify_local(apk)
    if verify_attestation_local:
        verify_attestation_local(apk)
    uploaded = client.create_asset(release_id, apk)
    state = client.get_release(release_id)
    try:
        verify_uploaded_assets(state, release_id=release_id, tag=tag, expected_names={"Meet.apk"})
    except MutationError as error:
        raise PublicationError(f"uploaded release state is invalid: {error}") from error
    assets = state["assets"]
    if uploaded.get("name") != "Meet.apk":
        raise PublicationError("upload response is not Meet.apk")
    asset = assets[0]
    asset_id = asset.get("id")
    if not isinstance(asset_id, int) or asset_id <= 0:
        raise PublicationError("uploaded asset ID is invalid")
    expected_size = int(asset.get("size", -1))
    manifest_artifacts = json.loads(manifest_path.read_text(encoding="utf-8")).get("artifacts", [])
    apk_entries = [item for item in manifest_artifacts if item.get("type") == "apk"]
    if len(apk_entries) != 1:
        raise PublicationError("release manifest does not have exactly one APK")
    expected_digest = str(apk_entries[0]["sha256"])
    if len(expected_digest) != 64 or any(c not in "0123456789abcdef" for c in expected_digest):
        raise PublicationError("release manifest APK digest is not canonical")
    temporary_download_directory: tempfile.TemporaryDirectory[str] | None = None
    if download_path is None:
        temporary_download_directory = tempfile.TemporaryDirectory(prefix="meet-release-download-")
        target = Path(temporary_download_directory.name) / "Meet.apk"
    else:
        target = download_path
    client.download_asset(asset_id, target, expected_size=expected_size, expected_sha256=expected_digest)
    verify_remote_assets(apk, _write_temp_json(state), target)
    if verify_downloaded:
        verify_downloaded(target)
    if verify_attestation_downloaded:
        verify_attestation_downloaded(target)
    before_patch = client.get_release(release_id)
    try:
        verify_uploaded_assets(before_patch, release_id=release_id, tag=tag, expected_names={"Meet.apk"})
    except MutationError as error:
        raise PublicationError(f"pre-publish release state is invalid: {error}") from error
    current = _snapshot(before_patch)
    if current != original or before_patch.get("assets") != state.get("assets"):
        raise PublicationError("release changed before final publication")
    if assert_tag_absent:
        assert_tag_absent(tag)
    elif hasattr(client, "assert_tag_absent"):
        client.assert_tag_absent(tag)
    patched = client.patch_release(
        release_id,
        {
            "name": original.name,
            "tag_name": original.tag_name,
            "target_commitish": original.target_commitish,
            "prerelease": original.prerelease,
            "body": body,
            "draft": False,
        },
    )
    if (
        patched.get("draft") is not False
        or patched.get("tag_name") != tag
        or patched.get("target_commitish") != source_sha
        or patched.get("name") != original.name
        or patched.get("body") != body
        or patched.get("prerelease") is not original.prerelease
    ):
        raise PublicationError("final publication response is malformed")
    final = client.get_release(release_id)
    try:
        from release_mutation_gate import verify_public_release_state
        verify_public_release_state(
            final,
            release_id=release_id,
            tag=tag,
            source_sha=source_sha,
            body=body,
            name=original.name,
            prerelease=original.prerelease,
        )
    except MutationError as error:
        raise PublicationError(f"final public release state is not exact: {error}") from error
    if final["assets"][0]["id"] != asset_id:
        raise PublicationError("final public release asset identity changed")
    result = {
        "release_id": release_id,
        "tag": tag,
        "source_sha": source_sha,
        "asset_id": asset_id,
        "body_sha256": hashlib.sha256(body.encode("utf-8")).hexdigest(),
        "published": True,
    }
    if temporary_download_directory is not None:
        temporary_download_directory.cleanup()
    return result


def _write_temp_json(value: Mapping[str, Any]) -> Path:
    handle, name = tempfile.mkstemp(prefix="meet-release-state-", suffix=".json")
    os.close(handle)
    path = Path(name)
    path.write_text(json.dumps(value), encoding="utf-8")
    return path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--release-id", required=True, type=int)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--evidence-directory", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--rendered-body", required=True, type=Path)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--apkanalyzer", required=True)
    parser.add_argument("--attestation-signer-workflow", required=True)
    parser.add_argument("--attestation-source-ref", required=True)
    parser.add_argument("--attestation-source-sha", required=True)
    args = parser.parse_args()
    try:
        if not os.environ.get("ATTESTATION_TOKEN"):
            raise PublicationError("ATTESTATION_TOKEN is required")
        client = GitHubReleaseClient(args.repository)
        metadata = json.loads(args.manifest.read_text(encoding="utf-8"))
        if not isinstance(metadata, dict):
            raise PublicationError("release manifest is malformed")
        from github_attestation import AttestationPolicy, verify_file
        from verify_android_artifacts import verify_apk
        policy = AttestationPolicy(
            repository=args.repository,
            signer_workflow=args.attestation_signer_workflow,
            source_ref=args.attestation_source_ref,
            source_digest=args.attestation_source_sha,
            predicate_type="https://slsa.dev/provenance/v1",
            result_limit=100,
        )

        def verify_android(path: Path) -> None:
            verify_apk(
                path,
                metadata,
                Path(args.apksigner),
                Path(args.apkanalyzer),
                False,
            )

        def verify_attestation(path: Path) -> Any:
            return verify_file(
                path,
                policy,
                token=os.environ["ATTESTATION_TOKEN"],
            )

        run(
            client=client,
            release_id=args.release_id,
            tag=args.tag,
            source_sha=args.source_sha,
            evidence_directory=args.evidence_directory,
            manifest_path=args.manifest,
            rendered_body_path=args.rendered_body,
            verify_local=verify_android,
            verify_downloaded=verify_android,
            verify_attestation_local=verify_attestation,
            verify_attestation_downloaded=verify_attestation,
        )
    except (PublicationError, MutationError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"release publication failed: {error}")
        return 1
    print("release publication passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
