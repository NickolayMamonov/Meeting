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
