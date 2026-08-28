# Android release smoke checklist

This is the operator-owned checklist for runtime smoke verification of an
Android build. It is evidence collection, not a substitute for the protected
publication gates in [Android release operations](android-release-operations.md).
Do not call a case passed from a build, unit test, or an unobservable assumption:
each case needs a device observation recorded below.

## 1. Scope and prerequisites

The smoke account and fixtures must be authorized for this verification. Do not
put OTPs, passwords, access tokens, Firebase files, keystore material, or
personal data in this document, a task comment, or committed evidence.

Before installing anything, confirm and record:

- a physical Android device or emulator with USB/ADB access, Android 36
  compatibility, a known device name/serial, enough storage, and a stable
  clock;
- `meet-backend-v3` reachable at the backend selected for the build;
- OTP delivery and a reusable returning-user account;
- one internal meeting fixture (not an external-registration meeting);
- one community fixture and permission to change its membership;
- one profile/avatar fixture, with permission to restore its original data;
- a way to make one request fail without changing the expected backend
  identity (for example, an emulator network toggle), and a way to restore
  networking;
- controlled access-token expiry or a backend fixture that naturally expires
  access tokens. If expiry cannot be controlled or observed, the automatic
  refresh case is `BLOCKED`, not `PASS`.

Record the environment before each run:

| Field | Value |
| --- | --- |
| Operator and UTC start time |  |
| Device model, OS/API, serial, emulator/physical |  |
| App mode and exact install source | `debug` / `snapshot` / `stable`; path or release URL |
| Artifact filename and SHA-256 |  |
| Package/application ID |  |
| Version name and version code |  |
| Backend origin actually used |  |
| Backend health/API observation and UTC time |  |
| Workflow, run number, and run attempt (snapshot/stable where applicable) |  |
| Full source commit SHA |  |
| Signer workflow and certificate SHA-256 |  |
| Returning-user/account fixture identifier (non-secret) |  |
| Meeting fixture identifier |  |
| Community fixture identifier |  |
| Original avatar/data restoration notes |  |

## 2. Toolchain and package identity

Use and record these exact local values:

- Temurin JDK `21.0.10`;
- Gradle wrapper `8.13` (`./gradlew`, not a different system Gradle);
- configured Android SDK: `C:/Users/whysoezzy/AppData/Local/Android/Sdk`;
- SDK platform `android-36`;
- SDK build-tools `36.1.0`.

Capture the commands and their outputs in the run evidence, without including
secrets:

```sh
java -version
./gradlew --version
adb version
```

The Android project uses Kotlin 2.0, Jetpack Compose, Koin, Ktor,
Coroutines/Flow, Clean Architecture, and convention plugins. This checklist
does not authorize changing those boundaries or the existing release
configuration.

## 3. Select exactly one execution mode

Do not mix packages, backends, or provenance between modes. Uninstall older
variants or use a clean device state so the package under test is unambiguous.
Record the selected mode and all identity fields before the first smoke case.

### 3.1 Local debug

Build and install the debug variant locally:

```sh
./gradlew :app:assembleDebug -PBASE_URL_DEBUG=<recorded-debug-origin>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug package is `dev.whysoezzy.meet.debug`. Its backend is the explicitly
recorded `BASE_URL_DEBUG`; the repository default is
`http://10.0.2.2:8080`, and `-PBASE_URL_DEBUG=...` may override it. Confirm the
server is reachable from the device (the emulator's `10.0.2.2` is its host
loopback mapping) and record the exact origin, APK path, version, and SHA-256.
A debug APK is local development evidence only and is not snapshot or stable
release evidence.

### 3.2 Authoritative signed-and-verified CI snapshot

Use only a successful push-to-`dev` run of
`.github/workflows/ci.yml`. The run must have completed the complete snapshot
chain:

1. `snapshot-build` creates the unsigned snapshot and metadata;
2. isolated `snapshot-sign` signs the APK with the expected RSA-4096
   certificate and verifies that signer, then removes signing inputs;
3. `snapshot-evidence` verifies APK package/version/debuggable identity,
   packages and attests the subjects, runs
   `python scripts/release/verify_chain.py <extracted-directory>`, and uploads
   the final artifact.

Download the final artifact named exactly
`android-snapshot-${run_number}-${run_attempt}` from that same successful run.
Extract it into a clean directory and run:

```sh
python scripts/release/verify_chain.py <extracted-directory>
```

Installation is allowed only after `release-chain verification passed`. Install
only the APK from that final artifact:

```sh
adb install -r <extracted-directory>/<apk-from-final-artifact>
```

