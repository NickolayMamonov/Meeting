import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from release_roles import (
    DEFAULT_RELEASE_ROLES_PATH,
    ReleaseBranchAuthority,
    ReleaseBranchRoles,
    ReleaseRole,
    ReleaseRolesError,
    assert_ref,
    load_release_roles,
    main,
)


FIXTURE_DIR = Path(__file__).parent / "fixtures" / "release-roles"


class ReleaseRolesTest(unittest.TestCase):
    def test_default_loader_reads_repository_root_contract(self):
        roles = load_release_roles()

        self.assertEqual(DEFAULT_RELEASE_ROLES_PATH, Path(__file__).parents[2] / "release-roles.json")
        self.assertEqual(roles.integration.role, ReleaseRole.INTEGRATION)
        self.assertEqual(roles.integration.branch, "dev")
        self.assertEqual(roles.integration.head_ref, "refs/heads/dev")
        self.assertEqual(roles.stable.role, ReleaseRole.STABLE)
        self.assertEqual(roles.stable.branch, "master")
        self.assertEqual(roles.stable.head_ref, "refs/heads/master")
        self.assertIs(roles.authority(ReleaseRole.INTEGRATION), roles.integration)
        self.assertIs(roles.authority(ReleaseRole.STABLE), roles.stable)

    def test_valid_fixture_matches_default_contract(self):
        self.assertEqual(
            load_release_roles(FIXTURE_DIR / "valid.json"),
            load_release_roles(DEFAULT_RELEASE_ROLES_PATH),
        )

    def test_authorities_and_roles_are_frozen(self):
        authority = ReleaseBranchAuthority(ReleaseRole.INTEGRATION, "dev")
        roles = ReleaseBranchRoles(
            integration=authority,
            stable=ReleaseBranchAuthority(ReleaseRole.STABLE, "master"),
        )

        with self.assertRaises((AttributeError, TypeError)):
            authority.branch = "master"
        with self.assertRaises((AttributeError, TypeError)):
            roles.stable = authority

    def test_rejects_all_duplicate_member_fixtures_before_schema_validation(self):
        fixtures = sorted(FIXTURE_DIR.glob("duplicate-*.json"))

        self.assertGreaterEqual(len(fixtures), 11)
        for fixture in fixtures:
            with self.subTest(fixture=fixture.name):
                with self.assertRaises(ReleaseRolesError):
                    load_release_roles(fixture)

    def test_rejects_duplicate_members_through_assert_ref_cli(self):
        for fixture in sorted(FIXTURE_DIR.glob("duplicate-*.json")):
            output = io.StringIO()
            with self.subTest(fixture=fixture.name), patch(
                "sys.stderr", output
            ):
                result = main(
                    [
                        "assert-ref",
                        "--role",
                        "integration",
                        "--ref",
                        "refs/heads/dev",
                        "--path",
                        str(fixture),
                    ]
                )
            self.assertEqual(result, 1)
            self.assertIn("release role assertion failed:", output.getvalue())

    def test_cli_accepts_matching_ref_and_rejects_mismatch(self):
        output = io.StringIO()
        with patch("sys.stdout", output):
            self.assertEqual(
                main(
                    [
                        "assert-ref",
                        "--role",
                        "integration",
                        "--ref",
                        "refs/heads/dev",
                    ]
                ),
                0,
            )
        self.assertIn("passed", output.getvalue())

        output = io.StringIO()
        with patch("sys.stderr", output):
            self.assertEqual(
                main(
                    [
                        "assert-ref",
                        "--role",
                        "stable",
                        "--ref",
                        "refs/heads/dev",
                    ]
                ),
                1,
            )
        self.assertIn("expected refs/heads/master", output.getvalue())
        self.assertIn("observed refs/heads/dev", output.getvalue())

    def test_rejects_noncanonical_branches_identically_at_authority_boundary(self):
        invalid_branches = (
            "dev/.hidden",
            "dev/./child",
            "dev/foo.lock",
            "dev/nested/.hidden",
            "dev/nested/foo.lock",
            "dev//child",
            "dev/../child",
            "dev/@{child}",
            "dev/",
            "/dev",
            "dev.",
            "dev with-space",
            "dev\\child",
            "refs/tags/dev",
            "refs/remotes/origin/dev",
        )
        for branch in invalid_branches:
            with self.subTest(branch=branch), self.assertRaises(ReleaseRolesError):
                ReleaseBranchAuthority(ReleaseRole.INTEGRATION, branch)

    def test_accepts_reachable_git_branch_grammar(self):
        for branch in ("dev", "master", "release/1.0", "feature/foo.bar", "dev/FOO.LOCK"):
            with self.subTest(branch=branch):
                authority = ReleaseBranchAuthority(ReleaseRole.INTEGRATION, branch)
                self.assertEqual(authority.head_ref, f"refs/heads/{branch}")

    def test_rejects_schema_shape_and_branch_assignment_errors(self):
        cases = {
            "missing root key": {"roles": {}},
            "extra root key": {"schemaVersion": 1, "roles": {}, "extra": True},
            "unsupported version": {"schemaVersion": 2, "roles": {}},
            "boolean version": {"schemaVersion": True, "roles": {}},
            "missing role": {"schemaVersion": 1, "roles": {"integration": {"branch": "dev"}}},
            "extra role": {
                "schemaVersion": 1,
                "roles": {
                    "integration": {"branch": "dev"},
                    "stable": {"branch": "master"},
                    "other": {"branch": "feature"},
                },
            },
            "extra authority key": {
                "schemaVersion": 1,
                "roles": {
                    "integration": {"branch": "dev", "extra": "x"},
                    "stable": {"branch": "master"},
                },
            },
            "equal branches": {
                "schemaVersion": 1,
                "roles": {
                    "integration": {"branch": "same"},
                    "stable": {"branch": "same"},
                },
            },
            "full ref": {
                "schemaVersion": 1,
                "roles": {
                    "integration": {"branch": "refs/heads/dev"},
                    "stable": {"branch": "master"},
                },
            },
        }
        for label, document in cases.items():
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "roles.json"
                path.write_text(json.dumps(document), encoding="utf-8")
                with self.subTest(label=label), self.assertRaises(ReleaseRolesError):
                    load_release_roles(path)

    def test_rejects_non_json_constants_and_invalid_utf8(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "roles.json"
            path.write_text(
                '{"schemaVersion": NaN, "roles": {}}',
                encoding="utf-8",
            )
            with self.assertRaises(ReleaseRolesError):
                load_release_roles(path)
            path.write_bytes(b"\xff")
            with self.assertRaises(ReleaseRolesError):
                load_release_roles(path)


if __name__ == "__main__":
    unittest.main()
