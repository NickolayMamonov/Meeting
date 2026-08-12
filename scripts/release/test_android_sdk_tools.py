import os
import stat
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from android_sdk_tools import AndroidSdkToolError, resolve_apksigner


class AndroidSdkToolsTest(unittest.TestCase):
    @staticmethod
    def make_sdk(root, versions):
        build_tools = root / "build-tools"
        build_tools.mkdir()
        for version, valid in versions:
            package = build_tools / version
            package.mkdir()
            (package / "source.properties").write_text(
                f"Pkg.Revision = {version}\n", encoding="utf-8"
            )
            if valid:
                tool = package / "apksigner"
                tool.write_text("#!/bin/sh\n", encoding="utf-8")
                tool.chmod(tool.stat().st_mode | stat.S_IXUSR)

    @staticmethod
    def successful_tool(*_args, **_kwargs):
        return type(
            "Completed",
            (),
            {"returncode": 0, "stdout": "0.9\n", "stderr": ""},
        )()

    def test_numeric_highest_and_matching_root_alias(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_sdk(root, [("35.0.9", True), ("35.0.10", True)])
            environment = {"ANDROID_SDK_ROOT": str(root), "ANDROID_HOME": str(root)}
            with patch("android_sdk_tools.subprocess.run", self.successful_tool):
                self.assertEqual(
                    resolve_apksigner(environment),
                    (root / "build-tools/35.0.10/apksigner").resolve(),
                )

    def test_equal_highest_is_ambiguous(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_sdk(root, [("35.0", True), ("35.0.0", True)])
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})

    def test_broken_highest_does_not_downgrade(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_sdk(root, [("35.0.0", True), ("36.1.0", False)])
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})

    def test_package_revision_and_tool_version_are_validated(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_sdk(root, [("36.1.0", True)])
            package = root / "build-tools/36.1.0"
            (package / "source.properties").write_text(
                "Pkg.Revision = 35.0.0\n", encoding="utf-8"
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})

            (package / "source.properties").write_text(
                "Pkg.Revision = 36.1.0\n", encoding="utf-8"
            )
            with patch(
                "android_sdk_tools.subprocess.run",
                return_value=type(
                    "Completed",
                    (),
                    {"returncode": 0, "stdout": "bad\n", "stderr": ""},
                )(),
            ):
                with self.assertRaises(AndroidSdkToolError):
                    resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})

    def test_duplicate_or_missing_package_metadata_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_sdk(root, [("36.1.0", True)])
            package = root / "build-tools/36.1.0"
            (package / "source.properties").write_text(
                "Pkg.Revision = 36.1.0\nPkg.Revision = 36.1.0\n",
                encoding="utf-8",
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})
            (package / "source.properties").write_text("Path = x\n", encoding="utf-8")
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})

    def test_missing_tool_and_version_failures_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_sdk(root, [("36.1.0", True)])
            package = root / "build-tools/36.1.0"
            tool = package / "apksigner"
            tool.unlink()
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})
            tool.write_text("#!/bin/sh\n", encoding="utf-8")
            tool.chmod(tool.stat().st_mode | stat.S_IXUSR)
            for result in (
                {"returncode": 1, "stdout": "0.9\n", "stderr": ""},
                {"returncode": 0, "stdout": "0.9\n", "stderr": "warning\n"},
                {"returncode": 0, "stdout": "0.9\n0.10\n", "stderr": ""},
            ):
                with patch(
                    "android_sdk_tools.subprocess.run",
                    return_value=type("Completed", (), result)(),
                ):
                    with self.assertRaises(AndroidSdkToolError):
                        resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})

    def test_symlink_escape_and_non_executable_tool_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            root = Path(directory)
            self.make_sdk(root, [("36.1.0", True)])
            package = root / "build-tools/36.1.0"
            tool = package / "apksigner"
            tool.chmod(tool.stat().st_mode & ~stat.S_IXUSR)
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})
            tool.unlink()
            escaped = Path(outside) / "apksigner"
            escaped.write_text("#!/bin/sh\n", encoding="utf-8")
            escaped.chmod(escaped.stat().st_mode | stat.S_IXUSR)
            try:
                tool.symlink_to(escaped)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are unavailable in this environment")
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner({"ANDROID_SDK_ROOT": str(root)})

    def test_conflicting_roots_are_rejected(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            self.make_sdk(Path(first), [("36.1.0", True)])
            self.make_sdk(Path(second), [("36.1.0", True)])
            with self.assertRaises(AndroidSdkToolError):
                resolve_apksigner(
                    {"ANDROID_SDK_ROOT": first, "ANDROID_HOME": second}
                )

    def test_missing_root_is_rejected(self):
        with self.assertRaises(AndroidSdkToolError):
            resolve_apksigner({})


if __name__ == "__main__":
    unittest.main()
