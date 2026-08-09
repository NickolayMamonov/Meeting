import unittest

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


if __name__ == "__main__":
    unittest.main()
