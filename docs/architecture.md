# Architecture

Confirmed completion is durable per platform/account/UTC day in SettingsStore.
DailyCheckOrchestrator checks it before any platform fetch, including manual checks,
and adopts solved cache rows from earlier versions. Completion cancels today's
retry alarms, queued reminder work, periodic polling, and visible reminder/failure
notifications. Future daily alarms move to tomorrow; the midnight receiver restores
normal polling for the new day. Reminder delivery shares the orchestrator mutex so
a concurrent completion cannot race a stale unsolved notification.

Source root: `app/src/main/java/com/streakguard/app/`.

| Package | Responsibility |
|---------|----------------|
| `diagnostics/` | Sanitized event collection, segmented rolling storage, access policy, retention worker, and report export/delivery |
| `ui/` | Today and Settings navigation, view models, onboarding, splash, theme |
| `domain/` | `DailyCheckOrchestrator`: checks, cache preservation, outage recovery, deletion |
| `platform/` | `StreakPlatform`, registry, LeetCode API implementation |
| `data/local/` | Room database and check history |
| `data/prefs/` | DataStore settings |
| `schedule/` | Reminder offsets/policy, AlarmManager scheduling, WorkManager workers, durable delivery state |
| `receiver/` | Reminder dispatch, silent midnight refresh, reboot/clock/timezone/update re-arming |
| `notify/` | Notification channels and disabled ringing service/controls |
| `util/` | UTC day boundaries and device-local timestamp formatting |
| `di/` | `AppContainer`, manual dependency container |

## Platform contract

`StreakPlatform` exposes `getDailyChallenge()`, `isCompletedToday(username)`
(`true`, `false`, or `null` for unknown), and `getStreak(username)`.
LeetCode uses public GraphQL requests through OkHttp and org.json.

The registry and check/history loop support multiple platform IDs. Settings,
`AlarmScheduler.signature()`/`deliveryKey()`, and reminder delivery currently
assume LeetCode (the worker uses the first returned status). Supporting another
platform requires extending these paths as well as implementing and registering it.
Current scheduling and cache day keys assume UTC platform days.

## Scheduled reminder flow

1. `AlarmScheduler` arms three daily UTC-reset-relative triggers. It tries
   `setExactAndAllowWhileIdle` and falls back to `setAndAllowWhileIdle` if denied.
2. `DailyCheckReceiver` queues unique expedited `ReminderCheckWorker` work, with
   a non-expedited fallback, and re-arms the daily triggers.
3. The worker validates its day, tier, settings signature, prior delivery/attempt,
   and cutoff. It calls `DailyCheckOrchestrator.runCheck()` and validates again.
4. The orchestrator serializes checks, loops enabled platforms with usernames,
   fetches data, and writes Room history while preserving known same-day data.
   Successful status fetches clear outage state; confirmed completion stops any
   matching ringing occurrence.
5. The worker owns scheduled notifications and retry decisions. Solved results
   are silent; unsolved results notify; unknown results use bounded retries and
   a shared one-notice-per-outage policy. `ReminderState` records delivery/attempts.
6. Final-stage ringing and its unknown-status fallback are guarded by
   `AlarmFeature.ENABLED = false`; the final-stage ordinary notification stays active.

## Silent refresh and UI

`HomeViewModel` loads today's cached row and observes history/username changes.
Manual checks reuse the orchestrator. `MainActivity.onStart()` requests a refresh
when sync-on-open is enabled. `StatusSyncWorker` performs periodic connected-network
refreshes. `MidnightRefreshReceiver` silently refreshes at UTC midnight, re-arms
itself, and supports six 30-minute retries when no status/details were fetched.

`BootReceiver` handles reboot, clock change, timezone change, and package replacement:
it re-arms reminders/midnight refresh and periodic sync, and restores eligible
ringing state except on timezone-change broadcasts. Disabled ringing restoration
clears any pending occurrence.

## State and deletion

Room holds check history; DataStore holds settings. SharedPreferences persist
reminder deliveries/outages, pending reminder retries, and ringing occurrences.
See [Data & storage](data-storage.md) for fields and defaults.

Settings edits serialize through a mutex. Local deletion cancels countdown work
and scheduled reminders, then clears preferences/history and notification/alarm
state under the check mutex so an in-flight fetch cannot repopulate deleted data.
Default periodic sync is re-established afterwards.

The V2 time popup edits UTC clock times to the minute. `ReminderSchedule` converts them into minute offsets before reset and validates the order and retry windows. DataStore reads legacy hour offsets as minutes until the first custom save. `ClockFormat` handles 12/24-hour display independently of scheduling. The alarm switch remains disabled behind the hard feature gate; reminder workers also check the saved ringing preference before any future alarm start.