Retrieve `snapshot-build.json` only from the same run's
`android-snapshot-signed-${run_number}-${run_attempt}` artifact for comparison.
The signed artifact is metadata evidence, not the installer. A local
`assembleSnapshot`, unsigned snapshot, or intermediate signed APK must not be
installed or recorded as release-smoke evidence. The final artifact is not
interchangeable with `Meet.apk` from stable publication.

The snapshot package is `dev.whysoezzy.meet.snapshot`. CI passes no
`BASE_URL_DEBUG` override, so the snapshot metadata and APK use the debug
fallback `http://10.0.2.2:8080`. The snapshot network-security resource permits
the local `10.0.2.2` and `localhost` domains for this non-production package.
Record the actual backend reachable from the device; do not relabel a local
snapshot as production.

Cross-check these values before installation. Every value must agree across
the final artifact, same-run metadata, manifest, checksums, attestation index,
individual attestations, and the CI run:

| Snapshot identity field | Recorded value |
| --- | --- |
| Workflow | `.github/workflows/ci.yml` |
| GitHub run number |  |
| GitHub run attempt |  |
| Full source commit SHA |  |
| `versionName` |  |
| `versionCode` |  |
| Final artifact name | `android-snapshot-${run_number}-${run_attempt}` |
| APK filename in final artifact |  |
| APK SHA-256 |  |
| Package | `dev.whysoezzy.meet.snapshot` |
| Baked `BASE_URL_DEBUG` | `http://10.0.2.2:8080` |
| Expected signer certificate SHA-256 |  |
| Observed signer certificate SHA-256 |  |
| Signer identity | RSA-4096; not Android Debug |
| `verify_chain.py` result | `PASS` / `FAIL` |

If any identity, checksum, attestation coverage, signer, run/attempt, or
baked-backend comparison disagrees, stop and mark the snapshot execution
`BLOCKED`. Do not repair or substitute an artifact.

### 3.3 Protected stable

Use only the exact published/candidate `Meet.apk` admitted by the protected
Android release chain. Stable installs as `dev.whysoezzy.meet` and must use the
exact production origin `https://api.whysoezzy.online`. Record the candidate or
release identity, APK SHA-256, version name/code, package, source commit, and
certificate fingerprint before installation.

Stable admission and evidence remain governed by
`docs/android-release-operations.md`: protected release metadata validation,
`validateReleasePublishingInputs`, the established lint/unit/APK/AAB Gradle
chain, isolated signing, Android artifact verification, checksums, attestations,
and the public production probe. The stable release network configuration
trusts Android system CAs, denies cleartext traffic, matches only
`api.whysoezzy.online`, and has no pin contract. Do not install an unsigned
stable artifact or a locally assembled release and call it protected stable
evidence.

## 4. Common execution rules

1. Start from the state required by the case. Record whether the app was
   force-stopped, relaunched, or cold-started.
2. Perform each action in order and record the adjacent observable result.
3. When a case says reload, force-stop and relaunch the app, then wait for the
   initial network load. A mere recomposition is not persistence proof.
4. For a request-failure check, record the precise failure injection and restore
   networking before proceeding. Do not reuse a stale screen as proof of a
   successful request.
5. Mark each case exactly `PASS`, `FAIL`, or `BLOCKED`. Use `BLOCKED` when a
   required account, fixture, backend capability, device observation, artifact
   identity, or protected input is unavailable. Include the reason and the
   smallest next step.
6. A case is `FAIL` when its prerequisite was available but an observed result
   differs from the expected result. Preserve the observation and do not
   silently retry it into a pass.

## 5. Smoke cases

### Case 1 — Email OTP login

Initial state: clear the app's local authenticated session, force-stop, and
launch the selected package while logged out.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 1.1 | Open the email sign-in screen and enter the authorized returning-user email. | The email is accepted; no prior authenticated main screen is visible. |
| 1.2 | Request an OTP and record the request time, without recording the OTP. | A clear request-success state is shown and the authorized account receives the OTP. |
| 1.3 | Enter the current OTP once and submit. | The app authenticates and reaches the main screen; protected content is loaded. |
| 1.4 | Force-stop and cold-start the app. | The authenticated session persists locally and the app returns to protected main content without asking for OTP again. |
| 1.5 | Navigate away and back to the main screen. | Authenticated navigation remains stable and no duplicate login or retry loop appears. |

Lifecycle proof: steps 1.1 and 1.4 prove logged-out startup and authenticated
cold-start persistence. Record server/account fixture and final route.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

### Case 2 — Automatic access-token refresh

