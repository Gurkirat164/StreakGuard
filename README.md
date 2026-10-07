# StreakGuard

An Android app that checks whether you've completed LeetCode's Problem of the Day (POTD)
and notifies you at a set time each day if you haven't — so you never break your streak.

Built with a plugin architecture: each coding platform implements one interface
(`StreakPlatform`), so adding Codeforces, CodeChef, etc. later is a small, isolated change.

## How it works

At your chosen time each day, an exact alarm fires and the app:

1. Fetches today's POTD from LeetCode's GraphQL endpoint (`activeDailyCodingChallengeQuestion`)
2. Checks your recent accepted submissions for that problem's `titleSlug`
3. If there is no accepted submission since the start of the current UTC day → posts a
   notification; tapping it opens the problem in your browser
4. If the POTD is done → stays silent (or sends a "streak safe" confirmation if you
   enable it in Settings)

Everything runs on-device. No server, no account, no API key — just your LeetCode username.

### Day-boundary note

LeetCode's "day" flips at **UTC midnight** (5:30 AM IST). A submission counts for
"today" when its timestamp is at or after the start of the current UTC day. Set your
reminder comfortably before that if you want a true last-chance nudge.

## Prerequisites

- Android Studio (Hedgehog or newer recommended)
- JDK 17 (Android Studio's bundled JBR works fine)
- A physical device or emulator running Android 8.0 (API 26) or newer

## Build & install

1. Open this folder in Android Studio (`File > Open…`, select the folder containing
   `settings.gradle.kts`).
2. Let the Gradle sync finish (the first sync downloads dependencies; needs internet).
3. `Build > Build APK(s)` → you'll get `app/build/outputs/apk/debug/app-debug.apk`.
4. Install it on your device (`adb install …` or drag onto the emulator).
5. Open StreakGuard → grant **Notifications** and **Alarms & reminders** when asked
   (the Settings screen also has shortcuts if you skip them).
6. In Settings, use **Change** to enter your public LeetCode username and **Test Connection** to verify it.
   Reminders default to 6, 3, and 1 hours before the **00:00 UTC** reset; use **Edit** to change these offsets.
   The timezone picker changes displayed times, while the underlying reset stays in UTC.
   Settings save automatically. Choose a 15, 30, or 60 minute silent sync interval, and optionally sync on app open.

Reminder changes re-arm the countdown alarms immediately, and they are re-armed
automatically after every reboot (open the app once after installing — Android only
delivers `BOOT_COMPLETED` to apps that have been launched at least once).

## Project layout

- `platform/` — the plugin layer: `StreakPlatform` interface, `PlatformRegistry`,
  and the `leetcode/` implementation (OkHttp + org.json, no auth)
- `domain/DailyCheckOrchestrator.kt` — runs the check for every enabled platform,
  writes history, decides when to notify
- `schedule/AlarmScheduler.kt` — exact daily alarm (`setExactAndAllowWhileIdle`)
- `receiver/` — `DailyCheckReceiver` (runs the check, re-arms the alarm),
  `BootReceiver` (re-arms after reboot)
- `notify/NotificationHelper.kt` — one notification channel, missed/done/test posts
- `data/local/` — Room database (`check_log` history)
- `data/prefs/SettingsStore.kt` — DataStore settings (per-platform usernames, check time)
- `ui/` — Jetpack Compose screens: Home (status cards, "Check now") and Settings

## Adding a platform (e.g. Codeforces)

1. Create a class implementing `StreakPlatform` — e.g.
   `platform/codeforces/CodeforcesPlatform.kt` with `getDailyChallenge()`,
   `isCompletedToday(username)` and `getStreak(username)`.
2. Add one line for it in the `platformRegistry` list in `AppContainer`.
3. Done — the Settings screen builds its per-platform sections from the registry,
   and scheduling, notifications and history work unchanged.

## Caveats

- LeetCode's GraphQL endpoint used here is **unofficial** and could change without
  notice. All parsing is defensive: failures surface as "Unknown", never as a crash.
- On-device scheduling means the phone must be on and online at check time. On some
  OEM skins, aggressive battery optimisation can delay alarms — exempting StreakGuard
  from battery optimisation gives the most reliable timing.
- "Unknown" results (network error, wrong username) intentionally do **not** trigger
  a notification — only a confirmed "not done" does.
