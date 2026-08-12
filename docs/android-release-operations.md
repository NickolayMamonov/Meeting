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
  `pull_request_target` and `merge_group`. The workflow must be installed on
  the protected base branch before this check is required: GitHub evaluates
  `pull_request_target` from that protected base revision, so a PR cannot
  modify the token-bearing workflow definition. The verifier is then checked
  out at the exact base SHA before the token is injected. It reports N/A only
  when a PR carries an explicit `non-release` label or
  `Release-Classification: non-release` body marker. Release Please's
  `autorelease: pending` label or its
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
Asset replacement uses the exact `https://uploads.github.com` endpoint with
`curl`; do not use `gh api --hostname uploads.github.com`, because GitHub CLI
rewrites that hostname to `api.uploads.github.com`.
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
first. It requires an existing, empty offline backup and a separate absent
credential handoff path, rejects canonically equal or nested paths in either
direction, refuses overwrites, displays the target paths, and does not
generate anything during preflight. Execute requires the exact
`CREATE-ANDROID-RELEASE-KEY` confirmation, uses one RSA-4096 `meet-release`
identity, and creates exactly these four artifacts in both locations:

- `meet-release.jks`;
- `meet-release.cer`;
- `meet-release.sha256`, normalized to the certificate-derived SHA-256;
- `meet-release-passwords.txt`, containing the store password, key password,
  alias, and certificate fingerprint.

### Bounded failure diagnostics

Bootstrap failures report only an allowlisted operation stage and bounded
category, together with backup-commit, cleanup, and recovery state. This is
the complete operator-visible diagnostic boundary: do not add raw exception
messages, child-process output, command arguments, paths, passwords, base64,
keystore or certificate bytes, private-key material, or recovery values to CI,
task comments, tickets, or chat.

Record the exact source SHA, mode, stage, category, whether the backup was
committed, and whether cleanup completed. Before backup commit, a failed
Execute removes only invocation-owned artifacts whose recorded identity still
matches; pre-existing, changed, race-created, and deliberately preserved
partial backup files are not deletion-owned. Do not retry Execute after a
pre-commit failure until the bounded evidence and retained paths have been
reviewed. After backup commit, preserve both complete identity sets and recover
only with `-Mode Provision` using the same paths.

The live failure corrected by this bootstrap was a Windows PowerShell 5.1
native-process boundary: Temurin 21 `keytool` writes ordinary progress and
warning text to stderr while returning success, and the baseline adapter let
PowerShell promote that stderr to a terminating native-command error. The
corrected adapter suppresses native stderr and uses the process exit status as
the success signal. The disposable integration suite proves this mechanism
fails on the exact approved baseline
`7aec34b8dd27c4bf2de68bcbee86ebfdf48cb059` and passes with the corrected
adapter under the same PowerShell 5.1/JDK 21 invocation.

Validation, Preflight, Execute, Provision, ACL, child-process, and cleanup
failures pass through the same bounded stage/category formatter. The
`Error` test hook is not an operator-visible diagnostic channel and is
rejected by the disposable identity authority; raw exception messages are
never forwarded to hooks or emitted by the script.

### Disposable Windows integration

The `Android signing bootstrap (Windows PowerShell 5.1)` CI check is the
production-shaped regression boundary. It runs on `windows-2022` with Temurin
21, pinned actions, the exact pull-request head SHA, read-only repository
permission, checkout credential persistence disabled, and credential
assertions before parsing or running either suite.

The integration suite may create only unmistakably non-release, one-day
identities beneath GUID-named system-temporary roots. It must use real Windows
PowerShell 5.1, JDK keytool, ACL, password-file, hashing, copy, verification,
and cleanup behavior. It must not use the approved USB or handoff paths, accept
production identity settings, provision GitHub, or retain fixture artifacts.
Its `finally` cleanup is mandatory on success and failure. These fixtures are
test evidence only and must never be promoted, copied to operational media, or
used as release credentials.