Initial state: authenticated from Case 1, with the account on a protected
screen. Use a backend-supported expiry or a controlled natural expiry; record
how it was induced and its UTC time.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 2.1 | Allow or induce the access token to expire while retaining a valid refresh token. | The expiry is observable or confirmed by the fixture; the refresh token remains valid. |
| 2.2 | Trigger a protected request from the current flow. | The app refreshes transparently, retries the protected request once as designed, and shows the requested content without routing to email. |
| 2.3 | Continue interacting with the current screen. | The current navigation/state is retained; no duplicate refresh loop, blank screen, or unexpected logout occurs. |
| 2.4 | Force-stop and relaunch after the successful refresh. | The refreshed authenticated state remains usable and a protected request succeeds. |
| 2.5 | If the fixture permits it, invalidate the refresh token and trigger another protected request. | The app clears authentication, routes to the email screen, and does not loop. Record this as a separate invalid-session observation. |
| 2.6 | Restore networking and repeat only if a transient network failure was injected. | A transport failure is reported/recoverable; it is not mislabeled as an invalid refresh token or used as proof of session expiry. |

If 2.1 cannot be controlled or confirmed, mark this case `BLOCKED` and state
what backend fixture or expiry control is missing. Do not infer refresh from a
normal successful request.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

### Case 3 — Main screen and pull-to-refresh

Initial state: authenticated, with the main screen opened from a cold start.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 3.1 | Inspect the initial main screen. | Required sections and their loading/empty/error states render without overlap, crash, or stale login route. |
| 3.2 | Scroll through each available paginated section. | Content loads page by page as requested; no duplicate page, stuck spinner, or unexplained empty page appears. Record fixture/page observations. |
| 3.3 | Pull down until refresh starts, then release. | A visible refresh indicator appears and completes; the screen remains usable and the latest backend response is rendered. |
| 3.4 | Change or identify a fixture value on the backend, pull to refresh again, and compare. | The changed data becomes visible, or the unchanged response is explained by the fixture; the completion state is observable. |
| 3.5 | With networking temporarily disabled, trigger refresh or retry on an error-capable section. | A visible error/retry state appears and the app does not report stale data as a fresh successful response. |
| 3.6 | Restore networking and use the visible retry or refresh action. | The request recovers and content renders without a navigation or authentication loop. |

Lifecycle proof: 3.2 checks pagination and 3.3–3.6 check refresh,
failure visibility, recovery, and fresh data.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

### Case 4 — Profile

Initial state: authenticated returning user with a known profile fixture.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 4.1 | Open the self-profile from the authenticated app. | The profile identifies the returning user and shows current profile data. |
| 4.2 | Inspect the meetings and communities sections. | The displayed memberships/content agree with the known fixture and load/error states are understandable. |
| 4.3 | Enter profile edit and change one permitted non-secret field. | Edit controls open; validation is visible and the save action is enabled only for valid input. |
| 4.4 | Save the change, then navigate away and return. | Save reports success and the changed value is rendered on the self-profile. |
| 4.5 | Force-stop and relaunch, then open self-profile again. | The saved profile value persists after a cold start and re-fetch; no account crossover is visible. |
| 4.6 | Restore the original field value if the fixture is reusable. | The original value is saved successfully or the residue is recorded for cleanup. |

Lifecycle proof: 4.4 and 4.5 cover navigation and cold-start persistence.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

### Case 5 — Meeting join and leave, including rollback

Use an internal meeting fixture. Do not use a meeting whose action sends the
user to external registration.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 5.1 | Open the meeting details and record its initial membership/button state. | The fixture is the intended internal meeting and the initial state is observable. |
| 5.2 | Tap Join and wait for the request/result state. | The button/count changes to the joined state only after the app's success behavior; no duplicate action is shown during the request. |
| 5.3 | Force-stop and relaunch, then re-open the same meeting. | The joined membership persists from a fresh load and agrees with the backend. |
| 5.4 | Tap Leave and wait for completion. | The button/count returns to the not-joined state and the success is observable. |
| 5.5 | Force-stop and relaunch, then re-open the meeting. | The leave persists after reload and the backend/UI agree. |
| 5.6 | Record the current state, disable networking or otherwise induce one join/leave request failure, then perform the opposite action. | The optimistic button/count change is rolled back to the recorded prior state when the request fails; the error is visible and the membership is not falsely committed. |
| 5.7 | Restore networking and re-fetch the meeting. | The backend state and displayed state agree; record any intentional fixture change for cleanup. |

Rollback proof is mandatory: a failure that leaves the optimistic state visible
is `FAIL`, even if a later reload happens to correct it.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

### Case 6 — Community subscribe and unsubscribe, including rollback

