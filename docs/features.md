# Features (current build)

## Daily streak check

- Runs once daily at the configured time via an exact alarm
  (`AlarmScheduler`, re-armed after each run and after reboot).
- For each enabled platform with a username set: fetches today's challenge,
  checks completion, writes the result to the local database.
- Sends a notification when the challenge is **not** done. Silent when it is
  done, unless "Confirm when done" is enabled in Settings.
- A platform that can't be reached (network error, bad username) is recorded
  as **unknown** and never triggers a notification.

## Manual check

- **CHECK NOW** button on the Today tab and **REFRESH STATUS** button on the
  LeetCode tab run the same check immediately and update both tabs.

## Cached status

- The result of the latest check is persisted per platform per UTC day.
- Opening the app shows the cached result instantly — no network call on
  launch. A fresh check happens only via the manual button or the scheduled
  daily alarm (i.e. effectively once per new question).

## Screens

- **Today** — status banner per platform (SOLVED / PENDING / UNKNOWN pill,
  inspection line, SOLVE NOW button opening the challenge in a browser, live
  countdown to the next UTC midnight), manual CHECK NOW, last-checked time.
- **LeetCode** — platform detail: username, today's challenge title (tappable,
  opens in browser), streak, status pill, SOLVE NOW, REFRESH STATUS.
- **Settings** — per-platform username + enable toggle, daily check time
  picker, "confirm when done" toggle, notification/permission helpers, test
  notification button.

## Time display

- All times in the UI are device-local (e.g. "Last checked 20:15",
  "solved after 05:30 IST"). The backend day boundary stays UTC.
