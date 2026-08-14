# Android release operations

## Scope and authority

- `dev` is the release source and pull-request target.
- Release Please uses the repository `RELEASE_PLEASE_TOKEN` for its draft
  release, tag, and pull-request operations.
- The protected `android-release` environment is used by stable build, signing,
  public probe, and final publication. Its administrator bypass remains
  disabled and its branch policy remains `dev`.
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

Only the isolated `stable-sign` job receives the signing secrets. Build,
evidence, public-probe, and publication jobs do not reference signing
material. The credential audit must verify this boundary and must not print
secret values.

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
6. The protected mutation job revalidates the immutable draft and requires an
   initially empty asset set. It builds the exact allowlist from the signed
   evidence, uploads each allowlisted asset once, downloads every remote asset,
   and verifies identity, size, and SHA-256.
7. Setting `draft=false` is the final GitHub mutation. A failed upload remains
   an unpublished draft and the workflow stops.

The release allowlist includes the authority document, release manifest,
checksums, candidate, neutral attestation index, signed APK/AAB, optional
outputs when produced, and each individual attestation. The local chain is
acyclic and every attestation subject is covered exactly once.

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