Use the community details action and record the initial membership and count.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 6.1 | Open community details from the supported app action. | The intended community fixture opens and its initial subscribe state/count are visible. |
| 6.2 | Tap Subscribe and wait for the request/result state. | The button and subscriber count reflect the subscribed state after success; duplicate submission is prevented while pending. |
| 6.3 | Force-stop and relaunch, then re-open community details. | Subscription persists after reload and agrees with the backend/count. |
| 6.4 | Tap Unsubscribe and wait for completion. | The button/count return to the unsubscribed state and success is visible. |
| 6.5 | Force-stop and relaunch, then re-open community details. | Unsubscription persists after reload. |
| 6.6 | Record the current state, disable networking or induce one subscribe/unsubscribe request failure, then perform the opposite action. | The optimistic button/count update rolls back to the recorded prior state; the error is visible and no false membership remains. |
| 6.7 | Restore networking and re-fetch the community. | Backend and UI membership/count agree; record any fixture residue for cleanup. |

Rollback proof is mandatory and has the same `FAIL` rule as Case 5.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

### Case 7 — Avatar upload

Use only permitted test images. Record filename, format, byte size, and whether
the fixture's original avatar can be restored. Do not commit image data.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 7.1 | Open self-profile edit and select a JPEG, PNG, or WebP test image. | The supported image is accepted; record the exact test file metadata. |
| 7.2 | Exercise boundary fixtures from 1 byte through 5 MiB where supported by the test set. | Each accepted size/type follows the app's validation; any rejected size/type has a clear validation result. Record every boundary observation. |
| 7.3 | Start upload and observe immediately while the request is in flight. | The upload control is disabled or otherwise prevents duplicate submission and visible progress/loading is shown. |
| 7.4 | Wait for upload completion and return to the profile. | The uploaded avatar renders from the returned/stored image and no broken-image placeholder or stale avatar remains. |
| 7.5 | Save the profile, navigate away, and return. | The profile save succeeds and the new avatar remains visible after navigation. |
| 7.6 | Force-stop and relaunch, then open self-profile. | The avatar persists after cold start and re-fetch. |
| 7.7 | Restore the original avatar/data when the fixture is reusable. | Restoration succeeds and is verified after reload, or residue is recorded for cleanup. |

Lifecycle proof: 7.5–7.6 cover navigation and cold-start persistence. A missing
test image, fixture permission, or ability to observe upload progress is
`BLOCKED`, with the missing prerequisite recorded.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

### Case 8 — Logout

Initial state: authenticated and able to open self-profile.

| # | Action | Expected result / evidence |
| --- | --- | --- |
| 8.1 | Open self-profile and invoke Logout. | The logout action is reachable and the app clears local authenticated state. |
| 8.2 | Observe the root navigation after logout. | The app routes to the email/authentication screen; protected main/profile content is no longer the root destination. |
| 8.3 | Force-stop and cold-start the app. | The logged-out state persists and the email screen remains the entry point. |
| 8.4 | Attempt to open protected content through any available stale/back/deep navigation path. | Protected content is unavailable without authentication and no navigation loop or stale private data is exposed. |
| 8.5 | If server logout failure can be safely induced, repeat logout and observe. | Local auth is still cleared and the app reaches email even when the server logout request fails; record the server failure separately. |

Lifecycle proof: 8.3 proves logout persistence across process restart. Do not
classify a transport failure as a successful server logout; the local
protection result must still be observable.

**Status:** `PASS` / `FAIL` / `BLOCKED`
**Evidence and notes:**

## 6. Cleanup and final disposition

After the eight cases:

1. Restore meeting membership to its recorded initial state; re-fetch the
   meeting and record the final state.
2. Restore community subscription to its recorded initial state; re-fetch the
   community and record the final state/count.
3. Restore the original avatar and any profile field changed for the run;
   reload self-profile and record the result.
4. Restore device networking, proxy, DNS, mock, and backend fixture settings.
5. Force-stop/uninstall the tested package if the device is shared, and remove
   only non-secret temporary downloads/evidence. Do not delete the canonical
   artifact or its identity metadata.
6. Record every residue, failed restoration, unavailable cleanup action, and
   backend-side change. A cleanup failure does not become a pass by omission.

### Result summary

| Case | Status (`PASS`, `FAIL`, or `BLOCKED`) | Evidence reference / notes |
| --- | --- | --- |
| 1. Email OTP login |  |  |
| 2. Automatic access-token refresh |  |  |
| 3. Main screen and pull-to-refresh |  |  |
| 4. Profile |  |  |
| 5. Meeting join and leave |  |  |
| 6. Community subscribe and unsubscribe |  |  |
| 7. Avatar upload |  |  |
| 8. Logout |  |  |

**Overall disposition:** `PASS` / `FAIL` / `BLOCKED`
**Overall rationale:**

The overall result is `PASS` only when all eight cases are `PASS`, the exact
artifact/package/backend identity is recorded, snapshot evidence (when
selected) is chain-verified, and cleanup is complete or explicitly accepted.
Any observed behavior mismatch is `FAIL`. Any unavailable prerequisite or
unverifiable identity is `BLOCKED`. Runtime smoke completion must not alter the
protected release authority or publication rollback policy.
