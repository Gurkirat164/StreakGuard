# StreakGuard

StreakGuard is an Android app that checks LeetCode's Problem of the Day (POTD)
and reminds you before the daily reset when it is still unsolved.

Current app version: **0.4.5** (`versionCode` **13**).

## How it works

1. Enter your public LeetCode username in Settings and use **Test Connection** to verify it.
2. Reminder tiers default to **6, 3, and 1 hours before 00:00 UTC**. Use **Edit** to
   set three exact UTC clock times, including minutes, in the Notification times popup. Keep them in stage order and more than 30 minutes apart.
3. At each tier, the app fetches the daily challenge and checks recent accepted
   submissions for its slug since the start of the current UTC day.
4. A confirmed unsolved result posts a notification that opens the challenge in
   your browser. A solved result stays silent. Failed checks use bounded retries
   and one connection/API failure notice per continuous outage.

The app also refreshes silently every 15, 30, or 60 minutes (default 15), on app
open by default, and at UTC midnight. Background timing depends on Android's
scheduling and battery restrictions. Cached status appears while fresh data loads.

No StreakGuard server, login, password, or API key is required. Checks run on the
phone and send your public username to LeetCode. See the [privacy policy](Policies/privacy-policy.html).

## Timezones

LeetCode resets at **UTC midnight** (5:30 AM in India). Reminder offsets always
refer to that reset. The Settings display-timezone picker defaults to UTC and
only changes the reminder times shown there; it does not move scheduled reminders.
The compact 12 hr / 24 hr selector under Others controls reminder and popup formatting and Today timestamps. Today timestamps use the device timezone.

The original timezone selector and single-line time/timezone layout are retained.
Further timezone redesign work is deferred; see [TODO.md](TODO.md).

## Setup and build

- Android 8.0 / API 26 or newer device or emulator.
- JDK 17 and an Android Studio version compatible with the configured Android
  Gradle Plugin. See [Building](docs/building.md) for tool versions and commands.
- Open the project, sync Gradle, and build the debug APK at
  `app/build/outputs/apk/debug/app-debug.apk`.
- Launch once, enter your username, and allow notifications and exact alarms.
  Settings provides shortcuts for missing permissions and a test notification.
- Settings switches save automatically; custom reminder times require **Save times**. Reminder and username changes re-arm the
  scheduled checks. Reboot, clock/timezone changes, and app updates also re-arm them.

## Reminder retries and alarm status

Scheduled failures show one failure notice per continuous outage. Retries run
approximately every 10 minutes. A successful status fetch ends the outage. The
first two tiers stop retrying 30 minutes before the next tier; delayed work cannot
send an earlier-tier reminder after that cutoff. The final tier allows three
retries after the initial attempt, bounded by UTC midnight.

**Alarm ringing remains disabled with `AlarmFeature.ENABLED = false`.** Ordinary
notifications, including the final tier, and API retries remain active. The Alarm Settings switch is present but disabled. Explicit user approval and device verification are still required before enabling
ringing. The bundled sound, foreground service, Stop/Snooze controls, and durable
three-ring limit are implemented but inactive; see [Features](docs/features.md).

## Project layout and documentation

- `app/src/main/java/com/streakguard/app/platform/` — platform contract and LeetCode API implementation.
- `domain/` — checks, cache preservation, recovery, and local-data deletion.
- `schedule/` and `receiver/` — UTC reminder tiers, workers, retries, and midnight refresh.
- `notify/` — notification channels and the disabled ringing system.
- `data/` — Room history, DataStore settings, and related state.
- `ui/` — Today and Settings screens, onboarding, splash, and theme.
- [Documentation index](docs/README.md) — features, architecture, storage, design, build, and versioning.

Paths after the first bullet are relative to the same application package directory.

## Adding a platform

Implement `StreakPlatform` and register it in `AppContainer`. The check orchestrator
and history support platform IDs, but Settings, reminder scheduling, and reminder
worker delivery currently contain LeetCode-specific assumptions. Extend those
paths and verify each platform's day boundary before claiming full support.

## Limitations

- LeetCode's public GraphQL endpoint is unofficial and may change. Failed checks
  produce unknown results and preserve previously known data where available.
- The completion check uses recent accepted submissions; it is not a full submission-history scan.
- The phone must be on. Android/OEM battery restrictions, work quotas, network
  availability, and permission settings can delay or suppress reminders. Without
  exact-alarm access the scheduler falls back to inexact alarms.
- Releases use R8 and resource shrinking; the UI uses AndroidX edge-to-edge with
  safe system insets. Play Console metric confirmation, the remaining OS/navigation
  layout matrix, timezone redesign, and inactive ringing verification are tracked
  in [TODO.md](TODO.md).
