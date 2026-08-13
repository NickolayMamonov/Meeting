import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import verify_android_artifacts
from verify_android_artifacts import ArtifactError, verify_rsa4096_signer


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


class ApksignerInjectionTest(unittest.TestCase):
    def test_apksigner_argument_is_required(self):
        with patch(
            "sys.argv",
            ["verify_android_artifacts.py", "--metadata", "metadata.json", "--apk", "app.apk"],
        ):
            with self.assertRaises(SystemExit) as error:
                verify_android_artifacts.main()
        self.assertEqual(error.exception.code, 2)

    def test_verify_apk_uses_only_injected_path(self):
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
            return "false\n"

        injected = Path("/sdk/build-tools/36.1.0/apksigner")
        analyzer = Path("/sdk/cmdline-tools/14.0/bin/apkanalyzer")
        with patch.object(verify_android_artifacts, "run", side_effect=fake_run):
            verify_android_artifacts.verify_apk(
                Path("app.apk"), metadata, injected, analyzer
            )
        self.assertEqual(calls[0][0], str(injected))
        self.assertNotEqual(calls[0][0], "apksigner")
        self.assertEqual([call[0] for call in calls[1:]], [str(analyzer)] * 4)
        self.assertEqual(len(calls), 5)

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
                ],
            ):
                with patch("builtins.print") as output:
                    self.assertEqual(verify_android_artifacts.main(), 1)
                    self.assertIn(
                        "--apkanalyzer must not be empty", output.call_args.args[0]
                    )


if __name__ == "__main__":
    unittest.main()
