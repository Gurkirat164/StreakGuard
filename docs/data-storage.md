# Data & storage

Everything is stored on-device. There is no server and no account.

## Room database — `streakguard.db`

Table `check_log`, one row per platform check (database version 2):

| Column          | Type    | Meaning                                              |
|-----------------|---------|------------------------------------------------------|
| id              | INTEGER | Auto-generated primary key                           |
| platformId      | TEXT    | e.g. `leetcode`                                      |
| date            | TEXT    | UTC platform day `yyyy-MM-dd` — the cache key        |
| completed       | INTEGER | 1 when the challenge was confirmed done              |
| known           | INTEGER | 0 when the check couldn't determine a status         |
| checkedAtEpoch  | INTEGER | Wall-clock time of the check (millis)                |
| challengeTitle  | TEXT    | Title of the challenge that was checked              |
| challengeUrl    | TEXT    | Link to the challenge                                |
| streak          | INTEGER | Streak value reported by the platform, if any        |

`CheckLogDao.getForDate(platformId, date)` returns the latest row for a
platform day. The UI loads this on launch — that's why closing and reopening
the app keeps the last result instead of resetting.

## Settings — DataStore Preferences (`settings`)

- `username_<platformId>` — public handle per platform
- `enabled_<platformId>` — per-platform toggle (default true)
- `check_hour` / `check_minute` — daily check time (default 21:00)
- `confirm_when_done` — optional "done" notification (default false)

## Time rules

- **Backend:** UTC everywhere. The platform day key (`date`), the completion
  rule ("accepted submission at/after start of UTC day"), and the countdown
  target (next UTC midnight) all use UTC. Each platform converts to UTC
  inside its own implementation.
- **Display:** device-local everywhere. "Last checked" times and the
  "solved after …" inspection line are formatted with
  `TimeUtils.formatLocalTime()` / `TimeUtils.utcMidnightInLocalTime()`
  (e.g. `05:30 IST`).
- The daily alarm time the user picks in Settings is a local wall-clock time
  and is scheduled with the device timezone.