Execution creates and verifies the local identity first, copies all four
artifacts to the initially empty backup using exclusive destinations on the
backup volume itself, compares each corresponding byte sequence and semantic
identity field, and only then commits the backup and permits a GitHub call.
Backup copies never use a system-temporary staging file or an overwrite-capable
move across volumes. A destination is cleanup-owned only after this invocation
successfully creates it exclusively; a pre-existing or race-created destination
is never overwritten or removed. If an invocation-owned backup copy fails after
creation, its partial bytes are preserved for diagnosis rather than being
mistaken for a pre-existing artifact.

The generated keystore explicitly uses JKS because the workflow records
distinct store and private-key passwords. Identity verification uses
`keytool -list` with the store password only (the JDK does not support a
key-password option for `-list`), then performs a non-mutating `keytool
-certreq` check with both passwords before comparing the exported certificate.
This catches a wrong private-key password instead of validating only keystore
metadata.

With `-ProvisionGitHubSecrets`, Execute streams values to `gh` through
standard input only. It writes the exact secret and variable names consumed by
the release workflows:

- `android-release`: `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, and
  `RELEASE_CERTIFICATE_SHA256`;
- `android-snapshot-signing`: `SNAPSHOT_RELEASE_KEYSTORE_BASE64`,
  `SNAPSHOT_RELEASE_KEYSTORE_PASSWORD`, `SNAPSHOT_RELEASE_KEY_PASSWORD`, and
  `RELEASE_CERTIFICATE_SHA256`.

Use `-GitHubRepository OWNER/REPOSITORY` when running outside the checkout.
Secret values, base64 keystore bytes, and key material are never command-line
arguments or printed.

If a post-commit GitHub write fails, do **not** rerun Execute and do not create
a second identity. Keep both the local handoff and the verified backup,
reconnect the removable media only as required for verification, and rerun
Provision with the exact same paths:

```powershell
./scripts/release/Initialize-AndroidReleaseSigning.ps1 `
  -Mode Provision `
  -OfflineBackupDirectory 'D:\offline\meet-release' `
  -CredentialHandoffDirectory 'C:\secure\meet-release-handoff' `
  -GitHubRepository 'NickolayMamonov/Meeting'
```

Provision is the identity-preserving retry entry point. It retains path
separation checks, accepts populated committed locations, performs no key
generation or artifact copy/overwrite, and fully verifies both four-artifact
sets, byte equality, alias `meet-release`, both passwords,
keystore/certificate identity, and the certificate-derived fingerprint before
the first `gh` call. Its preserved passwords are written only to collision-
resistant owner-only temporary files under the system temporary directory,
outside both identity directories; those files are guaranteed to be removed
before Provision returns or fails. Provision does not create, ACL, copy, or
overwrite any target artifact. It then idempotently rewrites the complete
command set above through stdin.
After a failure between environment writes, rerun Provision on the same paths;
never use Execute for recovery. Verify only names and non-secret status, then
disconnect the USB and store it offline again.

The backup media is FAT32. FAT32 does not provide the Windows ACL
confidentiality assumed for the local handoff, and physical access to the
connected USB grants access to the signing identity and plaintext recovery
metadata. Keep the removable media under physical control, disconnect it
after verification or Provision recovery, and store it offline. If the USB is
unavailable or compromise is suspected, stop and follow explicit credential
rotation/incident handling; do not silently generate a replacement identity.

### Corrected-bootstrap rollout

Rollout requires parser, deterministic, disposable integration, Python release,
diff, hosted CI, and independent review evidence on one exact final SHA. Merge
normally to `dev`; this correction does not authorize a stable Android release.

Immediately before the authorized attempt, verify that
`D:\meets\android-signing-backup` still exists and is empty and that
`C:\Users\whysoezzy\Documents\meets\android-release-handoff` is absent. Run
Preflight against those exact paths, review its result, and authorize exactly
one new Execute attempt. A pre-commit failure stops rollout pending review. If
the backup commits but GitHub provisioning is incomplete, preserve both sets
and use only same-identity Provision; never rerun Execute or generate a
replacement identity.

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
