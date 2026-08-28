import unittest

from release_notes import (
    END_MARKER,
    START_MARKER,
    ReleaseNotesError,
    render_release_notes,
)


class ReleaseNotesTest(unittest.TestCase):
    MANIFEST = {
        "version_name": "2.3.4",
        "version_code": 2030400,
        "signing_fingerprint": "A" * 64,
        "artifacts": [{
            "name": "Meet.apk",
            "type": "apk",
            "size": 1,
            "sha256": "B" * 64,
        }, {
            "name": "app.aab",
            "type": "aab",
            "size": 1,
            "sha256": "C" * 64,
        }],
    }

    def test_preserves_release_please_text_and_is_idempotent(self):
        body = "Release Please changes\n\n* Fix login."
        rendered = render_release_notes(body, self.MANIFEST)
        self.assertIn(body, rendered)
        self.assertIn("Version: 2.3.4 (code 2030400)", rendered)
        self.assertIn("Meet.apk SHA-256: " + "b" * 64, rendered)
        self.assertIn("Signing certificate SHA-256: " + "a" * 64, rendered)
        self.assertEqual(rendered, render_release_notes(rendered, self.MANIFEST))

    def test_rejects_duplicate_or_unbalanced_markers(self):
        for body in (
            START_MARKER,
            END_MARKER,
            f"{START_MARKER}\n{END_MARKER}\n{START_MARKER}\n{END_MARKER}",
            f"{END_MARKER}\n{START_MARKER}",
        ):
            with self.subTest(body=body), self.assertRaises(ReleaseNotesError):
                render_release_notes(body, self.MANIFEST)

    def test_preserves_caller_owned_trailing_whitespace(self):
        body = "Release Please changes \t\n\n"
        rendered = render_release_notes(body, self.MANIFEST)
        self.assertIn(body + "\n\n" + START_MARKER, rendered)
