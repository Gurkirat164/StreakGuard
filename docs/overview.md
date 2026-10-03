# Overview

StreakGuard is an Android app (Kotlin + Jetpack Compose, package
`com.streakguard.app`, current version 0.2.0) that protects your daily coding
streak.

**What it does today:** once a day, at a time you choose (default 9:00 PM), it
checks whether you solved LeetCode's Problem of the Day. If you haven't, it
sends you a notification. If you have, it stays silent (unless you enable the
optional "confirm when done" notification).

**Key properties of the current build:**

- Fully on-device. No developer server, no account, no password. The only
  thing you enter is your public LeetCode username.
- Checks LeetCode's public GraphQL API for today's challenge and your recent
  submissions.
- A platform is "done" for the day when you have an accepted submission for
  today's challenge timestamped at or after the start of the current UTC day
  (LeetCode flips its day at UTC midnight).
- Results are cached on the device per UTC day: reopening the app shows the
  last check instead of refetching.
- All times shown in the UI use the device's local timezone; the backend
  always works in UTC.
- Extensible through the `StreakPlatform` plugin interface — new platforms are
  added by implementing the interface and registering one line.
