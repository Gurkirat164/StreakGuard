# Architecture

```
ui/            Compose screens (Today / LeetCode / Settings), theme, nav
domain/        DailyCheckOrchestrator — runs checks, logs, notifies
platform/      StreakPlatform interface + PlatformRegistry + implementations
data/local/    Room database (check_log)
data/prefs/    SettingsStore (DataStore Preferences)
schedule/      AlarmScheduler (exact daily alarm)
receiver/      DailyCheckReceiver, BootReceiver (re-arm alarms)
notify/        NotificationHelper
util/          TimeUtils (UTC backend / local display rules)
di/            AppContainer — manual dependency container, everything lazy
```

## Plugin architecture

`StreakPlatform` (`platform/StreakPlatform.kt`) defines the contract:

- `getDailyChallenge()` — today's challenge (title, slug, url)
- `isCompletedToday(username)` — `true` / `false` / `null` (unknown)
- `getStreak(username)` — current streak, if the platform exposes one

`PlatformRegistry` holds the list. Adding a platform = implement the
interface + one line in `AppContainer`. Each platform converts to UTC inside
its own implementation; the orchestrator never assumes a timezone.

## Check flow

1. `AlarmScheduler` fires `DailyCheckReceiver` at the set time (or the user
   taps CHECK NOW / REFRESH STATUS).
2. `DailyCheckOrchestrator.runCheck()` loops over enabled platforms with a
   username, calls the platform, and inserts a `CheckLog` row keyed by the
   UTC date.
3. Notifications go out for missed (or optionally completed) challenges.
4. `HomeViewModel` reads the cached row for today's UTC date on launch and
   after every manual check — the UI never fetches directly.
