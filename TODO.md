# TODO

Timezone changes have been reverted at the user's request. The original selector, defaults, formatting, and single-line time/timezone layout are restored. Wait for the user's revised design before implementing timezone work again. Audible playback, notification actions, and layout still need verification on a physical device or emulator.

- [x] Completely remove the LeetCode page and its navigation entry. Keep only the Today and Settings pages for now.
- [x] Implement StreakGuard's own scheduled alarm with a custom sound and Stop/Snooze controls.
  - Ring for 60 seconds with Stop and 10-minute Snooze controls. Stop dismisses the current alarm and cancels its pending repeats; Snooze rings again after 10 minutes.
  - If the user does not stop or snooze the alarm within 60 seconds, stop the sound and automatically ring again 10 minutes later. Allow at most three ringing sessions total per alarm occurrence, including the initial ring and any snoozed or unattended repeats. Stop must immediately silence the alarm and cancel all remaining repeats; after the third session, do not ring again for that occurrence.
- [ ] Automatically detect the device's timezone and schedule notification times accordingly. Recalculate future notification times immediately when the applicable timezone changes, without repeating notifications already sent. Implementation reverted; await the user's revised instructions.
- [ ] Improve timezone switching and shortcode display. The previous implementation was reverted; await the user's revised dropdown and display design. Keep the time and timezone together on the same line; do not split them into separate rows or disrupt neighboring text.
- [ ] Add a separate block in Settings for timezone settings.
- [ ] Add a separate block in Settings for alarm settings.
- [ ] Add dedicated alarm enable/disable toggle options before re-enabling ringing. Alarms are currently hard-disabled by `AlarmFeature.ENABLED = false`; keep this false until the settings controls exist and the user explicitly approves enabling alarms. Ordinary reminder notifications and API retries remain active.
- [x] Remove the current alarm implementation once the new alarm system is in place.
- [x] Handle API fetch failures at scheduled notification times with a single failure notice and bounded retries:
  - Show an appropriate message for the failure, such as "No internet connection. We'll check again in 10 minutes."
  - Send the failure notice only once per continuous outage, across notification stages and retries. Reset eligibility for a new failure notice only after a successful API fetch ends the outage.
  - For the first two stages, retry the API fetch every 10 minutes, stopping on success or 30 minutes before the next notification stage's scheduled time, whichever comes first. Do not schedule retries at or after that cutoff.
  - Skip an unsuccessful notification stage once its retry window ends. Never send a delayed earlier-stage notification during a later stage; let the next stage run at its own scheduled time.
  - For the final stage, retry the API fetch three times at 10-minute intervals after the initial failed fetch (+10, +20, and +30 minutes). If the third retry also fails, trigger an alarm when the last known question status is unsolved or unknown; do not trigger it when the last known status is solved. Apply the maximum of three ringing sessions and immediate Stop behavior above.
