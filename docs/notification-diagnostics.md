# Investigating missing reminders

Debug reports now include a persistent delivery audit, Android process-exit evidence,
background restrictions, idle state, boot count, uptime, notification channel sound
settings, and retained WorkManager states. Collection remains local and uses the
existing Diagnostics access policy. Sharing still uses the existing confirmation.

Each daily reminder and retry gets a random `trace_id`, carried through its alarm,
receiver, worker, and posting call. It contains no username or settings signature.
Critical checkpoints are flushed to disk synchronously; the ledger uses a synced
temporary file and atomic replacement where supported. Ordinary events still use
the bounded asynchronous queue. Diagnostic failures never prevent reminder work.

The ledger retains up to 512 occurrences for seven days. Every cancellation and
replacement keeps the previous occurrence, including when reopening replaces an
overdue reminder with tomorrow's schedule. Clearing diagnostics or disabling the
feature purges this ledger too. Older installations have no retroactive ledger.

## Reading the audit

| Finding | Meaning |
| --- | --- |
| `scheduled_no_receiver_evidence_cause_unknown` | Scheduling returned successfully, but no receiver entry was persisted after the 60-second grace period. The OS cause is unknown. |
| `overdue_no_receiver_before_app_cancel` | The app subsequently cancelled/replaced that overdue occurrence, with no receiver entry recorded. Check the cancellation reason and Android evidence. |
| `work_requested_no_worker_start` | Receiver requested work, but worker start is absent. Match `work_id` with the WorkManager snapshot and enqueue checkpoints. |
| `worker_started_no_terminal_evidence` | Worker started without a recorded terminal outcome. Check process exits and retained work state/stop reason. |
| `notification_blocked` / `notification_post_failed` | Posting was blocked by permission/channel settings or threw an exception. |
| `posting_accepted_display_unconfirmed` | `notify()` returned. The active-notification snapshot is additional registration evidence; neither proves the user saw or heard it. |

Explicit skip reasons distinguish completion, changed settings, an already-delivered
occurrence, an exhausted attempt, and a missed delivery window. Connection-error
notices are distinguished from actual challenge reminders. Enqueue completion means
the WorkManager operation completed; with KEEP, another existing request may have
been retained. A timed-out enqueue remains unconfirmed, not rejected. Completed
work can be pruned, and the export includes at most 100 retained work records.

Android 11+ supplies up to 16 historical process exits. Both its reason code and a
label are included, without raw descriptions, stack dumps, or device identifiers.
`user_requested` is not proof of force-stop: Android also uses it for other user
actions, and older versions use it for some package changes. Unsupported APIs and
failed evidence collection are reported explicitly. A restriction snapshot after
reopening describes that moment, not necessarily the moment a reminder was missed.

No in-app logger can run while Android prevents the app from executing. Missing
receiver evidence must not be automatically attributed to battery optimization.
Device-level Android evidence may still be needed to resolve an unknown OS cause.

## Reproduce and collect

Open the updated app once to register traced reminders. Leave the challenge
unsolved, allow the scheduled times to pass with the screen off, and export the
report promptly after a miss. Do not clear logs or reinstall before exporting.
Compare expected reminder times, cancellation reasons, exit timestamps, boot
counts, and work IDs. A manual test notification only tests immediate posting;
it does not validate scheduled background execution.

The app version and notification/retry policy are unchanged. Alarm ringing remains
disabled by `AlarmFeature.ENABLED = false`.
