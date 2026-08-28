import os
import stat
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

import android_sdk_tools
from android_sdk_tools import (
    AndroidSdkToolError,
    _APKANALYZER_PROBE_TIMEOUT_SECONDS,
    resolve_apkanalyzer,
    resolve_apksigner,
)


class AndroidSdkToolsTest(unittest.TestCase):
    def setUp(self):
        self.platform = patch.object(android_sdk_tools.sys, "platform", "linux")
        self.platform.start()
        self.addCleanup(self.platform.stop)

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

    @staticmethod
    def make_analyzer_sdk(root, packages, launchers=("apkanalyzer",)):
        command_line_tools = root / "cmdline-tools"
        command_line_tools.mkdir()
        for installation_directory, revision, package_path, valid in packages:
            package = command_line_tools / installation_directory
            (package / "bin").mkdir(parents=True)
            (package / "source.properties").write_text(
                f"Pkg.Revision = {revision}\nPkg.Path = {package_path}\n",
                encoding="utf-8",
            )
            if valid:
                for launcher in launchers:
                    tool = package / "bin" / launcher
                    tool.write_text("#!/bin/sh\n", encoding="utf-8")
                    tool.chmod(tool.stat().st_mode | stat.S_IXUSR)

    @staticmethod
    def successful_probe(*_args, **_kwargs):
        return type(
            "Completed",
            (),
            {
                "returncode": 0,
                "stdout": "",
                "stderr": "Usage:\napkanalyzer [global options] <subject> <verb> [options] <apk> [<apk2>]\n",
            },
        )()

    def test_apkanalyzer_uses_unique_highest_metadata_revision(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root,
                [
                    ("latest", "12.0", "cmdline-tools;12.0", True),
                    ("11.0", "11.0", "cmdline-tools;11.0", True),
                ],
            )
            with patch("android_sdk_tools.subprocess.run", self.successful_probe):
                self.assertEqual(
                    resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)}),
                    (root / "cmdline-tools/latest/bin/apkanalyzer").resolve(),
                )

    def test_apkanalyzer_accepts_versioned_package_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("12.0", "12.0", "cmdline-tools;12.0", True)]
            )
            with patch("android_sdk_tools.subprocess.run", self.successful_probe):
                self.assertEqual(
                    resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)}),
                    (root / "cmdline-tools/12.0/bin/apkanalyzer").resolve(),
                )

    def test_apkanalyzer_non_windows_prefers_extensionless_launcher(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root,
                [("latest", "14.0.0", "cmdline-tools;14.0.0", True)],
                launchers=("apkanalyzer", "apkanalyzer.bat"),
            )
            with patch("android_sdk_tools.subprocess.run", self.successful_probe):
                self.assertEqual(
                    resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)}),
                    (root / "cmdline-tools/latest/bin/apkanalyzer").resolve(),
                )

    def test_apkanalyzer_rejects_missing_package_path_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            metadata = root / "cmdline-tools/latest/source.properties"
            metadata.write_text("Pkg.Revision = 14.0.0\n", encoding="utf-8")
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_rejects_mismatched_package_path_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            metadata = root / "cmdline-tools/latest/source.properties"
            metadata.write_text(
                "Pkg.Revision = 14.0.0\nPkg.Path = cmdline-tools;21.0\n",
                encoding="utf-8",
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_rejects_alias_metadata_for_versioned_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("22.0", "22.0.0", "cmdline-tools;latest", True)]
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_rejects_normalized_equivalent_package_path_metadata(self):
        for revision, package_path in (
            ("12.0", "cmdline-tools;12.0.0"),
            ("12.0.0", "cmdline-tools;12.0"),
        ):
            with self.subTest(revision=revision, package_path=package_path):
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    self.make_analyzer_sdk(
                        root, [("latest", revision, package_path, True)]
                    )
                    with self.assertRaises(AndroidSdkToolError):
                        resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_rejects_malformed_package_path_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0 extra", True)]
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_rejects_duplicate_package_path_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            metadata = root / "cmdline-tools/latest/source.properties"
            metadata.write_text(
                "Pkg.Revision = 14.0.0\n"
                "Pkg.Path = cmdline-tools;14.0.0\n"
                "Pkg.Path = cmdline-tools;14.0.0\n",
                encoding="utf-8",
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_accepts_crlf_identity_probe(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            result = type(
                "Completed",
                (),
                {
                    "returncode": 0,
                    "stdout": "",
                    "stderr": "Usage:\r\napkanalyzer [global options] <subject> <verb> [options] <apk> [<apk2>]\r\n",
                },
            )()
            with patch("android_sdk_tools.subprocess.run", return_value=result):
                self.assertTrue(resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)}).is_absolute())

    def test_apkanalyzer_windows_prefers_bat_launcher_and_allows_batch_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root,
                [("latest", "14.0.0", "cmdline-tools;14.0.0", True)],
                launchers=("apkanalyzer", "apkanalyzer.bat"),
            )
            batch_file = root / "cmdline-tools/latest/bin/apkanalyzer.bat"
            batch_file.chmod(batch_file.stat().st_mode & ~stat.S_IXUSR)
            with patch.object(android_sdk_tools.sys, "platform", "win32"):
                with patch(
                    "android_sdk_tools.subprocess.run",
                    side_effect=self.successful_probe,
                ) as probe:
                    self.assertEqual(
                        resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)}),
                        batch_file.resolve(),
                    )
            probe.assert_called_once_with(
                [str(batch_file.resolve())],
                capture_output=True,
                check=False,
                text=True,
                timeout=_APKANALYZER_PROBE_TIMEOUT_SECONDS,
            )

    def test_apkanalyzer_windows_does_not_fallback_to_extensionless_launcher(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            with patch.object(android_sdk_tools.sys, "platform", "win32"):
                with patch(
                    "android_sdk_tools.subprocess.run",
                    side_effect=self.successful_probe,
                ) as probe:
                    with self.assertRaises(AndroidSdkToolError):
                        resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})
            probe.assert_not_called()

    def test_apkanalyzer_failures_are_rejected_without_downgrade(self):
        for result in (
            type("Completed", (), {"returncode": 1, "stdout": "", "stderr": ""})(),
            type("Completed", (), {"returncode": 0, "stdout": "unexpected", "stderr": ""})(),
            type("Completed", (), {"returncode": 0, "stdout": "", "stderr": ""})(),
            type("Completed", (), {"returncode": 0, "stdout": "", "stderr": "wrong"})(),
        ):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.make_analyzer_sdk(
                    root,
                    [
                        ("old", "13.0.0", "cmdline-tools;13.0.0", True),
                        ("latest", "14.0.0", "cmdline-tools;14.0.0", True),
                    ],
                )
                with patch("android_sdk_tools.subprocess.run", return_value=result):
                    with self.assertRaises(AndroidSdkToolError):
                        resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_probe_timeout_is_30_seconds(self):
        self.assertEqual(_APKANALYZER_PROBE_TIMEOUT_SECONDS, 30)

    def test_apkanalyzer_probe_timeout_and_launch_errors_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            for error in (OSError("launch"), UnicodeError("decode")):
                with patch(
                    "android_sdk_tools.subprocess.run", side_effect=error
                ):
                    with self.assertRaises(AndroidSdkToolError):
                        resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})
            with patch(
                "android_sdk_tools.subprocess.run",
                side_effect=__import__("subprocess").TimeoutExpired(
                    ["apkanalyzer"], _APKANALYZER_PROBE_TIMEOUT_SECONDS
                ),
            ):
                with self.assertRaises(AndroidSdkToolError):
                    resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_probe_uses_exact_bounded_command(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            with patch(
                "android_sdk_tools.subprocess.run", side_effect=self.successful_probe
            ) as probe:
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})
            probe.assert_called_once_with(
                [str((root / "cmdline-tools/latest/bin/apkanalyzer").resolve())],
                capture_output=True,
                check=False,
                text=True,
                timeout=_APKANALYZER_PROBE_TIMEOUT_SECONDS,
            )

    def test_apkanalyzer_selected_package_tool_failures_are_rejected(self):
        for valid, executable_mode in ((False, None), (True, 0)):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.make_analyzer_sdk(
                    root, [("latest", "14.0.0", "cmdline-tools;14.0.0", valid)]
                )
                if executable_mode == 0:
                    tool = root / "cmdline-tools/latest/bin/apkanalyzer"
                    tool.chmod(tool.stat().st_mode & ~stat.S_IXUSR)
                with self.assertRaises(AndroidSdkToolError):
                    resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_equal_highest_or_invalid_selected_package_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root,
                [
                    ("one", "14.0.0", "cmdline-tools;14.0.0", True),
                    ("two", "14.0.0", "cmdline-tools;14.0.0", True),
                ],
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})
            (root / "cmdline-tools/two/source.properties").write_text(
                "Pkg.Revision = broken\n", encoding="utf-8"
            )
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_package_symlink_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            root = Path(directory)
            outside_root = Path(outside)
            self.make_analyzer_sdk(
                root, [("valid", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            escaped = outside_root / "escaped"
            (escaped / "bin").mkdir(parents=True)
            (escaped / "source.properties").write_text(
                "Pkg.Revision = 15.0.0\n", encoding="utf-8"
            )
            tool = escaped / "bin" / "apkanalyzer"
            tool.write_text("#!/bin/sh\n", encoding="utf-8")
            tool.chmod(tool.stat().st_mode | stat.S_IXUSR)
            package_link = root / "cmdline-tools/escaped"
            try:
                package_link.symlink_to(escaped, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are unavailable in this environment")
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_cmdline_tools_symlink_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            root = Path(directory)
            outside_root = Path(outside)
            self.make_analyzer_sdk(
                outside_root,
                [("latest", "14.0.0", "cmdline-tools;14.0.0", True)],
            )
            command_line_tools = root / "cmdline-tools"
            try:
                command_line_tools.symlink_to(
                    outside_root / "cmdline-tools", target_is_directory=True
                )
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are unavailable in this environment")
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_source_properties_symlink_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            root = Path(directory)
            outside_root = Path(outside)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            external_metadata = outside_root / "source.properties"
            external_metadata.write_text("Pkg.Revision = 15.0.0\n", encoding="utf-8")
            metadata = root / "cmdline-tools/latest/source.properties"
            metadata.unlink()
            try:
                metadata.symlink_to(external_metadata)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are unavailable in this environment")
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_apkanalyzer_source_properties_symlink_loop_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_analyzer_sdk(
                root, [("latest", "14.0.0", "cmdline-tools;14.0.0", True)]
            )
            metadata = root / "cmdline-tools/latest/source.properties"
            metadata.unlink()
            try:
                metadata.symlink_to(metadata)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are unavailable in this environment")
            with self.assertRaises(AndroidSdkToolError):
                resolve_apkanalyzer({"ANDROID_SDK_ROOT": str(root)})

    def test_cli_default_and_explicit_success_contract(self):
        expected = Path("/sdk/build-tools/36.1.0/apksigner")
        for argv in (["android_sdk_tools.py"], ["android_sdk_tools.py", "apksigner"]):
            with patch("android_sdk_tools.resolve_apksigner", return_value=expected):
                with patch("sys.argv", argv), redirect_stdout(StringIO()) as stdout, redirect_stderr(
                    StringIO()
                ) as stderr:
                    self.assertEqual(__import__("android_sdk_tools").main(), 0)
            self.assertEqual(stdout.getvalue(), f"{expected}\n")
            self.assertEqual(stderr.getvalue(), "")

    def test_cli_analyzer_failure_contract(self):
        with patch(
            "android_sdk_tools.resolve_apkanalyzer",
            side_effect=AndroidSdkToolError("identity probe failed"),
        ):
            with patch("sys.argv", ["android_sdk_tools.py", "apkanalyzer"]):
                with redirect_stdout(StringIO()) as stdout, redirect_stderr(StringIO()) as stderr:
                    self.assertEqual(__import__("android_sdk_tools").main(), 1)
        self.assertEqual(stdout.getvalue(), "")
        self.assertEqual(
            stderr.getvalue(),
            "Android SDK apkanalyzer resolution failed: identity probe failed\n",
        )

    def test_cli_unknown_or_extra_arguments_use_argparse_exit_two(self):
        for argv in (
            ["android_sdk_tools.py", "other"],
            ["android_sdk_tools.py", "apksigner", "extra"],
        ):
            with patch("sys.argv", argv):
                with self.assertRaises(SystemExit) as error:
                    __import__("android_sdk_tools").main()
            self.assertEqual(error.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
