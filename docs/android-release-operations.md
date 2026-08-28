# Android release operations

## Scope and authority

- `dev` is the release source and pull-request target.
- Release Please uses the repository `RELEASE_PLEASE_TOKEN` for its draft
  release, tag, and pull-request operations.
- The protected `android-release` environment is used by stable build, signing,
  public probe, final publication, and the non-publishing release proof. Its
  administrator bypass remains disabled and its branch policy remains `dev`.
- No backend, VPS, Nginx, certificate, Google Play, device, or USB operation
  is part of Android publication.

## Exact protected inventory

Repository secrets:

- `RELEASE_PLEASE_TOKEN` — Release Please and all draft visibility, upload,
  remote verification, and final publication API calls.
- `GOOGLE_SERVICES_JSON` — raw Firebase configuration for the stable build.

The `android-release` environment contains:

- Secret `RELEASE_KEYSTORE_BASE64`.
- Secret `RELEASE_KEYSTORE_PASSWORD`.
- Secret `RELEASE_KEY_PASSWORD`.
- Non-secret `RELEASE_CERTIFICATE_SHA256`.
- Non-secret `BASE_URL_RELEASE`, exactly
  `https://api.whysoezzy.online`.

The stable publication workflow gives signing secrets only to the isolated
`stable-sign` job. Its build, evidence, public-probe, and publication jobs do
not reference signing material. The dispatch-only `Android release proof`
workflow has a separate signer boundary: only `proof-sign` receives the
production keystore and passwords. In that workflow, `proof-build` receives
Firebase configuration and release URL/certificate variables, while
`proof-evidence`, `proof-public-probe`, and `proof-report` receive no signing
material. The credential audit must verify both boundaries and must not print
secret values. The proof workflow has no release API token and cannot create,
mutate, publish, or upload a GitHub Release.

## Release networking

Release Gradle inputs and generated metadata accept only the byte-for-byte
origin `https://api.whysoezzy.online`. Generated
`network_security_config.xml`:

- trusts Android system CAs;
- denies cleartext traffic;
- matches only `api.whysoezzy.online`;
- contains no pin elements.

Debug and snapshot variants keep their local configuration. `:core:network`
continues to expose the existing `BuildConfig.BASE_URL` boundary; no API,
repository, domain, presentation, or UX contract changes are allowed.

## Public installer and protected evidence

The public GitHub Release projection contains exactly one project-uploaded asset:
`Meet.apk`. GitHub's generated source ZIP and tarball links are platform links,
not project-uploaded release assets.

The protected Actions artifact `android-release-evidence-${tag}` remains the
complete auditor chain. It contains the canonical `Meet.apk`, the signed AAB,
optional mapping/native-symbol outputs, authority, manifest, checksums,
candidate, attestation index, and every individual attestation. The AAB and
evidence files never cross the public upload boundary.

Release notes preserve Release Please's text and add one deterministic
`meet-android-verification` section with the version/code, `Meet.apk` SHA-256,
and expected RSA-4096 certificate fingerprint.

## Ordered publication

1. Release Please creates the intended draft and stable build verifies the
   tag, source commit, version, exact HTTPS origin, and empty draft.
2. Stable build provisions raw `GOOGLE_SERVICES_JSON` only for the build,
   removes the temporary file, validates release inputs, runs the relevant
   lint/tests, and produces unsigned APK/AAB artifacts.
3. Isolated `stable-sign` signs the APK and AAB with the existing `meet-release`
   RSA-4096 identity, verifies the certificate fingerprint, and removes signing
   files before the job ends.
4. Stable evidence verifies package/version/non-debuggable state, APK/AAB
   identity, checksums, manifests, optional mapping or native symbols, and
   exhaustive GitHub attestations.
5. The protected public probe requires
   `https://api.whysoezzy.online/meetings` to return HTTPS 200 with bounded
   valid JSON and `/actuator` to return HTTPS 404. It uses the standard
   operating-system/Python TLS verifier, rejects redirects and transport/TLS
   errors, and never logs response bodies.
6. The protected mutation job checks out the reviewed tooling revision, reads
   the protected evidence, revalidates the immutable empty draft, verifies the
   local `Meet.apk`, and performs one upload POST for that literal name.
   It then validates the returned positive asset ID, downloads by that ID
   through the bounded direct-200/one-302 transport, and independently verifies
   bytes, Android identity, signer, and attestation.
7. The final mutation is one non-retried PATCH carrying the captured release
   name, tag, source, prerelease state, rendered body, and `draft=false`.
   Read-only checks prove the exact public state afterward; no mutation follows.
   A failed upload may leave an unpublished draft containing `Meet.apk`, and a
   rerun fails empty-draft admission rather than deleting or replacing it.

