# Android release operations

## Authority and approvals

- `dev` is the release source and pull-request target. `master` is used only
  as the protected producer-evidence source for the credential audit.
- `RELEASE_PLEASE_TOKEN` is a fine-grained PAT owned by the release operator.
  It is the only credential used by Release Please and is scoped to the
  repository with only the contents and pull-request rights required for its
  release PR/tag/draft-release operation.
- The stable job uses the existing `android-release` Environment. Environment
  reviewers remain a separate protection boundary. There is currently no
  distinct second reviewer, so the environment must remain blocked until an
  independent reviewer is provisioned; this implementation does not weaken
  or self-approve that protection.
- The `release-please-credential-audit` check is unconditional on
  `pull_request` and `merge_group`. It reports N/A only when a PR carries an
  explicit `non-release` label or `Release-Classification: non-release` body
  marker. Release Please's `autorelease: pending` label or its
  `release-please--branches--dev` head branch is an explicit release
  classification. Missing or ambiguous classification is a release failure.

## Protected inputs

Configure these as environment variables/secrets without committing values:

- `BASE_URL_RELEASE`: absolute HTTPS production URL.
- `RELEASE_SPKI_PINS`: at least two unique SHA-256 SPKI pins.
- `RELEASE_CERTIFICATE_SHA256`: normalized certificate fingerprint.
- `RELEASE_GOOGLE_SERVICES_JSON_BASE64`.
- `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
  `RELEASE_KEY_PASSWORD`.
- `SNAPSHOT_GOOGLE_SERVICES_JSON_BASE64`,
  `SNAPSHOT_RELEASE_KEYSTORE_BASE64`,
  `SNAPSHOT_RELEASE_KEYSTORE_PASSWORD`,
  `SNAPSHOT_RELEASE_KEY_PASSWORD`.
- `RELEASE_PLEASE_TOKEN` as a fine-grained repository secret.

Snapshot and stable key material must verify to the same `meet-release`
certificate. CI never generates, rotates, or prints the key.

## Artifact chain and recovery

The release tooling generates authority, distributables, manifest, checksums,
candidate, individual attestations, and recovery envelope in that order.
Manifests/checksums cover only earlier objects and distributables; the
candidate covers manifest/checksums; the envelope covers candidate and
attestations. The envelope and candidate are excluded from their own coverage,
so the graph is acyclic.

Attestation identity is the executable bundle digest, statement subject,
certificate identity, and Rekor entry identity. List IDs and producer action
IDs are informational and are never used as uniqueness keys.

Stable publication is the final draft-to-published mutation. A failed run is
recovered by rerunning the same stable job and re-verifying the same
Release-Please tag/draft. It cannot create a competing tag or release.

## Signing bootstrap and incident response

Run `scripts/release/Initialize-AndroidReleaseSigning.ps1` in preflight mode
first. It requires an existing offline backup and a separate credential
handoff path, refuses overwrites, displays all target paths, and does not
generate anything during preflight. Execution requires explicit confirmation,
uses RSA-4096 alias `meet-release`, verifies backup bytes and fingerprint, and
keeps passwords out of arguments and logs.

For leaked signing material, revoke access, preserve evidence, and make an
explicit migration decision. Do not rotate automatically in CI or rewrite a
published tag/release.
