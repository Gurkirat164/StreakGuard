# Device notification investigation, October 22, 2026

## Conclusion

The connected Vivo I2127 running Android 14 records actual StreakGuard force-stops
by `com.vivo.abe` (Vivo AppBehaviorEngine). The latest was **October 20 at
22:46:37.982 IST**, before all three missed reminders on October 21. StreakGuard's
next retained process startup/app opening was **October 22 at 00:02:56 IST**.
The three reminder traces contain scheduling success but no receiver entry.
Together these strongly explain yesterday's misses as interrupted background
execution following the vendor force-stop, before a LeetCode request or
notification posting could run. The retained evidence does not reveal the exact
vendor policy that selected StreakGuard for stopping.

Tasks is **Tasks.org, package `org.tasks`, version 15.12 (151202)**, not Google
Tasks. Its published 15.12 implementation also uses exact idle-capable alarms,
a receiver, and expedited WorkManager work. Its ordinary scheduled reminders use
local task data. Firebase is present, but its custom message handler requests
sync when `sync=true`; this is not evidence that the observed reminders were
ready-to-display cloud push notifications.

## Evidence collected

Read-only collection around 14:39-14:47 IST on October 22. Neither app was
launched, force-stopped, reinstalled, or reconfigured during this investigation.
No application source, app version, ringing flag, or reminder policy was changed.

- All four available StreakGuard diagnostic segments: 1,718 lines, spanning
  October 18 at 01:23:51 IST through October 22 at 14:38:26 IST.
- The three October 21 reminder ledger records.
- Android process-exit history for both packages, package and app-operation
  state, alarm/job scheduling, notification channels, Doze exemptions, targeted
  usage/broadcast records, and retained logcat entries for the apps/vendor.
- Published Tasks.org source at the `15.12` tag, matching the installed version.

Raw local evidence and downloaded reference source are under
`build/notification-investigation-2026-10-22/` (Git-ignored). Device dumps can
include information about other apps; they were not added to source control or
uploaded. Tasks is not debuggable, so Android denied access to its private logs.
Vendor RMS dumps supplied no usable per-app policy evidence. The current logcat
buffer only retained recent entries, not the October 20 stopping event.

## Timeline (all times IST)

| Time | Evidence |
| --- | --- |
| Oct 18, 18:30:03 | StreakGuard logs a successful tier-0 notification posting. |
| Oct 18, 18:46:22 | Android records FORCE STOP, description identifies `com.vivo.abe`. |
| Oct 19, 17:32:01 | Another FORCE STOP identifies `com.vivo.abe`. |
| Oct 20, 22:30:03 | Tier-2 receiver enters and reminder worker starts. |
| Oct 20, 22:30:07 | Tier-2 posting succeeds. Future Oct 21 reminders have been registered. |
| Oct 20, 22:37:50 | Last completed background status check before the gap; API requests returned HTTP 200. |
| Oct 20, 22:46:37 | Android records FORCE STOP: `stop com.streakguard.app due to stop by com.vivo.abe`. |
| Oct 21, 18:30 / 20:30 / 22:30 | Scheduled reminder times; no corresponding receiver/worker/posting evidence. |
| Oct 22, 00:02:56 | App opens; recovery audit marks all three reminders overdue with no receiver evidence; app replaces their schedules. |
| Oct 22, 00:03:13 | Android records REMOVE TASK, calling PID 3585. Separate service evidence identifies that PID as the launcher. This differs from the earlier FORCE STOP events. |
| Oct 22, 13:49:30 | Android records ordinary LOW_MEMORY process death. Background status work subsequently resumes. |

The actual force-stop preceding yesterday's failure occurred on October 20,
not October 21. There is no retained crash or ANR record explaining these misses.
The latest 16 exit records are bounded history, not a guarantee that every past
event remains available. A present-day `stopped=false` snapshot cannot show the
package's state before it was reopened.

## Comparison with Tasks

| Area | StreakGuard | Tasks.org 15.12 |
| --- | --- | --- |
| Notification permission now | Granted | Granted |
| Exact-alarm app operation now | Allowed; diagnostic `exact_permission=true` | Allowed; exact alarm registered |
| Android Doze user exemption now | Absent | Absent |
| Scheduler | `setExactAndAllowWhileIdle`, expedited WorkManager with ordinary-work quota fallback | Same alarm API and expedited-work fallback |
| Reminder content dependency | Fresh LeetCode check before challenge notification | Saved task/reminder data |
| Retained force-stop evidence | Three exits explicitly identify `com.vivo.abe` | None in its five retained exits |
| Other exits | Recent-task removal, low memory, empty-process cleanup | Recent-task removal, low memory, empty-process cleanup |
| Channels | Early tiers can be silent; urgent tier has high importance and sound | Default reminders have high importance and sound |

The package dump's install-permission line says `SCHEDULE_EXACT_ALARM:
granted=false` for both apps. That line alone is misleading here: both have the
special app operation allowed, and actual exact alarms are present. StreakGuard
also recorded successful exact scheduling before the misses.

Tasks' alarm snapshot records four wakeups for `NotificationReceiver`; a future
exact alarm is also registered. This supports local scheduled execution. It does
not prove every configured task reminder was posted, seen, or sounded. There is
no accessible complete task schedule plus posting history to independently
confirm the user's report that all Tasks notifications arrived.

Tasks can lose its process and still be started again for reminders. Process
death is different from the explicit package force-stop recorded for StreakGuard.
There is no evidence here that adding Firebase, changing WorkManager, requesting
exact-alarm access again, or making a channel louder would resolve the observed
vendor stopping. Neither app being Doze-exempt also means that exemption alone
does not explain their different outcomes.

## Source verification and next steps

Published Tasks 15.12 source inspected:

- [WorkManagerImpl](https://github.com/tasks/tasks/blob/15.12/app/src/main/java/org/tasks/jobs/WorkManagerImpl.kt): schedules exact idle-capable alarms; requests expedited NotificationWork without network constraints.
- [NotificationReceiver](https://github.com/tasks/tasks/blob/15.12/app/src/main/java/org/tasks/jobs/NotificationReceiver.kt): requests expedited notification work.
- [NotificationWork](https://github.com/tasks/tasks/blob/15.12/app/src/main/java/org/tasks/jobs/NotificationWork.kt): triggers saved alarms/notifications and schedules the next occurrence.
- [Notifier](https://github.com/tasks/tasks/blob/15.12/app/src/main/java/org/tasks/Notifier.kt): reads tasks through TaskDao and posts task notifications.
- [TasksFirebaseMessagingService](https://github.com/tasks/tasks/blob/15.12/app/src/googleplay/java/org/tasks/fcm/TasksFirebaseMessagingService.kt): requests sync for `sync=true` messages.

The relevant delivery difference is local task data versus StreakGuard's live
status check, not the mere presence of WorkManager. Removing the live network
dependency could improve delivery after a receiver starts, with an explicit
tradeoff when completion on another device is not yet known. It would not itself
resolve this force-stop case.

To establish why Vivo selects one app and not the other, compare their Vivo
Autostart, background power, and recent-app locking settings on this phone and
capture vendor policy evidence at the next incident before reopening StreakGuard.
The collected Android permission snapshots do not expose those separate vendor
controls. No supported bypass of force-stop has been demonstrated. Any proposed
behavioral fix needs an actual screen-off/overnight delivery test on this device.

Alarm ringing remains disabled with `AlarmFeature.ENABLED = false`; ordinary
notifications and retries remain unchanged.
