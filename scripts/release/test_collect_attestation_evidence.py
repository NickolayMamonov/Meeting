import base64
import json
import unittest

from collect_attestation_evidence import CollectionError, _payload_from_bundle


class CollectAttestationEvidenceTest(unittest.TestCase):
    def test_dsse_payload_is_decoded_when_top_level_statement_is_absent(self):
        statement = {"subject": [{"name": "artifact.apk"}], "predicate": {}}
        payload = json.dumps(statement, separators=(",", ":")).encode("utf-8")
        decoded, raw = _payload_from_bundle(
            {
                "dsseEnvelope": {
                    "payload": base64.b64encode(payload).decode("ascii"),
                }
            }
        )
        self.assertEqual(decoded, statement)
        self.assertEqual(raw, payload)

    def test_conflicting_top_level_statement_is_rejected(self):
        payload_statement = {"subject": [{"name": "artifact.apk"}], "predicate": {}}
        top_level_statement = {
            "subject": [{"name": "tampered.apk"}],
            "predicate": {},
        }
        payload = json.dumps(payload_statement, separators=(",", ":")).encode("utf-8")
        with self.assertRaisesRegex(CollectionError, "conflicts"):
            _payload_from_bundle(
                {
                    "statement": top_level_statement,
                    "dsseEnvelope": {
                        "payload": base64.b64encode(payload).decode("ascii"),
                    },
                }
            )

    def test_malformed_dsse_is_rejected_even_with_a_valid_statement(self):
        with self.assertRaisesRegex(CollectionError, "payload"):
            _payload_from_bundle(
                {
                    "statement": {"subject": []},
                    "dsseEnvelope": {"payload": "not-base64"},
                }
            )


if __name__ == "__main__":
    unittest.main()
