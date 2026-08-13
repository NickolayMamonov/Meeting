import copy
import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
CONFIG_PATH = ROOT / "release-please-config.json"
MANIFEST_PATH = ROOT / ".release-please-manifest.json"
VERSION_JSON_PATH = ROOT / "version.json"
VERSION_TEXT_PATH = ROOT / "version.txt"
RELEASE_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "release.yml"
BOOTSTRAP_SHA = "a4defd547446ea83fae3e6d87a906818ecac4630"
ACTION_SHA = "a02a34c4d625f9be7cb89156071d8567266a2445"
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def updater_spec(config):
    package = config["packages"]["."]
    version_file = package.get("version-file")
    extra_files = package.get("extra-files")
    if version_file == "version.json":
        raise ValueError("Simple version-file must not be version.json")
    if version_file != "version.txt":
        raise ValueError("Simple version-file must be version.txt")
    if not isinstance(extra_files, list) or len(extra_files) != 1:
        raise ValueError("exactly one extra-file updater is required")
    updater = extra_files[0]
    if not isinstance(updater, dict):
        raise ValueError("version.json must use a typed updater")
    if updater != {
        "type": "json",
        "path": "version.json",
        "jsonpath": "$.version",
    }:
        raise ValueError("unsupported version.json updater")
    paths = [version_file] + [
        item if isinstance(item, str) else item.get("path") for item in extra_files
    ]
    if len(paths) != len(set(paths)):
        raise ValueError("duplicate updater path")
    return updater


def apply_json_version(document, version):
    if set(document) != {"version"} or not isinstance(document["version"], str):
        raise ValueError("version.json must contain only a string version")
    updated = copy.deepcopy(document)
    updated["version"] = version
    return updated


def collect_after_bootstrap(commits, boundary):
    boundary_index = commits.index(boundary)
    return commits[boundary_index + 1 :]


def validate_repository_state(config, manifest, version_document, version_text):
    updater_spec(config)
    if not isinstance(version_document, dict):
        raise ValueError("version.json must be an object")
    if set(version_document) != {"version"} or not isinstance(
        version_document["version"], str
    ):
        raise ValueError("version.json must contain only a string version")
    version = version_document["version"]
    if not SEMVER.fullmatch(version) or version_text.strip() != version:
        raise ValueError("version transports are not synchronized")
    if not isinstance(manifest, dict):
        raise ValueError("manifest must be an object")
    if manifest == {}:
        return
    if set(manifest) != {"."} or not isinstance(manifest["."], str):
        raise ValueError("manifest must be empty or one root entry")
    if manifest["."] != version or not SEMVER.fullmatch(manifest["."]):
        raise ValueError("generated manifest is not synchronized")


class ReleasePleaseConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = read_json(CONFIG_PATH)
        cls.manifest = read_json(MANIFEST_PATH)
        cls.version_document = read_json(VERSION_JSON_PATH)
        cls.version_text = VERSION_TEXT_PATH.read_text(encoding="utf-8")
        cls.workflow = RELEASE_WORKFLOW_PATH.read_text(encoding="utf-8")

    def test_pinned_workflow_and_preserved_release_settings(self):
        self.assertIn(
            f"googleapis/release-please-action@{ACTION_SHA} # v4.2.0",
            self.workflow,
        )
        self.assertIn("branches: [dev]", self.workflow)
        self.assertIn("target-branch: dev", self.workflow)
        self.assertIn("config-file: release-please-config.json", self.workflow)
        self.assertIn("manifest-file: .release-please-manifest.json", self.workflow)
        self.assertEqual(
            self.config["$schema"],
            "https://raw.githubusercontent.com/googleapis/release-please/main/schemas/config.json",
        )
        self.assertEqual(self.config["release-type"], "simple")
        self.assertTrue(self.config["bump-minor-pre-major"])
        self.assertTrue(self.config["include-v-in-tag"])
        self.assertEqual(self.config["initial-version"], "1.0.0")
        self.assertTrue(self.config["draft"])
        self.assertEqual(self.config["target-branch"], "dev")
        self.assertEqual(self.config["changelog-path"], "CHANGELOG.md")
        self.assertEqual(self.config["bootstrap-sha"], BOOTSTRAP_SHA)

    def test_simple_and_typed_json_updaters_have_one_authority_each(self):
        self.assertEqual(
            updater_spec(self.config),
            {"type": "json", "path": "version.json", "jsonpath": "$.version"},
        )

    def test_previous_duplicate_string_updater_shape_is_rejected(self):
        bad = copy.deepcopy(self.config)
        bad["packages"]["."]["version-file"] = "version.json"
        bad["packages"]["."]["extra-files"] = ["version.json"]
        with self.assertRaises(ValueError):
            updater_spec(bad)

    def test_only_the_supported_typed_json_shape_is_accepted(self):
        for extra_files in (
            ["version.json"],
            [{"type": "json", "path": "version.json"}],
            [{"type": "json", "path": "version.json", "jsonpath": "$"}],
            [{"type": "yaml", "path": "version.json", "jsonpath": "$.version"}],
            [
                {"type": "json", "path": "version.json", "jsonpath": "$.version"},
                {"type": "json", "path": "version.json", "jsonpath": "$.version"},
            ],
        ):
            with self.subTest(extra_files=extra_files):
                bad = copy.deepcopy(self.config)
                bad["packages"]["."]["extra-files"] = extra_files
                with self.assertRaises(ValueError):
                    updater_spec(bad)

    def test_typed_json_update_changes_only_the_version_field(self):
        original = copy.deepcopy(self.version_document)
        updated = apply_json_version(original, "1.1.0")
        self.assertEqual(set(updated), {"version"})
        self.assertEqual(updated["version"], "1.1.0")
        self.assertEqual(original, {"version": "1.0.0"})

    def test_bootstrap_collection_is_exclusive_without_repository_history(self):
        commits = ["before-bootstrap", BOOTSTRAP_SHA, "after-one", "after-two"]
        self.assertEqual(
            collect_after_bootstrap(commits, BOOTSTRAP_SHA),
            ["after-one", "after-two"],
        )
        self.assertNotIn(
            BOOTSTRAP_SHA, collect_after_bootstrap(commits, BOOTSTRAP_SHA)
        )
        self.assertEqual(
            collect_after_bootstrap(
                ["older", "oldest", BOOTSTRAP_SHA, "release-fix"], BOOTSTRAP_SHA
            ),
            ["release-fix"],
        )

    def test_repository_accepts_only_coherent_bootstrap_or_generated_state(self):
        validate_repository_state(
            self.config, self.manifest, self.version_document, self.version_text
        )
        validate_repository_state(
            self.config, {".": "1.0.0"}, self.version_document, self.version_text
        )
        with self.assertRaises(ValueError):
            validate_repository_state(
                self.config, {"other": "1.0.0"}, self.version_document, self.version_text
            )
        with self.assertRaises(ValueError):
            validate_repository_state(
                self.config,
                {},
                {"version": "1.0.0", "extra": "field"},
                self.version_text,
            )
        with self.assertRaises(ValueError):
            validate_repository_state(
                self.config, {".": "1.1.0"}, self.version_document, self.version_text
            )


if __name__ == "__main__":
    unittest.main()
