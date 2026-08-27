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


def _manifest_installer(manifest_path: Path, apk: Path) -> tuple[int, str]:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PublicationError("release manifest cannot be read") from error
    if not isinstance(manifest, Mapping):
        raise PublicationError("release manifest is malformed")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise PublicationError("release manifest artifacts are malformed")
    apk_entries = [
        item for item in artifacts
        if isinstance(item, Mapping) and item.get("type") == "apk"
    ]
    if len(apk_entries) != 1 or apk_entries[0].get("name") != "Meet.apk":
        raise PublicationError("release manifest does not have exactly one Meet.apk")
    entry = apk_entries[0]
    size = entry.get("size")
    digest = entry.get("sha256")
    if (
        isinstance(size, bool)
        or not isinstance(size, int)
        or size <= 0
        or size > MAX_RELEASE_ASSET_BYTES
        or not isinstance(digest, str)
        or len(digest) != 64
        or any(character not in "0123456789abcdef" for character in digest)
    ):
        raise PublicationError("release manifest Meet.apk metadata is invalid")
    try:
        local_size = apk.stat().st_size
    except OSError as error:
        raise PublicationError("canonical Meet.apk cannot be read") from error
    if local_size != size:
        raise PublicationError("local Meet.apk size does not match manifest")
    local_digest = hashlib.sha256()
    with apk.open("rb") as source:
        for block in iter(lambda: source.read(CHUNK_BYTES), b""):
            local_digest.update(block)
    if local_digest.hexdigest() != digest:
        raise PublicationError("local Meet.apk digest does not match manifest")
    return size, digest


def _serialize_verification(value: Any) -> dict[str, Any] | None:
    """Retain only validated, non-secret verification result metadata."""

    if value is None:
        return None
    policy = getattr(value, "policy", None)
    matched = getattr(value, "matched_subject", None)
    subjects = getattr(value, "statement_subjects", ())
    if policy is None or matched is None:
        raise PublicationError("verification callback returned an unvalidated record")
    return {
        "verified": True,
        "path": Path(getattr(value, "path", "")).name,
        "file_sha256": getattr(value, "file_sha256", ""),
        "policy": {
            "repository": policy.repository,
            "signer_workflow": policy.signer_workflow,
            "source_ref": policy.source_ref,
            "source_digest": policy.source_digest,
            "predicate_type": policy.predicate_type,
            "result_limit": policy.result_limit,
        },
        "matched_subject": {
            "name": matched.name,
            "sha256": matched.sha256,
        },
        "subjects": [
            {"name": subject.name, "sha256": subject.sha256}
            for subject in subjects
        ],
    }


def _asset_identity(asset: Mapping[str, Any]) -> tuple[int, str, int, str | None]:
    """Return only immutable asset fields; REST telemetry is intentionally ignored."""

    if not isinstance(asset, Mapping):
        raise PublicationError("release asset is malformed")
    asset_id = asset.get("id")
    name = asset.get("name")
    size = asset.get("size")
    digest = asset.get("digest")
    if (
        isinstance(asset_id, bool)
        or not isinstance(asset_id, int)
        or asset_id <= 0
        or not isinstance(name, str)
        or not name
        or isinstance(size, bool)
        or not isinstance(size, int)
        or size <= 0
        or size > MAX_RELEASE_ASSET_BYTES
    ):
        raise PublicationError("release asset immutable identity is malformed")
    if digest in (None, ""):
        canonical_digest = None
    elif (
        isinstance(digest, str)
        and len(digest) == 71
        and digest.startswith("sha256:")
        and all(character in "0123456789abcdef" for character in digest[7:])
    ):
        canonical_digest = digest
    else:
        raise PublicationError("release asset digest is malformed")
    return asset_id, name, size, canonical_digest


