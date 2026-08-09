#!/usr/bin/env python3
"""Require externally supplied runtime evidence before stable publication."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any, Mapping


CERTIFICATE_PATTERN = re.compile(r"^[0-9a-fA-F]{64}$")


def verify(evidence: Mapping[str, Any]) -> None:
    if evidence.get("reset_device_state") is True:
        raise ValueError("runtime gate may not reset authenticated device state")
    if evidence.get("firebase_package") != "dev.whysoezzy.meet":
        raise ValueError("runtime Firebase package is not the stable application ID")
    certificate = evidence.get("signing_certificate")
    if not isinstance(certificate, str) or not CERTIFICATE_PATTERN.fullmatch(certificate):
        raise ValueError("runtime signing certificate fingerprint is invalid")
    pins = evidence.get("tls_spki")
    if (
        not isinstance(pins, list)
        or len(pins) < 2
        or len(pins) != len(set(pins))
        or any(not isinstance(pin, str) or not CERTIFICATE_PATTERN.fullmatch(pin) for pin in pins)
    ):
        raise ValueError("runtime TLS/SPKI evidence must contain two unique SHA-256 pins")
    if not isinstance(evidence.get("backend_revision"), str) or not evidence["backend_revision"]:
        raise ValueError("runtime backend revision is missing")
    device = evidence.get("authenticated_device")
    if (
        not isinstance(device, Mapping)
        or not device.get("serial")
        or device.get("authenticated_before") is not True
        or device.get("authenticated_after") is not True
        or device.get("state_preserved") is not True
    ):
        raise ValueError("authenticated device evidence is missing or state was not preserved")
    install = evidence.get("runtime_install")
    if (
        not isinstance(install, Mapping)
        or install.get("package") != "dev.whysoezzy.meet"
        or install.get("installed") is not True
        or install.get("state_preserved") is not True
    ):
        raise ValueError("runtime install evidence is incomplete")
    if evidence.get("runtime_authenticated_api") is not True:
        raise ValueError("authenticated runtime API evidence is missing")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    try:
        evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
        if not isinstance(evidence, dict):
            raise ValueError("runtime evidence must be an object")
        verify(evidence)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"runtime publication gate failed: {error}")
        return 1
    print("runtime publication gate passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
