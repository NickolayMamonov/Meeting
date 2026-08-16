# MEE3-50 rework checklist

- [x] Serialize account exit, message ingress, and owner/state reads under one cleanup mutex.
- [x] Delete the authenticated push installation on explicit logout/account transitions before local owner cleanup; preserve bounded timeout and terminal 204/404 handling.
- [x] Retain denied-permission events as `PENDING_DISPLAY` and drain them after permission/lifecycle recovery.
- [x] Wire durable constrained WorkManager enqueue/cancel/retry/exit behavior through an injectable worker factory boundary.
- [x] Rearm registration after a credential-version 401 when the same user's credential advances.
- [x] Gate cold-start tap navigation until `MeetNavHost` is ready and suppress same-route duplicates.
- [x] Quarantine malformed/version-invalid envelopes as `SUPPRESSED_CORRUPT`; accept reminder offsets lexically only as `60` or `1440`.
- [x] Replace lossy join permission signaling with a durable consumable eligibility handoff and add focused API 33+ policy tests.
- [x] Complete the implementation checklist only after focused tests pass.
- [x] Run required static/build checks and record unavailable runtime/backend/release evidence without inventing claims.

## Implementation rework gate — 2026-08-16

- [x] Guard authenticated `onUnregistered` wake-up and same-FID terminal/retry rearm.
- [x] Serialize reconciliation and fence Firebase/API completion with owner, generation, operation, pending FID, installation ID, nonce, and terminal nonce.
- [x] Make explicit logout auth clearing exception-safe; remove installation DELETE from successful account deletion and forced-logout observation.
- [x] Use Koin AndroidX WorkManager with `REPLACE`; persist the encrypted migration marker.
- [x] Fail closed for existing empty/version-invalid envelopes and validate canonical persisted IDs/timestamps.
- [x] Preserve DELETE repository failures and terminal malformed-success classification.
- [x] Add focused fencing, reducer, and account-cleanup tests.
- [x] Re-run focused tests, full relevant unit suites, ktlint, lint, debug assemble, dependency insight, and diff check.

## Fresh implementation findings — 2026-08-16

- [x] Separate explicit logout's bounded installation DELETE from forced `UnauthorizedError` logout and successful account deletion.
- [x] Fence the full account-exit interval from state cleanup through auth/session cleanup; block `onUnregistered` and `onRegistered` resurrection during the fence.
- [x] Apply the individual 500 ms `clearAccountState` bound to account deletion.
- [x] Validate account-changed tombstone owner `userId` and `generation` semantics.
- [x] Advance `statusChangedAt` for display, navigation-claim, and navigation-complete transitions.
- [x] Atomically migrate valid legacy authenticated credential stores to a random epoch revision 1 and fail closed on corrupt metadata; preserve the epoch across clears.
- [x] Perform bounded installation DELETE before authenticated A-to-B owner replacement cleanup.
- [x] Add focused forced-exit, exit-race, account-transition DELETE, tombstone, retention, migration, and corruption tests.
- [x] Re-run the local verification matrix: focused exit/fencing/state/auth tests, module unit tests,
  app/core-auth ktlint, app lint, and debug assemble.
- [ ] Run core-auth instrumentation for malformed ciphertext when an API 33+ authorized device is
  supplied; record external runtime/snapshot/release gates.

- [ ] API 33+ authorized-device runtime, backend fixture, snapshot Firebase client, and release-signing prerequisites remain environment gates.

## Implementation session — approved gate findings (2026-08-16)

- [x] Restore the auth token keyset backup exclusion in both Android backup rule files.
- [x] Fence delayed Firebase data-message ingress by exit epoch and keep the service alive through synchronous durable handoff completion.
- [x] Make tap claim, navigation, and completion execute under the account-exit fence.
- [x] Revalidate the captured exit epoch before `onRegistered` scheduling.
- [x] Persist and classify explicit logout installation DELETE outcomes after local binding clear.
- [x] Re-run the required targeted/static/build verification and record external gates.

## Fresh Implementation session — latest approval findings (2026-08-16)

- [x] Capture the Firebase service callback exit epoch before the synchronous handoff and pass the immutable epoch into durable message processing.
- [x] Add a service-path A-exit-B race test proving a stale callback cannot be attributed to B.
- [x] Re-enqueue a guarded current-state reconciliation after transient/timeout A-to-B installation DELETE failure.
- [x] Assert scheduler activity and eventual B installation registration after cleanup retry.
- [x] Recover corrupt credential metadata into a fresh epoch at revision 0 and advance to revision 1 only on the next complete authenticated write, with instrumentation coverage.
- [x] Return evicted displayed notification IDs from bounded-ledger pruning and best-effort cancel them after the atomic state commit, with reducer/coordinator coverage.
- [ ] Re-run snapshot/release/device/backend gates; prerequisites remain externally blocked.
