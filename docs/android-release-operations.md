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
- The same workflow has a protected-`master` producer job. It uploads exactly
  `credential-audit-evidence.json`; the consumer enumerates all producer runs
  and artifacts, selects the newest execution, downloads the artifact, and
  hashes the downloaded ZIP against the selected GitHub artifact digest before
  parsing it, then binds its run/ref/workflow claims to the selected API record.
- `MERGE_QUEUE_EVIDENCE_JSON` is a non-secret repository variable containing
  `{"source":"github-api","branch":"dev","required_checks":[...]}`. It enables
  the live GraphQL queue adapter, which paginates the complete queue, reads
  required check conclusions, rereads the queue, and binds the singleton entry
  and synthetic two-parent commit to the `merge_group` event.

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
- `RELEASE_UPLOAD_TOKEN` in the separate `android-release-upload` Environment.
  Configure `RELEASE_UPLOAD_ACTOR` and `RELEASE_PLEASE_ACTOR` as non-secret
  variables so release ID, Release Please author, and every asset uploader are
  bound before a mutation.
- `RELEASE_PRODUCER_WORKFLOW_ID` identifies the protected-master producer
  workflow for the live credential audit.
- `BUNDLETOOL_VERSION` and `BUNDLETOOL_SHA256` are required environment
  variables. Stable publication downloads exactly that Bundletool version and
  verifies the SHA-256 before inspecting the AAB.

Snapshot and stable key material must verify to the same `meet-release`
certificate. CI never generates, rotates, or prints the key. Build jobs only
produce unsigned artifacts; isolated signing jobs receive the keystore and
passwords without checking out or executing repository code. The final
release mutation is a separate fresh job with no signing inputs.

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

Attestation collection requests a bounded complete result set, rejects
multiple verified results, decodes and hashes the signed DSSE payload, parses
the exact `verificationResult.signature.certificate` as X.509, checks the
bundle's verification material and Rekor entry against that certificate, and
requires the parsed authoritative statement to match the local payload.

Stable publication is the final draft-to-published mutation. A failed run is
recovered by rerunning the same stable job and re-verifying the same
Release-Please tag/draft. It cannot create a competing tag or release.
Manual recovery is accepted only for an existing Release Please draft, exact
release ID/tag/source commit, and a previously verified evidence artifact.
The release ID is resolved from the tag through the GitHub Releases API rather
than a non-existent Release Please action output. Recovery first resolves the
selected stable-evidence run, successful stable-evidence job attempt, exact
release artifact, and API-reported ZIP digest. It hashes the downloaded ZIP
before extracting it, then binds the candidate, tag commit, workflow/ref/SHA,
run attempt/conclusion, and every producer/authoritative attestation statement
to that same producer execution. The final recovery mutation job consumes only
the verified declarative artifact and does not check out or execute release
source code while `RELEASE_UPLOAD_TOKEN` is present.
Runtime publication also requires externally recorded evidence bound to the
exact verified release: `release_id`, `tag`, `source_sha`,
`candidate_sha256`, and `manifest_sha256` must match the API-verified draft and
the downloaded candidate/manifest bytes. The runtime Firebase package,
signing certificate fingerprint, and TLS/SPKI pin set must then match the
verified stable manifest exactly. Backend revision, authenticated-device,
install, and authenticated-API gates remain required; the evidence must
explicitly state that emulator/device state was not reset.

## Signing bootstrap and incident response

Run `scripts/release/Initialize-AndroidReleaseSigning.ps1` in preflight mode
first. It requires an existing offline backup and a separate credential
handoff path, refuses overwrites, displays all target paths, and does not
generate anything during preflight. Execution requires explicit confirmation,
uses RSA-4096 alias `meet-release`, verifies backup bytes and fingerprint, and
keeps passwords out of arguments and logs.

After the offline backup has been verified, the optional
`-ProvisionGitHubSecrets` switch streams the generated keystore and passwords
to `gh secret set --env android-release` through standard input. Use
`-GitHubRepository OWNER/REPOSITORY` when running outside the checkout. Secret
values are never command-line arguments or written into the repository:

```powershell
./scripts/release/Initialize-AndroidReleaseSigning.ps1 `
  -Mode Execute `
  -OfflineBackupDirectory 'D:\offline\meet-release' `
  -CredentialHandoffDirectory 'C:\secure\meet-release-handoff' `
  -ProvisionGitHubSecrets `
  -GitHubRepository 'NickolayMamonov/Meeting'
```

For leaked signing material, revoke access, preserve evidence, and make an
explicit migration decision. Do not rotate automatically in CI or rewrite a
published tag/release.
