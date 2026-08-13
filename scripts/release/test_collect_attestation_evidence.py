import base64
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from collect_attestation_evidence import (
    CollectionError,
    _certificate_from_bundle,
    _payload_from_bundle,
    _record,
    _rekor,
)


FIXTURE = Path(__file__).parent / "fixtures" / "verified-attestation-bundle.json"


class CollectAttestationEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.verified_fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))

    @staticmethod
    def _openssl_success(*args, **kwargs):
        return Mock(returncode=0)

    def record(self, verified):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "artifact.apk"
            path.write_bytes(b"fixture apk bytes")
            with patch(
                "collect_attestation_evidence.subprocess.run",
                side_effect=self._openssl_success,
            ):
                return _record(
                    path,
                    verified,
                    source_ref="refs/heads/dev",
                    source_sha="a" * 40,
                    signer_workflow="owner/repo/.github/workflows/ci.yml",
                    run_id=123,
                    run_attempt=1,
                )

    def test_current_github_cli_wrapper_is_collected_end_to_end(self):
        signature_certificate = self.verified_fixture["verificationResult"]["signature"]["certificate"]
        self.assertNotIn("rawBytes", signature_certificate)
        self.assertEqual(signature_certificate["certificateIssuer"], "https://token.actions.githubusercontent.com")
        self.assertNotIn("authoritativeBundle", self.verified_fixture["verificationResult"])

        record = self.record(copy.deepcopy(self.verified_fixture))

        self.assertEqual(record["subject"]["name"], "artifact.apk")
        self.assertEqual(
            record["producer"]["bundle"]["statement"]["subject"]["sha256"],
            "9063f43e105bcc9cd7836ef7477970cbff389e69aa221954ce55f8616a615bf0",
        )
        self.assertEqual(record["producer"]["certificate"], record["authoritative"]["certificate"])
        self.assertEqual(record["producer"]["rekor"], record["authoritative"]["rekor"])
        self.assertEqual(
            record["producer"]["bundle"]["signature"]["payload"],
            self.verified_fixture["attestation"]["bundle"]["dsseEnvelope"]["payload"],
        )
        self.assertEqual(
            record["authoritative"]["statement"]["source_sha"],
            "a" * 40,
        )
        self.assertNotIn("certificateIssuer", record["producer"]["certificate"])
        self.assertNotIn("subjectAlternativeName", record["producer"]["certificate"])

    def test_parsed_certificate_metadata_cannot_replace_bundle_der(self):
        verified = copy.deepcopy(self.verified_fixture)
        bundle_certificate = verified["attestation"]["bundle"]["verificationMaterial"]["certificate"]
        bundle_certificate.pop("rawBytes")
        verified["verificationResult"]["signature"]["certificate"]["rawBytes"] = "not-DER"
        verified["verificationResult"]["signature"]["certificate"]["certificateLikeMetadata"] = {
            "der_base64": "also-not-DER"
        }

        with self.assertRaises(CollectionError):
            self.record(verified)

    def test_explicit_authoritative_bundle_must_match_signed_bundle(self):
        verified = copy.deepcopy(self.verified_fixture)
        authoritative = copy.deepcopy(verified["attestation"]["bundle"])
        verified["verificationResult"]["authoritativeBundle"] = authoritative

        record = self.record(verified)
        self.assertEqual(record["producer"]["certificate"], record["authoritative"]["certificate"])
        self.assertEqual(record["producer"]["rekor"], record["authoritative"]["rekor"])

        conflicting_statement = copy.deepcopy(verified)
        conflicting_statement["verificationResult"]["authoritativeBundle"]["dsseEnvelope"][
            "payload"
        ] = base64.b64encode(
            json.dumps(
                {
                    "predicate": {"buildType": "sanitized"},
                    "subject": [{"digest": {"sha256": "different"}, "name": "artifact.apk"}],
                },
                separators=(",", ":"),
            ).encode()
        ).decode()
        with self.assertRaisesRegex(CollectionError, "statements differ"):
            self.record(conflicting_statement)

        conflicting_certificate = copy.deepcopy(verified)
        conflicting_certificate["verificationResult"]["authoritativeBundle"][
            "verificationMaterial"
        ].pop("certificate")
        with self.assertRaises(CollectionError):
            self.record(conflicting_certificate)

        conflicting_rekor = copy.deepcopy(verified)
        conflicting_rekor["verificationResult"]["authoritativeBundle"][
            "verificationMaterial"
        ]["tlogEntries"][0]["logIndex"] = 8
        with self.assertRaisesRegex(CollectionError, "Rekor"):
            self.record(conflicting_rekor)

    def test_explicit_authoritative_bundle_requires_strict_shape_and_own_rekor(self):
        mutations = (
            lambda bundle: bundle.pop("mediaType"),
            lambda bundle: bundle.pop("dsseEnvelope"),
            lambda bundle: bundle["verificationMaterial"].pop("tlogEntries"),
            lambda bundle: bundle["verificationMaterial"].update({"tlogEntries": []}),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                verified = copy.deepcopy(self.verified_fixture)
                authoritative = copy.deepcopy(verified["attestation"]["bundle"])
                mutation(authoritative)
                verified["verificationResult"]["authoritativeBundle"] = authoritative
                with self.assertRaises(CollectionError):
                    self.record(verified)

    def test_certificate_der_validation_fails_closed(self):
        direct = copy.deepcopy(self.verified_fixture["attestation"]["bundle"])
        certificate = direct["verificationMaterial"]["certificate"]
        cases = {
            "missing": {},
            "non_string": {"rawBytes": 123},
            "empty": {"rawBytes": ""},
            "malformed_base64": {"rawBytes": "not base64"},
            "empty_der": {"rawBytes": ""},
            "unparseable_der": {"rawBytes": base64.b64encode(b"not an x509 certificate").decode()},
        }
        for name, replacement in cases.items():
            with self.subTest(name=name):
                candidate = copy.deepcopy(direct)
                candidate["verificationMaterial"]["certificate"] = replacement
                with self.assertRaises(CollectionError):
                    _certificate_from_bundle(candidate)

    def test_certificate_locations_and_aliases_fail_closed(self):
        direct = copy.deepcopy(self.verified_fixture["attestation"]["bundle"])
        material_certificate = direct["verificationMaterial"]["certificate"]

        with patch(
            "collect_attestation_evidence.subprocess.run",
            side_effect=self._openssl_success,
        ):
            conflicting_locations = copy.deepcopy(direct)
            conflicting_locations["certificate"] = {
                "rawBytes": base64.b64encode(b"different").decode(),
            }
            with self.assertRaises(CollectionError):
                _certificate_from_bundle(conflicting_locations)

            matching_direct_location = copy.deepcopy(direct)
            matching_direct_location["certificate"] = copy.deepcopy(material_certificate)
            self.assertEqual(
                _certificate_from_bundle(matching_direct_location),
                material_certificate["rawBytes"],
            )

            conflicting_aliases = copy.deepcopy(direct)
            conflicting_aliases["verificationMaterial"]["certificate"]["der_base64"] = (
                base64.b64encode(b"different").decode()
            )
            with self.assertRaises(CollectionError):
                _certificate_from_bundle(conflicting_aliases)

            alternate_encoding = copy.deepcopy(direct)
            alternate_encoding["verificationMaterial"]["certificate"] = {
                "der_base64": material_certificate["rawBytes"],
            }
            self.assertEqual(
                _certificate_from_bundle(alternate_encoding),
                material_certificate["rawBytes"],
            )

    def test_top_level_verified_bundle_compatibility_is_collected_end_to_end(self):
        verified = copy.deepcopy(self.verified_fixture)
        bundle = verified.pop("attestation")["bundle"]
        verified["bundle"] = bundle

        record = self.record(verified)

        self.assertEqual(record["subject"]["name"], "artifact.apk")
        self.assertEqual(record["producer"]["rekor"]["log_index"], 7)

    def test_bundle_representations_fail_closed(self):
        wrapper = copy.deepcopy(self.verified_fixture)
        direct = wrapper["attestation"]["bundle"]
        cases = {
            "missing": {},
            "empty": {"attestation": {"bundle": {}}},
            "non_mapping_attestation": {"attestation": "invalid"},
            "non_mapping_bundle": {"attestation": {"bundle": "invalid"}},
            "direct_attestation": {
                "attestation": copy.deepcopy(direct),
            },
            "simultaneous": {
                "attestation": copy.deepcopy(wrapper["attestation"]),
                "bundle": copy.deepcopy(direct),
            },
            "wrapper_direct_hybrid": {
                "attestation": {
                    "bundle": copy.deepcopy(direct),
                    "statement": {"subject": []},
                },
            },
            "nested_bundle": {
                "attestation": {
                    "bundle": {"bundle": copy.deepcopy(direct)},
                },
            },
            "deeper_bundle": {
                "bundle": {
                    "bundle": {"bundle": copy.deepcopy(direct)},
                },
            },
            "non_mapping_top_level_bundle": {"bundle": []},
            "statement_only": {
                "bundle": {
                    "mediaType": direct["mediaType"],
                    "statement": {
                        "subject": [{"name": "artifact.apk"}],
                        "predicate": {},
                    },
                    "verificationMaterial": copy.deepcopy(direct["verificationMaterial"]),
                },
            },
            "unsigned_statement": {
                "bundle": {
                    "mediaType": direct["mediaType"],
                    "statement": {"subject": []},
                    "certificate": copy.deepcopy(
                        direct["verificationMaterial"]["certificate"]
                    ),
                },
            },
            "unsigned_dsse": {
                "bundle": {
                    "dsseEnvelope": copy.deepcopy(direct["dsseEnvelope"]),
                },
            },
        }
        for name, verified in cases.items():
            with self.subTest(name=name):
                with self.assertRaises(CollectionError):
                    self.record(verified)

    def test_conflicting_bundle_aliases_fail_closed(self):
        direct = self.verified_fixture["attestation"]["bundle"]

        def conflicting_rekor_alias(bundle):
            bundle["verificationMaterial"]["tlog_entries"] = []

        cases = {
            "conflicting_dsse_alias": lambda bundle: bundle.update(
                {"dsse_envelope": {"payload": "different"}}
            ),
            "conflicting_material_alias": lambda bundle: bundle.update(
                {"verification_material": {}}
            ),
            "conflicting_certificate_encoding": lambda bundle: bundle[
                "verificationMaterial"
            ]["certificate"].update({"der_base64": "different"}),
            "conflicting_rekor_alias": conflicting_rekor_alias,
        }
        for name, mutate in cases.items():
            with self.subTest(name=name):
                verified = copy.deepcopy(self.verified_fixture)
                mutate(verified["attestation"]["bundle"])
                with self.assertRaises(CollectionError):
                    self.record(verified)

    def test_conflicting_verification_result_aliases_fail_closed(self):
        verified = copy.deepcopy(self.verified_fixture)
        conflicting = copy.deepcopy(verified["verificationResult"])
        conflicting["statement"] = {"subject": [{"name": "different.apk"}]}
        verified["verification_result"] = conflicting

        with self.assertRaisesRegex(CollectionError, "verification result"):
            self.record(verified)

    def test_conflicting_media_type_aliases_fail_closed(self):
        verified = copy.deepcopy(self.verified_fixture)
        verified["attestation"]["bundle"]["media_type"] = "conflicting-media-type"

        with self.assertRaisesRegex(CollectionError, "media type"):
            self.record(verified)

    def test_malformed_rekor_conversion_is_collection_error(self):
        verified = copy.deepcopy(self.verified_fixture)
        verified["attestation"]["bundle"]["verificationMaterial"]["tlogEntries"][0][
            "logIndex"
        ] = "not-an-integer"
        with self.assertRaises(CollectionError):
            self.record(verified)

        with self.assertRaises(CollectionError):
            _rekor(
                {},
                {
                    "verifiedTimestamps": [
                        {
                            "logId": {"keyId": "a" * 64},
                            "logIndex": 1,
                            "integratedTime": "not-a-timestamp",
                        }
                    ]
                },
            )

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
