# Features (current build)

Settings includes Debug below Privacy Policy. It provides local timestamped event
logs, current settings/permission snapshots, confirmed Share and Clear actions,
and a seven-day/10 MB rolling limit. API responses and usernames are excluded.
No automatic report upload is configured. See [Debug diagnostics](diagnostics.md).

## Countdown reminders

- Three stages before LeetCode's 00:00 UTC reset, defaulting to 6, 3, and 1 hours.
- Edit opens the Notification times popup with a time picker for each stage. Choose exact UTC clock times to the minute, after 00:00 and before the next reset, in stage order and more than 30 minutes apart. Save commits and re-arms them; Cancel leaves the schedule unchanged. Existing hour-based settings retain their original times.
- AlarmManager schedules the stages, using exact alarms when permitted and an
  inexact fallback otherwise. The receiver queues a WorkManager reminder check.
- Each stage fetches status. Confirmed unsolved challenges produce a notification
  opening the problem in a browser; solved challenges stay silent. Once solved,
  a durable account/day marker suppresses all further daily checks (including
  manual refresh and sync-on-open), notifications, and retries until UTC midnight.
  The existing cache remains visible. Changing account uses that account's own marker.
- Vibration Pulse controls vibration for all three stages, test notifications,
  and connection-failure notices. Off disables their vibration; on uses the same
  pulse for each. The first stage stays soundless (default importance with vibration,
  low importance without); later stages use high importance. Android channel settings apply.
- Durable delivery state prevents duplicate delivery. Workers recheck the day,
  settings signature, delivery state, and stage cutoff after fetching.

## Failures and retries

- One connection/API failure notice per continuous outage, shared across tiers
  and retries. A successful status fetch ends the outage and clears the notice.
- Retries run approximately every 10 minutes. For the first two stages, they stop
  30 minutes before the next stage. Earlier-stage work cannot deliver after its cutoff.
- The final stage allows three retries after the initial failed attempt; all
  attempts are bounded by the end of the UTC platform day.
- Failed checks preserve previously known same-day status/details where possible.
  Unknown results do not produce an ordinary unsolved reminder.

## Cache and silent refresh

- Today loads the latest cached row for the current UTC day and username.
- **Check now** fetches immediately. Manual checks update status without sending
  an ordinary scheduled reminder.
- **Sync when app opens** is enabled by default and refreshes on activity start.
- Background sync uses WorkManager at 15, 30, or 60 minutes (default 15), with a
  connected-network constraint. Actual execution times are controlled by Android.
- A separate silent UTC-midnight refresh re-arms daily. If no status or question
  details are obtained, it allows up to six retries at approximately 30-minute intervals.
- The most recent cached streak can be reused across days for the same username.

## Screens and settings

- **Today:** challenge/status card, challenge details and browser action, streak,
  countdown, manual check, and last-checked time.
- **Settings:** LeetCode username and public-profile connection test; reminder
  enable switch; custom-time popup; display-timezone selector; haptics; Alarm Settings; compact 12 hr / 24 hr selector first under Others; sync interval
  and sync-on-open toggle; shortcuts for missing notification/exact-alarm
  permissions; test notification; local-data deletion; version and privacy link.
- Settings switches save automatically; reminder-time drafts require Save. Changing username or reminder times re-arms reminders;
  disabling reminders cancels reminder work/retries. Silent sync is separate.
- Local-data deletion resets preferences/history and notification/delivery/alarm
  state, cancels countdown work, and restores the default periodic sync interval.
  It does not change the LeetCode account or replay first-install onboarding.
- The former LeetCode detail screen and navigation entry have been removed.

## Time display

The Settings reminder-time display uses the selected zone (UTC by default), with
time and timezone on one line. Today timestamps and reset-time text use the
device timezone. All reminder offsets and platform-day keys remain anchored to UTC.
The existing timezone selector remains in place while redesign work is deferred. The 12 hr / 24 hr preference (default 24 hr) controls Settings reminder times, the UTC time popup/pickers, and Today last-checked timestamps. T-minus labels are removed; each displayed time uses its stage color. Haptics reads "Vibration pulse".

## Implemented but disabled: ringing alarms

`AlarmFeature.ENABLED = false` remains false. The Alarm Settings block has an off, disabled ringing switch and an availability note. Explicit user approval and device verification are still required before enabling ringing. Normal reminder
notifications, including the final stage, and API retries remain active.

The inactive implementation uses a bundled sound and foreground playback service,
with Stop and 10-minute Snooze controls. Rings last up to 60 seconds; snoozed and
unattended repeats count toward a maximum of three rings, bounded by UTC midnight.
Stop cancels repeats; completion and relevant settings changes cancel the occurrence.
Occurrence state survives process restarts and supports restoration after reboot.
Its final-failure fallback would ring only for an unsolved or unknown cached status,
not a known solved status. No fallback ringing occurs while the feature is disabled.

Playback, notification actions, and layout still require device/emulator verification.
