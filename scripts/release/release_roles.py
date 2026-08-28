#!/usr/bin/env python3
"""Load and assert the repository's strict release branch-role contract."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, NoReturn


class ReleaseRolesError(ValueError):
    """Raised when the release branch-role contract is invalid."""


class ReleaseRole(str, Enum):
    """The only roles supported by the release contract."""

    INTEGRATION = "integration"
    STABLE = "stable"


@dataclass(frozen=True, slots=True)
class ReleaseBranchAuthority:
    """The immutable branch authority assigned to one release role."""

    role: ReleaseRole
    branch: str

    def __post_init__(self) -> None:
        if not isinstance(self.role, ReleaseRole):
            raise ReleaseRolesError("release role is not recognized")
        validate_canonical_branch(self.branch)

    @property
    def head_ref(self) -> str:
        """Return the fully-qualified Git head ref for this authority."""

        return f"refs/heads/{self.branch}"


@dataclass(frozen=True, slots=True)
class ReleaseBranchRoles:
    """The complete immutable release role assignment."""

    integration: ReleaseBranchAuthority
    stable: ReleaseBranchAuthority

    def __post_init__(self) -> None:
        if not isinstance(self.integration, ReleaseBranchAuthority):
            raise ReleaseRolesError("integration authority is invalid")
        if not isinstance(self.stable, ReleaseBranchAuthority):
            raise ReleaseRolesError("stable authority is invalid")
        if self.integration.role is not ReleaseRole.INTEGRATION:
            raise ReleaseRolesError("integration authority has the wrong role")
        if self.stable.role is not ReleaseRole.STABLE:
            raise ReleaseRolesError("stable authority has the wrong role")
        if self.integration.branch == self.stable.branch:
            raise ReleaseRolesError("integration and stable branches must differ")

    def authority(self, role: ReleaseRole) -> ReleaseBranchAuthority:
        """Return the authority for an exact :class:`ReleaseRole` value."""

        if not isinstance(role, ReleaseRole):
            raise ReleaseRolesError("release role is not recognized")
        if role is ReleaseRole.INTEGRATION:
            return self.integration
        return self.stable


_BRANCH_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._/-]*\Z")
_EXPECTED_ROOT_KEYS = frozenset({"schemaVersion", "roles"})
_EXPECTED_ROLE_KEYS = frozenset({"integration", "stable"})
_EXPECTED_AUTHORITY_KEYS = frozenset({"branch"})
_REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_RELEASE_ROLES_PATH = _REPOSITORY_ROOT / "release-roles.json"


def _duplicate_safe_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    values: dict[str, Any] = {}
    for key, value in pairs:
        if key in values:
            raise ReleaseRolesError(f"duplicate JSON object member: {key}")
        values[key] = value
    return values


def _reject_json_constant(value: str) -> NoReturn:
    raise ReleaseRolesError(f"invalid JSON constant: {value}")


def validate_canonical_branch(branch: Any) -> str:
    """Validate and return one canonical Git branch name.

    Release identity callers use this same in-process grammar instead of
    trusting a role file or a repeated caller assertion to have validated it.
    """

    if not isinstance(branch, str):
        raise ReleaseRolesError("branch must be a string")
    if _BRANCH_PATTERN.fullmatch(branch) is None or branch.startswith("refs/"):
        raise ReleaseRolesError(f"branch is not Git-compatible: {branch!r}")
    if (
        branch.endswith("/")
        or branch.endswith(".")
        or "//" in branch
        or ".." in branch
        or "@{" in branch
    ):
        raise ReleaseRolesError(f"branch is not Git-compatible: {branch!r}")
    components = branch.split("/")
    if any(component.startswith(".") for component in components):
        raise ReleaseRolesError(f"branch is not Git-compatible: {branch!r}")
    if any(component.endswith(".lock") for component in components):
        raise ReleaseRolesError(f"branch is not Git-compatible: {branch!r}")
    return branch


def _require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReleaseRolesError(f"{label} must be an object")
    return value


def _require_exact_keys(value: dict[str, Any], expected: frozenset[str], label: str) -> None:
    actual = frozenset(value)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        details: list[str] = []
        if missing:
            details.append(f"missing {', '.join(missing)}")
        if extra:
            details.append(f"unexpected {', '.join(extra)}")
        suffix = f" ({'; '.join(details)})" if details else ""
        raise ReleaseRolesError(f"{label} has the wrong keys{suffix}")


def _authority(value: Any, role: ReleaseRole) -> ReleaseBranchAuthority:
    mapping = _require_object(value, f"{role.value} role")
    _require_exact_keys(mapping, _EXPECTED_AUTHORITY_KEYS, f"{role.value} role")
    branch = mapping["branch"]
    if not isinstance(branch, str):
        raise ReleaseRolesError(f"{role.value} branch must be a string")
    return ReleaseBranchAuthority(role, validate_canonical_branch(branch))


def load_release_roles(path: Path | None = None) -> ReleaseBranchRoles:
    """Load and strictly validate the repository role document.

    JSON object members are checked for duplicates while parsing, before any
    object can collapse repeated keys into a last-value-wins dictionary.
    """

    roles_path = DEFAULT_RELEASE_ROLES_PATH if path is None else Path(path)
    try:
        text = roles_path.read_text(encoding="utf-8")
        document = json.loads(
            text,
            object_pairs_hook=_duplicate_safe_object,
            parse_constant=_reject_json_constant,
        )
    except ReleaseRolesError:
        raise
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ReleaseRolesError(f"cannot load release roles from {roles_path}: {error}") from error

    root = _require_object(document, "release roles")
    _require_exact_keys(root, _EXPECTED_ROOT_KEYS, "release roles")
    schema_version = root["schemaVersion"]
    if isinstance(schema_version, bool) or not isinstance(schema_version, int):
        raise ReleaseRolesError("schemaVersion must be integer 1")
    if schema_version != 1:
        raise ReleaseRolesError("unsupported release roles schemaVersion")

    role_values = _require_object(root["roles"], "roles")
    _require_exact_keys(role_values, _EXPECTED_ROLE_KEYS, "roles")
    return ReleaseBranchRoles(
        integration=_authority(role_values["integration"], ReleaseRole.INTEGRATION),
        stable=_authority(role_values["stable"], ReleaseRole.STABLE),
    )


def assert_ref(
    role: ReleaseRole,
    ref: str,
    *,
    path: Path | None = None,
) -> None:
    """Assert that ``ref`` is the exact head ref for ``role``."""

    if not isinstance(role, ReleaseRole):
        raise ReleaseRolesError("release role is not recognized")
    if not isinstance(ref, str):
        raise ReleaseRolesError("observed ref must be a string")
    expected = load_release_roles(path).authority(role).head_ref
    if ref != expected:
        raise ReleaseRolesError(f"expected {expected}; observed {ref}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    assert_ref_parser = commands.add_parser(
        "assert-ref",
        help="assert that a ref belongs to the selected release role",
    )
    assert_ref_parser.add_argument(
        "--role",
        choices=[role.value for role in ReleaseRole],
        required=True,
    )
    assert_ref_parser.add_argument("--ref", required=True)
    assert_ref_parser.add_argument(
        "--path",
        type=Path,
        default=None,
        help="role document to load (defaults to the repository root)",
    )
    args = parser.parse_args(argv)

    try:
        assert_ref(ReleaseRole(args.role), args.ref, path=args.path)
    except (ReleaseRolesError, OSError, ValueError) as error:
        print(f"release role assertion failed: {error}", file=sys.stderr)
        return 1
    print("release role assertion passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