class GitHubReleaseClient:
    """Fixed-host GitHub REST client with no automatic redirect or retry."""

    def __init__(
        self,
        repository: str,
        *,
        token: str | None = None,
        opener: Any | None = None,
        data_opener: Any | None = None,
    ) -> None:
        if "/" not in repository or repository.count("/") != 1:
            raise PublicationError("repository must be owner/repo")
        self.repository = repository
        self.token = token if token is not None else os.environ.get("RELEASE_API_TOKEN", "")
        if not self.token:
            raise PublicationError("RELEASE_API_TOKEN is required")
        self._opener = opener or urllib.request.build_opener(_NoRedirect, urllib.request.ProxyHandler({}))
        self._data_opener = data_opener or urllib.request.build_opener(
            _NoRedirect, urllib.request.ProxyHandler({})
        )

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
            error.close()
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
                error.close()
                return
            raise PublicationError(f"tag authority lookup failed with HTTP {error.code}") from error
        except OSError as error:
            raise PublicationError("tag authority lookup failed") from error
        response.close()
        raise PublicationError(f"release tag already exists: {tag}")

    def create_asset(self, release_id: int, path: Path) -> dict[str, Any]:
        try:
            size = path.stat().st_size
        except OSError as error:
            raise PublicationError("release asset cannot be stat'ed") from error
        if size <= 0 or size > MAX_RELEASE_ASSET_BYTES:
            raise PublicationError("release asset size is outside the configured bound")
        query_name = urllib.parse.quote(path.name, safe="")
        url = f"https://{UPLOAD_HOST}/repos/{self.repository}/releases/{release_id}/assets?name={query_name}"
        with path.open("rb") as source:
            body = bytearray()
            while True:
                block = source.read(CHUNK_BYTES)
                if not block:
                    break
                body.extend(block)
                if len(body) > MAX_RELEASE_ASSET_BYTES:
                    raise PublicationError("release asset exceeds the configured bound")
        if len(body) != size:
            raise PublicationError("release asset changed while being read")
        with self._open(
            self._opener,
            url,
            method="POST",
            body=bytes(body),
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
                error.close()
                raise PublicationError(f"asset download failed with HTTP {error.code}") from error
            response = error
        except OSError as error:
            raise PublicationError("asset download transport failed") from error
        status = getattr(response, "status", 200)
        location = response.headers.get("Location")
        if status == 302:
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
                response.close()
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
        elif status != 200:
            response.close()
            raise PublicationError("asset data response is not HTTP 200")
        elif location:
            response.close()
            raise PublicationError("direct asset response unexpectedly redirects")
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

    def verify_tag_source(self, tag: str, source_sha: str) -> None:
        """Resolve a final tag ref, including annotated tags, to the source commit."""

        encoded_tag = urllib.parse.quote(tag, safe="")
        url = self._api_url(f"git/ref/tags/{encoded_tag}")
        with self._open(self._opener, url) as response:
            try:
                ref = json.loads(_bounded_read(response, MAX_JSON_BYTES))
            except (json.JSONDecodeError, PublicationError) as error:
                raise PublicationError("final tag ref response is malformed") from error
        if not isinstance(ref, Mapping) or not isinstance(ref.get("object"), Mapping):
            raise PublicationError("final tag ref response is malformed")
        target = ref["object"]
        target_type = target.get("type")
        target_sha = target.get("sha")
        if not isinstance(target_sha, str) or len(target_sha) != 40 or any(
            character not in "0123456789abcdef" for character in target_sha
        ):
            raise PublicationError("final tag ref target is invalid")
        if target_type == "commit":
            resolved_sha = target_sha
        elif target_type == "tag":
            tag_url = self._api_url(f"git/tags/{target_sha}")
            with self._open(self._opener, tag_url) as response:
                try:
                    annotated = json.loads(_bounded_read(response, MAX_JSON_BYTES))
                except (json.JSONDecodeError, PublicationError) as error:
                    raise PublicationError("annotated tag response is malformed") from error
            nested = annotated.get("object") if isinstance(annotated, Mapping) else None
            resolved_sha = nested.get("sha") if isinstance(nested, Mapping) else None
            if not isinstance(resolved_sha, str) or len(resolved_sha) != 40 or any(
                character not in "0123456789abcdef" for character in resolved_sha
            ):
                raise PublicationError("annotated tag target is invalid")
        else:
            raise PublicationError("final tag ref target type is invalid")
        if resolved_sha != source_sha:
            raise PublicationError("final tag ref does not resolve to source_sha")


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
    verify_local: Callable[[Path], Any] | None = None,
    verify_downloaded: Callable[[Path], Any] | None = None,
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
    apk = evidence_directory / "Meet.apk"
    expected_size, expected_digest = _manifest_installer(manifest_path, apk)
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
    tag_checker = assert_tag_absent
    if tag_checker is None and hasattr(client, "assert_tag_absent"):
        tag_checker = client.assert_tag_absent
    if tag_checker is None:
        raise PublicationError("tag authority checker is missing")
    tag_checker(tag)
    if verify_local:
        local_android = verify_local(apk)
    else:
        local_android = None
    if verify_attestation_local:
        local_attestation = verify_attestation_local(apk)
    else:
        local_attestation = None
    uploaded = client.create_asset(release_id, apk)
    state = client.get_release(release_id)
    try:
        verify_uploaded_assets(
            state,
            release_id=release_id,
            tag=tag,
            expected_names={"Meet.apk"},
            expected_size=expected_size,
            expected_sha256=expected_digest,
        )
    except MutationError as error:
        raise PublicationError(f"uploaded release state is invalid: {error}") from error
    assets = state["assets"]
    if uploaded.get("name") != "Meet.apk":
        raise PublicationError("upload response is not Meet.apk")
    asset = assets[0]
    asset_id = asset.get("id")
    if not isinstance(asset_id, int) or asset_id <= 0:
        raise PublicationError("uploaded asset ID is invalid")
    temporary_download_directory: tempfile.TemporaryDirectory[str] | None = None
    if download_path is None:
        temporary_download_directory = tempfile.TemporaryDirectory(prefix="meet-release-download-")
        target = Path(temporary_download_directory.name) / "Meet.apk"
    else:
        target = download_path
    client.download_asset(asset_id, target, expected_size=expected_size, expected_sha256=expected_digest)
    remote_state_path = _write_temp_json(state)
    try:
        verify_remote_assets(apk, remote_state_path, target)
    finally:
        remote_state_path.unlink(missing_ok=True)
    if verify_downloaded:
        downloaded_android = verify_downloaded(target)
    else:
        downloaded_android = None
    if verify_attestation_downloaded:
        downloaded_attestation = verify_attestation_downloaded(target)
    else:
        downloaded_attestation = None
    before_patch = client.get_release(release_id)
    try:
        verify_uploaded_assets(
            before_patch,
            release_id=release_id,
            tag=tag,
            expected_names={"Meet.apk"},
            expected_size=expected_size,
            expected_sha256=expected_digest,
        )
    except MutationError as error:
        raise PublicationError(f"pre-publish release state is invalid: {error}") from error
    current = _snapshot(before_patch)
    before_asset_identity = _asset_identity(before_patch["assets"][0])
    uploaded_asset_identity = _asset_identity(state["assets"][0])
    if current != original or before_asset_identity != uploaded_asset_identity:
        raise PublicationError("release changed before final publication")
    tag_checker(tag)
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
    if not hasattr(client, "verify_tag_source"):
        raise PublicationError("final tag source verifier is missing")
    client.verify_tag_source(tag, source_sha)
    result = {
        "release_id": release_id,
        "tag": tag,
        "source_sha": source_sha,
        "asset_id": asset_id,
        "body_sha256": hashlib.sha256(body.encode("utf-8")).hexdigest(),
        "published": True,
        "verification": {
            "android_local": local_android,
            "android_downloaded": downloaded_android,
            "attestation_local": _serialize_verification(local_attestation),
            "attestation_downloaded": _serialize_verification(downloaded_attestation),
        },
        "android_checks": {
            "local": verify_local is not None,
            "downloaded": verify_downloaded is not None,
        },
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
    parser.add_argument("--attestation-run-id", required=True, type=int)
    parser.add_argument("--attestation-run-attempt", required=True, type=int)
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
                run_id=args.attestation_run_id,
                run_attempt=args.attestation_run_attempt,
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
