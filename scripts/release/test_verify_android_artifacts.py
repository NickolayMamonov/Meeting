import argparse
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import verify_android_artifacts
from verify_android_artifacts import (
    ArtifactError,
    decode_debuggable_output,
    parse_expected_debuggable,
    run,
    verify_jarsigner_bundle,
    verify_rsa4096_signer,
)


class SignerInvariantTest(unittest.TestCase):
    def test_accepts_apksigner_rsa4096_output(self):
        verify_rsa4096_signer(
            "Signer #1 key algorithm: RSA\nSigner #1 key size (bits): 4096\n"
        )

    def test_accepts_keytool_rsa4096_output(self):
        verify_rsa4096_signer("Public Key Algorithm: 4096-bit RSA key\n")

    def test_rejects_non_rsa_or_wrong_key_size(self):
        with self.assertRaises(ArtifactError):
            verify_rsa4096_signer(
                "Signer #1 key algorithm: EC\nSigner #1 key size (bits): 256\n"
            )
        with self.assertRaises(ArtifactError):
            verify_rsa4096_signer(
                "Signer #1 key algorithm: RSA\nSigner #1 key size (bits): 2048\n"
            )


class JarsignerVerificationTest(unittest.TestCase):
    def test_accepts_verified_self_signed_pkix_and_no_timestamp_warnings(self):
        output = (
            "jar verified.\n"
            "Warning: This jar contains entries whose signer certificate is self-signed.\n"
            "Warning: This jar contains entries whose certificate chain is not trusted.\n"
            "Warning: This jar contains entries whose signer certificate is not timestamped.\n"
        )
        with patch.object(
            verify_android_artifacts, "run", return_value=output
        ) as invoked:
            verify_jarsigner_bundle(Path("app.aab"))
        command = invoked.call_args.args[0]
        self.assertEqual(
            command,
            ["jarsigner", "-verify", "-verbose", "-certs", "app.aab"],
        )
        self.assertNotIn("-strict", command)

    def test_rejects_missing_verification_success(self):
        with self.assertRaisesRegex(ArtifactError, "did not report"):
            with patch.object(
                verify_android_artifacts,
                "run",
                return_value="Warning: signer certificate is self-signed.\n",
            ):
                verify_jarsigner_bundle(Path("app.aab"))

    def test_rejects_unsigned_entry_and_jar_unsigned_warnings(self):
        for warning in (
            "jar verified.\nWarning: This jar contains unsigned entries.\n",
            "jar verified.\nWarning: jar is unsigned.\n",
            "jar verified.\nWarning: jar-unsigned entry.\n",
        ):
            with self.subTest(warning=warning):
                with self.assertRaisesRegex(ArtifactError, "unsigned"):
                    with patch.object(
                        verify_android_artifacts, "run", return_value=warning
                    ):
                        verify_jarsigner_bundle(Path("app.aab"))

    def test_run_preserves_nonzero_subprocess_failures(self):
        failure = subprocess.CalledProcessError(
            1, ["jarsigner"], output="jar verified.\n", stderr="failure\n"
        )
        with patch("verify_android_artifacts.subprocess.run", side_effect=failure):
            with self.assertRaises(ArtifactError):
                run(["jarsigner", "-verify", "app.aab"])


