#!/usr/bin/env python3
"""Emit the small protected-master producer evidence artifact.

The artifact is deliberately boring: it binds the producer workflow execution
to the protected ref and run identity. The consumer independently enumerates
the run/artifact pair through the GitHub API and verifies these claims after
download, so a stale artifact cannot be substituted for a newer producer run.
"""

from __future__ import annotations

import json
import os
from pathlib import Path


def required(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise SystemExit(f"{name} is required")
    return value


def main() -> int:
    output = Path(os.environ.get("OUTPUT", "credential-audit-evidence.json"))
    run_id = int(required("GITHUB_RUN_ID"))
    run_number = int(required("GITHUB_RUN_NUMBER"))
    run_attempt = int(os.environ.get("GITHUB_RUN_ATTEMPT", "1"))
    payload = {
        "schema": 1,
        "repository": required("GITHUB_REPOSITORY"),
        "workflow_path": required("WORKFLOW_PATH"),
        "ref": required("GITHUB_REF"),
        "sha": required("GITHUB_SHA"),
        "run_id": run_id,
        "run_number": run_number,
        "run_attempt": run_attempt,
        "status": "completed",
        "conclusion": "success",
    }
    if payload["ref"] != "refs/heads/master":
        raise SystemExit("producer evidence must be emitted only from protected master")
    output.write_text(json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
