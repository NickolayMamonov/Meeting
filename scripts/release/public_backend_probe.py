#!/usr/bin/env python3
"""Fail-closed probe of the public production HTTPS contract."""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
from collections.abc import Callable
from typing import Any


ORIGIN = "https://api.whysoezzy.online"
MAX_MEETINGS_BYTES = 1_048_576


class ProbeError(ValueError):
    pass


Fetch = Callable[[str], tuple[int, bytes]]


def _validate_meetings(body: bytes) -> None:
    if len(body) > MAX_MEETINGS_BYTES:
        raise ProbeError("meetings response is too large")
    try:
        json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ProbeError("meetings response is not valid JSON") from error


def probe(fetch: Fetch) -> None:
    meetings_status, meetings_body = fetch(f"{ORIGIN}/meetings")
    if meetings_status != 200:
        raise ProbeError("meetings endpoint did not return HTTPS 200")
    _validate_meetings(meetings_body)

    actuator_status, _ = fetch(f"{ORIGIN}/actuator")
    if actuator_status != 404:
        raise ProbeError("public actuator endpoint is not HTTPS 404")


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request: Any, *args: Any, **kwargs: Any) -> Any:
        raise ProbeError("redirects are not permitted")


def _fetch(url: str) -> tuple[int, bytes]:
    if not url.startswith(f"{ORIGIN}/"):
        raise ProbeError("probe URL is outside the fixed production origin")
    opener = urllib.request.build_opener(_NoRedirectHandler)
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with opener.open(request, timeout=15) as response:
            return int(response.status), response.read(MAX_MEETINGS_BYTES + 1)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return 404, b""
        raise ProbeError("HTTPS probe returned an unexpected HTTP status") from error
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise ProbeError("HTTPS probe transport or TLS validation failed") from error


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.parse_args()
    try:
        probe(_fetch)
    except ProbeError as error:
        print(f"public backend probe failed: {error}")
        return 1
    print("public backend probe passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