The release concurrency lane uses `cancel-in-progress: false`. Operators must
not manually mutate the draft, its assets, body, or tag from protected mutation
admission through final verification. GitHub exposes no supported transaction
that atomically binds those values to the final PATCH. A mutation during the
final request is therefore an explicitly excluded operator condition: if
divergence is observed afterward, the result is indeterminate/manual
investigation, with no retry, repair, delete, or rollback request.

## Non-publishing exact release proof

After the implementation is merged to `dev`, operators dispatch the protected
proof from `dev` with the exact accepted application commit:

```text
gh workflow run release-proof.yml --ref dev -f application_sha=<accepted-40-hex-sha>
```

`application_sha` is the commit whose Android source is built, packaged, signed,
and probed. `${{ github.sha }}` is recorded separately as the workflow/tooling
commit: it supplies the proof workflow and release scripts used to verify the
application artifact. The workflow requires the application commit to be a
lowercase 40-hex commit, checks it out exactly, and proves it is an ancestor of
the workflow commit and `origin/dev`.

The five-job chain must complete successfully without skipped jobs:

1. `proof-build` enters `android-release`, validates Firebase and the exact
   production URL, then runs release metadata generation, validation, lint,
   tests, APK, and AAB builds.
2. `proof-sign` is the isolated production signer. It signs both formats,
   verifies RSA-4096 identity, requires both normalized certificate SHA-256
   values to equal
   `b643fc0e49f572d3b7202c1e28e0ded1eb50228c70ae7531a573c97c5763536f`, and
   retains only those non-secret values in `signer-fingerprints.json`.
3. `proof-evidence` verifies application identity, version, non-debuggable
   state, signer identity, checksums, and exact current-run attestations. It
   must print `release-chain verification passed`.
4. `proof-public-probe` checks out the same application commit and runs the
   fixed-origin public backend probe. It must print
   `public backend probe passed`.
5. `proof-report` rechecks the evidence chain and retains one seven-day
   artifact named
   `android-release-proof-<application_sha>-<run_attempt>`.

The retained artifact contains `release-output`, `signer-fingerprints.json`,
and `proof-run.json`. The run record includes the workflow name, workflow/tooling
SHA, application SHA, run ID, run attempt, each predecessor job conclusion,
and the report job name. The completed Actions run record supplies the final
`proof-report` conclusion; acceptance requires all five named jobs to be
successful and non-skipped.

## Credential and QA boundaries

`RELEASE_PLEASE_TOKEN` is exposed to the publication driver only as
`RELEASE_API_TOKEN` for authenticated GitHub REST control-plane requests.
`${{ github.token }}` is exposed only as `ATTESTATION_TOKEN`; the attestation
child receives it as `GH_TOKEN`. The child receives no release API token,
attestation-token variable, ambient GitHub token, cookies, or signing inputs.
The redirected asset data request uses a fresh credential-free client.

The exact-head non-production audit is run with:

```text
gh workflow run release-credential-audit.yml --ref <PR-branch> -f publication_harness=true -f expected_sha=<40-hex-PR-head>
```

The QA workflow checks out the requested tooling head separately from the
fixed reviewed application source commit
`1670aa6b9a415c7638c9b5b348d9ecd991b736c8`, requires both checkout HEADs and
their commit objects, and requires SHA inequality. Gradle runs only in the
application checkout with `-PreleaseCommitSha` set to that fixed commit; the
attestation policy remains bound to the requested tooling head. Temporary
keystores, passwords, Firebase configuration, and QA evidence/report artifacts
are removed or expire after seven days. QA has no release secret, production
release write permission, tag mutation, or PR mutation access.

The protected chain's local inventory includes the authority document, release
manifest, checksums, candidate, neutral attestation index, signed APK/AAB,
optional outputs when produced, and each individual attestation. The local
chain is acyclic and every attestation subject is covered exactly once. That
inventory is not the public release asset set.

## Bundletool

Stable evidence downloads one reviewed public Bundletool release and verifies
the jar before execution:

- version `1.18.3`;
- SHA-256
  `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`.

These values are workflow source constants, not environment configuration.

## Verification and rollout

Run the convention/network tests, release Python tests, relevant ktlint and
unit tests, `:app:lintRelease`, `:app:assembleRelease`,
`:app:bundleRelease`, `validateReleasePublishingInputs`, and
`generateReleaseBuildMetadata`. Inspect generated XML, the merged manifest,
release metadata, checksums, and attestation references. Search active
source, workflows, tests, and docs for stale pin, device-evidence,
or alternate-credential contracts.

Merge the correction normally, let Release Please refresh PR #67, merge that
PR normally, and verify one public signed `v1.0.0` release. Confirm package
`dev.whysoezzy.meet`, version name/code, non-debuggable release state, the
existing RSA-4096 certificate fingerprint
`b643fc0e49f572d3b7202c1e28e0ded1eb50228c70ae7531a573c97c5763536f`, exact
checksums, manifests, optional outputs, attestations, and production URL.