class ApksignerInjectionTest(unittest.TestCase):
    def test_apksigner_argument_is_required(self):
        with patch(
            "sys.argv",
            ["verify_android_artifacts.py", "--metadata", "metadata.json", "--apk", "app.apk"],
        ):
            with self.assertRaises(SystemExit) as error:
                verify_android_artifacts.main()
        self.assertEqual(error.exception.code, 2)

    def test_expected_debuggable_argument_is_required(self):
        with patch(
            "sys.argv",
            [
                "verify_android_artifacts.py",
                "--metadata",
                "metadata.json",
                "--apk",
                "app.apk",
                "--apksigner",
                "apksigner",
                "--apkanalyzer",
                "apkanalyzer",
            ],
        ):
            with self.assertRaises(SystemExit) as error:
                verify_android_artifacts.main()
        self.assertEqual(error.exception.code, 2)

    def test_expected_debuggable_accepts_only_lowercase_literals(self):
        self.assertIs(parse_expected_debuggable("true"), True)
        self.assertIs(parse_expected_debuggable("false"), False)
        for value in ("TRUE", "False", "1", "yes", ""):
            with self.subTest(value=value):
                with self.assertRaises(argparse.ArgumentTypeError):
                    parse_expected_debuggable(value)

    def test_cli_rejects_noncanonical_expected_debuggable_values(self):
        base_argv = [
            "verify_android_artifacts.py",
            "--metadata",
            "metadata.json",
            "--apk",
            "app.apk",
            "--apksigner",
            "apksigner",
            "--apkanalyzer",
            "apkanalyzer",
            "--expected-debuggable",
        ]
        for value in ("TRUE", "False", "1", "yes", ""):
            with self.subTest(value=value), patch("sys.argv", [*base_argv, value]):
                with self.assertRaises(SystemExit) as error:
                    verify_android_artifacts.main()
                self.assertEqual(error.exception.code, 2)

    def test_decode_debuggable_output_is_typed_and_fail_closed(self):
        self.assertIs(decode_debuggable_output(" \nTrUe\n"), True)
        self.assertIs(decode_debuggable_output(" false \n"), False)
        for value in ("", "maybe", "true\nwarning", "0"):
            with self.subTest(value=value):
                with self.assertRaises(ArtifactError):
                    decode_debuggable_output(value)

    def _verify_apk(self, actual_debuggable: str, expected_debuggable: bool):
        metadata = {
            "expectedCertificateSha256": "a" * 64,
            "applicationId": "example.app",
            "versionName": "1.0",
            "versionCode": 1,
        }
        calls = []

        def fake_run(command):
            calls.append(command)
            if command[1] == "verify":
                return (
                    "Signer #1 key algorithm: RSA\n"
                    "Signer #1 key size (bits): 4096\n"
                    "SHA-256 digest: " + ":".join(["aa"] * 32)
                )
            if command[1:3] == ["manifest", "application-id"]:
                return "example.app\n"
            if command[1:3] == ["manifest", "version-name"]:
                return "1.0\n"
            if command[1:3] == ["manifest", "version-code"]:
                return "1\n"
            if command[1:3] == ["manifest", "debuggable"]:
                return actual_debuggable
            raise AssertionError(f"unexpected command: {command}")

        injected = Path("/sdk/build-tools/36.1.0/apksigner")
        analyzer = Path("/sdk/cmdline-tools/14.0/bin/apkanalyzer")
        with patch.object(verify_android_artifacts, "run", side_effect=fake_run):
            verify_android_artifacts.verify_apk(
                Path("app.apk"),
                metadata,
                injected,
                analyzer,
                expected_debuggable,
            )
        self.assertEqual(calls[0][0], str(injected))
        self.assertNotEqual(calls[0][0], "apksigner")
        self.assertEqual([call[0] for call in calls[1:]], [str(analyzer)] * 4)
        self.assertEqual(len(calls), 5)

    def test_verify_apk_accepts_true_and_false_matches(self):
        self._verify_apk(" \nTRUE\n", True)
        self._verify_apk("false\n", False)

    def test_verify_apk_rejects_both_mismatch_directions(self):
        for actual, expected in (("false\n", True), ("true\n", False)):
            with self.subTest(actual=actual, expected=expected):
                with self.assertRaises(ArtifactError):
                    self._verify_apk(actual, expected)

    def test_verify_apk_rejects_empty_or_malformed_actual_output(self):
        for actual in ("", "not-a-boolean\n"):
            with self.subTest(actual=actual):
                with self.assertRaises(ArtifactError):
                    self._verify_apk(actual, False)

    def test_cli_returns_nonzero_for_mismatch_and_malformed_actual_output(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            metadata = root_path / "metadata.json"
            metadata.write_text(
                json.dumps(
                    {
                        "expectedCertificateSha256": "a" * 64,
                        "applicationId": "example.app",
                        "versionName": "1.0",
                        "versionCode": 1,
                    }
                ),
                encoding="utf-8",
            )

            def fake_run(command):
                if command[1] == "verify":
                    return (
                        "Signer #1 key algorithm: RSA\n"
                        "Signer #1 key size (bits): 4096\n"
                        "SHA-256 digest: " + ":".join(["aa"] * 32)
                    )
                if command[1:3] == ["manifest", "application-id"]:
                    return "example.app\n"
                if command[1:3] == ["manifest", "version-name"]:
                    return "1.0\n"
                if command[1:3] == ["manifest", "version-code"]:
                    return "1\n"
                return self.actual_debuggable

            for actual in ("false\n", "not-a-boolean\n"):
                with self.subTest(actual=actual):
                    self.actual_debuggable = actual
                    with patch(
                        "sys.argv",
                        [
                            "verify_android_artifacts.py",
                            "--metadata",
                            str(metadata),
                            "--apk",
                            str(root_path / "app.apk"),
                            "--apksigner",
                            "apksigner",
                            "--apkanalyzer",
                            "apkanalyzer",
                            "--expected-debuggable",
                            "true",
                        ],
                    ), patch.object(
                        verify_android_artifacts, "run", side_effect=fake_run
                    ), patch("builtins.print") as output:
                        self.assertNotEqual(verify_android_artifacts.main(), 0)
                        self.assertIn(
                            "Android artifact verification failed",
                            output.call_args.args[0],
                        )

    def test_empty_apksigner_is_rejected_before_path_normalization(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            metadata = root_path / "metadata.json"
            metadata.write_text(json.dumps({}), encoding="utf-8")
            with patch(
                "sys.argv",
                [
                    "verify_android_artifacts.py",
                    "--metadata",
                    str(metadata),
                    "--apk",
                    str(root_path / "app.apk"),
                    "--apksigner",
                    "",
                    "--apkanalyzer",
                    "apkanalyzer",
                    "--expected-debuggable",
                    "false",
                ],
            ):
                with patch("builtins.print") as output:
                    self.assertEqual(verify_android_artifacts.main(), 1)
                    self.assertIn(
                        "--apksigner must not be empty", output.call_args.args[0]
                    )

    def test_empty_apkanalyzer_is_rejected_before_path_normalization(self):
        with tempfile.TemporaryDirectory() as root:
            root_path = Path(root)
            metadata = root_path / "metadata.json"
            metadata.write_text(json.dumps({}), encoding="utf-8")
            with patch(
                "sys.argv",
                [
                    "verify_android_artifacts.py",
                    "--metadata",
                    str(metadata),
                    "--apk",
                    str(root_path / "app.apk"),
                    "--apksigner",
                    "apksigner",
                    "--apkanalyzer",
                    "",
                    "--expected-debuggable",
                    "false",
                ],
            ):
                with patch("builtins.print") as output:
                    self.assertEqual(verify_android_artifacts.main(), 1)
                    self.assertIn(
                        "--apkanalyzer must not be empty", output.call_args.args[0]
                    )


if __name__ == "__main__":
    unittest.main()
