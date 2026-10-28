# Debug diagnostics

Settings shows **Debug** directly below Privacy Policy. Logs are local and enabled
for the current rollout. Share and Clear both require confirmation. Sharing opens
Android's chooser; the user chooses an app and recipient. No upload server, remote
flag service, analytics SDK, or automatic transfer is configured.

## Stored information

Events contain epoch milliseconds and ISO UTC timestamps, fixed event names, and
allowlisted fields. They cover startup/screens, sanitized settings snapshots,
permission state, notification channel importance/vibration, scheduling, exact vs
inexact fallback, checks, HTTP status/duration, failures by exception class, retries,
worker outcomes, solve suppression, cancellation, and notification posting results.
Uncaught errors include exception type and up to 12 code frames, without exception
messages or payloads. `NOTIFICATION_POSTED` means `NotificationManager.notify`
returned; Android/OEM settings may still prevent display, sound, or user visibility.

No API bodies, GraphQL queries, headers, usernames, challenge text/URLs, credentials,
IP/SSID, advertising IDs, or persistent device identifiers are collected. Account
configuration/verification is recorded only as booleans. Reports include app
version/code, manufacturer/model, Android API, device timezone/time, current app
settings, notification/exact-alarm permissions, channel state, network validation,
battery optimization exemption, and power-save status. Review new fields against
`DiagnosticFields` before adding them; never pass raw errors or response objects.

## Retention, storage, and export

- Retained event bytes are capped at **10,000,000 bytes (10 MB)** and **7 × 24 hours**.
  The earlier limit wins. Individual expired/oldest entries are removed, including
  entries inside a segment. UTF-8 bytes, not character count, determine the cap.
- Logs live in `noBackupFilesDir/diagnostics`, excluded from Android backup.
  Bounded asynchronous collection and synchronized 64 KiB segments keep disk work
  off the UI thread. A saturated queue records an EVENTS_DROPPED count on recovery;
  logging/storage failure never prevents a reminder from running.
- Cleanup runs on writes, startup, viewing/export, and a best-effort hourly worker.
  Android may defer idle maintenance; any access/export first removes expired data.
- Share prepares a ZIP only after confirmation. One temporary export is retained
  under `cacheDir/diagnostic_reports`; it is replaced on the next export, expires
  after 24 hours at cleanup, and may be removed earlier by Android. This temporary
  export and filesystem metadata are additional to the 10 MB retained-event limit.
- FileProvider exposes only the export directory, with temporary read permission.
  Private live logs and arbitrary app files cannot be shared through it.
- Clear deletes retained logs and temporary reports, and revokes their URI grants.
  New events resume afterward. Copies already sent outside the app are unaffected.
  Settings' Delete all your data also clears diagnostics.

## Future access and delivery changes

`DiagnosticsAccessPolicy` is injected by AppContainer. Its collect/visible/share
permissions are enforced by both collection and export, not just the settings UI.
`DiagnosticsFeaturePolicy.ENABLED` is the single global build-time switch; turning
it off in an app update hides Debug, stops collection, cancels maintenance, and
purges local logs/reports on startup. This does **not** remotely disable old builds.

For a future remote/cohort rollout, replace the policy with a cached eligibility
provider and call `DiagnosticsService.refreshAccess()` whenever eligibility changes.
The observable access state updates the UI; revoking all access purges the files.
Do not use a public username as authentication for a privileged upload endpoint.

`DiagnosticReportDestination` separates export creation from delivery. The current
AndroidShareDestination uses the system Sharesheet. A future server destination can
reuse the same sanitized ZIP, but must add an authenticated endpoint, matching
consent text, transport/error handling, and privacy disclosures before enabling it.

## Validation

Run unit tests for timestamp expiry, simultaneous limits, UTF-8 byte bounds,
restart, concurrency, clock rollback, clear, and field redaction. The isolated
optimized QA test also exercises the ZIP, FileProvider scope, successful notification
posting, settings/permission snapshots, export clearing/revocation, and access removal:

```powershell
.\gradlew.bat assembleOptimizedTest assembleOptimizedTestAndroidTest -PoptimizedSmoke
adb shell pm grant com.streakguard.app.qa android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -e diagnostics true com.streakguard.app.qa.test/com.streakguard.app.OptimizedSmokeRunner
```

Use only the `.qa` test package: this test clears its logs and uses a fake username.

Validated on 2026-10-18: debug and optimized release APK/AAB builds, archived release
symbols, all 34 unit tests, and lint (0 errors; 24 existing warnings and 1 hint).
The optimized API 37 device test passed report ZIP readability, posting-success
logging, settings/permission snapshots, response/username exclusion, FileProvider
scope, clear/revocation, and access-disable purging. Manual UI checks confirmed Debug
below Privacy Policy, the Share disclosure/Android chooser, and the Clear warning
and completion message. No report was sent to an external recipient during testing.
