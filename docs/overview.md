# Overview

StreakGuard is an Android app written in Kotlin with Jetpack Compose, package
`com.streakguard.app`. The current version is **0.4.5**, Android version code **13**.

It checks LeetCode's Problem of the Day using your public username. Three reminder
stages default to 6, 3, and 1 hours before the **00:00 UTC** reset. A confirmed
unsolved result sends a notification; a solved result stays silent. Connection/API
failures show one notice per continuous outage and use bounded retries.

The Today screen displays cached challenge details, completion status, streak,
and a countdown. Silent refreshes run periodically (15 minutes by default), on
app open when enabled, and at UTC midnight. Settings provides username validation,
custom reminder times, 12/24-hour format, display timezone, haptics, the disabled alarm switch, sync preferences, permission shortcuts,
a test notification, and local-data deletion. These are the only two main screens.

Checks run on the device without a StreakGuard account or server. Requests to
LeetCode include the public username. Completion means a recent accepted submission
for today's challenge at or after the start of the current UTC day.

The Settings timezone defaults to UTC and controls its reminder-time display.
Today timestamps use the device timezone. Changing the display timezone does not
move the reset or reminder instants.

Alarm ringing is implemented but disabled by `AlarmFeature.ENABLED = false`.
Ordinary reminder notifications and retries remain active. Dedicated Settings
controls are present; explicit user approval and device verification are still required before enabling ringing.

The platform interface and history support extension, but Settings and reminder
scheduling/delivery currently need changes to support platforms beyond LeetCode.
