# Data & storage

Diagnostic events use private `noBackupFilesDir/diagnostics` with a seven-day/10 MB
rolling limit. Temporary shared ZIPs use a narrow cache directory exposed through
FileProvider. See [Debug diagnostics](diagnostics.md).

Checks and preferences are stored in Android's private app storage. There is no
StreakGuard server or account. LeetCode requests include the public username.
Android backup is enabled in the manifest; backup/restore behavior depends on the
system and device settings. See [the privacy policy](../Policies/privacy-policy.html).

## Room database: `streakguard.db`

Database version **3**, table `check_log`. Each persisted check inserts a row;
`getForDate(platformId, date)` returns the latest row for that platform day.

| Column | Type | Meaning |
|--------|------|---------|
| `id` | INTEGER | Auto-generated primary key |
| `platformId` | TEXT | Platform identifier, currently `leetcode` |
| `date` | TEXT | UTC platform day, `yyyy-MM-dd` |
| `completed` | INTEGER | Completion value; meaningful when `known` is true |
| `known` | INTEGER | Whether completion status is known |
| `checkedAtEpoch` | INTEGER | Check timestamp in milliseconds |
| `challengeTitle` | TEXT, nullable | Daily challenge title |
| `challengeUrl` | TEXT, nullable | Challenge browser URL |
| `streak` | INTEGER, nullable | Platform-reported streak |
| `difficulty` | TEXT, nullable | Easy, Medium, or Hard |
| `questionNumber` | TEXT, nullable | LeetCode frontend question ID |

Migration 1 to 2 added URL, streak, and known status; migration 2 to 3 added
difficulty and question number. Both migrations are registered.

Failed checks preserve existing same-day data where available instead of replacing
known status with unknown. Cache rows are only reused when `checked_username`
matches the active username. Today can reuse the most recent streak across days.

## DataStore Preferences: `settings`

| Key | Default | Purpose |
|-----|---------|---------|
| `username_<platformId>` | Empty | Public username |
| `enabled_<platformId>` | `true` | Platform check participation; not a current Settings UI toggle |
| `verified_username_<platformId>` | Empty | Username confirmed by profile test |
| `checked_username_<platformId>` | Empty | Username associated with cached checks |
| `reminders_enabled` | `true` | Scheduled reminder enable switch |
| `haptics_enabled` | `true` | Later-tier vibration preference |
| `sync_on_open` | `true` | Refresh on activity start |
| `sync_interval` | `15` | Periodic sync minutes: 15, 30, or 60 |
| `completed_day_<platform>_<username>` | absent | Last confirmed solved UTC day for this account; expired markers do not suppress a new day |
| `display_timezone` | `UTC` | Settings reminder-time display zone; `DEVICE` resolves to device zone |
| `reminder_minutes` | `360,180,60` | UTC-reset-relative minute offsets; three descending values in 1–1439, with more than 30 minutes between stages |
| `use_24_hour_time` | `true` | 24-hour vs 12-hour clock display |
| `alarms_enabled` | `false` | Ringing preference; Settings cannot enable it while the hard feature gate is false |
| `onboarding_done` | `false` | First-install onboarding completion |

The former `check_hour` / `check_minute` daily-time setting is no longer used.
There is no current "confirm when done" setting. Legacy `reminder_hours` values are read as minutes without shifting delivery times; saving custom times writes `reminder_minutes` and removes the legacy key. Malformed settings fall back to the defaults. The ringing toggle is present but disabled pending approval.

## Durable scheduling state

- SharedPreferences `reminder_delivery`: delivered keys, finished attempts, and
  continuous-outage notice state. Delivery keys include UTC day, tier, and username.
- SharedPreferences `reminder_retries`: pending retry day, attempt, timestamp, and
  signature per tier. Signatures include username, platform enable state, and offsets.
- SharedPreferences `challenge_alarm`: inactive ringing occurrence token, day,
  platform, username, title, ring count, and next trigger. Ringing is disabled by
  `AlarmFeature.ENABLED = false`.
- WorkManager stores periodic sync and queued reminder work in its own internal storage.

## Deletion

Settings > Others > Delete all your data clears saved preferences and Room history,
notifications, delivery/outage state, pending reminder retries, and ringing state.
The onboarding-complete flag is retained as true. Countdown work is cancelled and
periodic sync is re-established at its default interval; the empty username prevents
platform checks until configured again. The LeetCode account is unaffected.
Deletion is serialized with checks to prevent an in-flight fetch restoring history.

## Time rules

- Platform day keys, completion cutoffs, countdown, and reminder offsets use UTC.
- Settings formats reminder times in the selected zone (UTC by default).
- Today timestamps follow the device timezone and the app's 12/24-hour preference; its
  reset-time text also uses the device timezone.
- Changing Settings' display timezone does not reschedule or shift reminder instants.
  System clock/timezone broadcasts re-arm the UTC-based schedule.
- Further timezone redesign is deferred as recorded in [TODO.md](../TODO.md).
