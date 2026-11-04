# Vivo notification reliability

Research date: 2026-10-20. Repository reviewed: 0.4.4, versionCode 12,
including the existing uncommitted notification diagnostics work.

**Finding:** public distribution should prioritize delivery that needs minimal
background execution, with Vivo settings guidance reserved for troubleshooting.
Allowing StreakGuard through Vivo's background-power and Autostart controls is a
documented mitigation, but unsuitable as a mandatory first-run checklist.
An app cannot promise to restart itself from Android's actual stopped state.
This investigation does not establish a hardware-verified fix.

The user reports a general Vivo problem without specific models or firmware.
Other manufacturers remain untested, not confirmed unaffected. No physical-device
delivery test was completed in this investigation.

## Distribution strategy: reduce the work required to deliver a reminder

The user explicitly wants a normal consumer experience without a lengthy battery
settings checklist. The settings below are a fallback, not the primary product
solution.

Android's alarm service can start an application's receiver even when its process
is not running. A local reminder can therefore use a short execution window to
read saved state and post a notification, without a continuously running service.
This assumes alarm delivery is allowed; it does not bypass a stopped package.
[Android AlarmManager](https://developer.android.com/reference/android/app/AlarmManager).

Cloud delivery provides another route. Google Play services can display eligible
FCM notification messages on behalf of an app without starting its process.
Messages should contain the display content so the phone does not need a further
network request before showing the alert.
[FCM delivery guidance](https://firebase.google.com/docs/cloud-messaging/android-message-priority).
FCM still requires reopening after an Android Settings force-quit; adding it is
not a guaranteed escape from force-stop.
[FCM receive prerequisites](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages).

These are supported mechanisms, not claims about the private implementations of
specific competitors. Established reminder apps also document failures:
[TickTick's FAQ](https://help.ticktick.com/articles/7055792921664028672) asks affected
Android users to allow Autostart/background operation, and
[Todoist troubleshooting](https://www.todoist.com/help/todoist/troubleshooting/troubleshoot-notifications-not-received-from-todoist-OKO3ezsK)
includes battery optimization and app cleaners.

StreakGuard currently requires:

`scheduled alarm -> receiver -> WorkManager -> live LeetCode check -> challenge notification`

The proposed first app-side improvement is:

`scheduled alarm -> validate saved day/account/completion state -> local notification`

Status refresh and existing network retries would run separately. Suppress the
reminder when today's completion is already known. When current completion is
unknown, use neutral deadline wording rather than asserting that the challenge
is unfinished. This changes product behavior and is not implemented here.
Someone who solved on another device could receive a reminder until that
completion is learned; this is the explicit tradeoff for offline delivery.
It improves failures after receiver entry, not cases where Vivo blocks the
receiver itself.

If freshly verified completion is required before every notification, evaluate
server-side checks and ready-to-display push notifications. That requires a
backend, scheduling/token management, failure handling, and moving the necessary
handle and reminder settings off-device. A local fallback needs a deliberate
duplicate/stale-message policy; it must not be added as a second uncontrolled
sender. Validate both routes on real Vivo firmware before promising reliability.

Keep initial onboarding limited to the permissions needed for the chosen delivery
mode. Offer a background delivery test and targeted Vivo help when there is
evidence of a problem, without treating an immediate test ping as proof of
overnight reliability.

## Device setup for troubleshooting

Menu names vary between models, regions, Funtouch OS, and OriginOS. Search the
phone's Settings for the named option if the example route differs.

1. **Allow background power use for StreakGuard.** Look under Settings > Battery >
   Background power consumption management > StreakGuard. Select the option
   allowing background activity, such as High background power consumption or
   Don't restrict background power usage. Some builds expose this under Apps >
   Special app access. Vivo documents its high-background-power control;
   AirDroid's own Vivo setup guide documents both routes.
   [Vivo guidance](https://bbs.vivo.com/in/thread/11952),
   [AirDroid device setup](https://airdroid-business.freshdesk.com/en/support/solutions/articles/154000199990--connection-vivo-devices-how-to-keep-biz-daemon-running-in-the-background-).
2. **Enable Autostart for StreakGuard.** A documented route is Settings > Apps >
   Special app access > Autostart. The vendor's manual places Autostart among
   special app permissions. This is a separate control from notification access.
   [Vivo manual, page 81](https://asia-exstatic-vivofs.vivo.com/PSee2l50xoirPK7y/1727424675246/a2d6a31198407f8dcca15154c49be848.pdf#page=81).
3. **Check app battery optimization separately, if available.** Set StreakGuard's
   battery use to Unrestricted or Don't optimize where that is an additional
   setting. A successful Android Doze exemption check is not evidence that Vivo
   Autostart and cleanup protection are also configured. SingularityApp documents
   these as separate steps for its own Vivo reminder delivery.
   [SingularityApp Vivo instructions](https://singularity-app.com/wiki/vivo-notifications/).
4. **For cleanup-related failures, lock StreakGuard in Recent Apps.** Open the
   recent-apps view, open StreakGuard's card menu, and select Lock where present.
   Treat this as cleanup protection, not a guarantee against force-stop or every
   process termination. AirDroid includes this step in its device instructions.
   [AirDroid device setup](https://airdroid-business.freshdesk.com/en/support/solutions/articles/154000199990--connection-vivo-devices-how-to-keep-biz-daemon-running-in-the-background-).
5. **If delivery still fails overnight, test with Sleep standby optimization off,
   where available.** Find it through Settings search. Vivo says this feature can
   freeze idle apps during sleeping hours and limit network switching. Disabling
   it for comparison is our troubleshooting recommendation, not a universal
   vendor guarantee; it may increase standby battery use.
   [Vivo's feature description](https://www.vivo.com/in/products/y300-5g).
6. **Confirm the ordinary notification prerequisites.** Enable StreakGuard's
   notifications and relevant reminder channels, and grant Alarms & reminders
   access for precise scheduling. That scheduling permission does not enable the
   app's disabled ringing feature.
   [Android exact-alarm access](https://developer.android.com/develop/background-work/services/alarms).
7. **Open StreakGuard once after configuring the phone.** The existing app-open
   path registers reminders again. Then leave it in the background for the
   scheduled test below.

Apply these controls to StreakGuard. Additional permissions listed by remote
management apps, such as overlays or background pop-ups, are not established
requirements for this app's ordinary notifications.

## What code can and cannot fix

Android distinguishes ordinary process death from a package being force-stopped.
The stopped state persists until user interaction removes it. Android 15 also
cancels pending intents on entry to this state and offers a boot-completed
broadcast when user interaction takes the app out of it. More retries, a
persistent service, or another scheduler cannot provide a supported autonomous
escape from that state.
[Android stopped-state behavior](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state).

Therefore, “Vivo force-stopped the app” remains a hypothesis until device evidence
confirms it. Missing notifications can also result from blocked notification
channels, delayed jobs, failed network checks, or intentionally skipped reminders.

Current implementation findings:

| Area | What the current checkout does | Consequence |
| --- | --- | --- |
| `AlarmScheduler.kt` | Uses `setExactAndAllowWhileIdle`, falling back to an inexact idle-capable alarm when permission is denied. | An idle-capable alarm is already present; merely adding one is not a new fix. |
| `DailyCheckReceiver.kt` | Enqueues expedited `ReminderCheckWorker`, with ordinary-work fallback. | Alarm delivery and notification delivery are separate events. |
| `SyncScheduler.kt` | Checks live challenge status before an actual missed-challenge notification; applies completion, deduplication, deadline, and retry rules. | A received alarm does not prove a reminder can be posted on time. |
| `MainActivity.kt`, `BootReceiver.kt` | Re-arm on activity creation, reboot, package replacement, and time changes; Settings also re-arms on resume. | Recovery already exists, but cannot execute while the package is stopped. |
| `SettingsPermissions.kt`, `SettingsScreen.kt` | Guide notification and exact-alarm access; provide an immediate test notification. | Vivo background setup and a delayed background test are missing. |
| Manifest and `BootReceiver.kt` | Do not handle the exact-alarm permission-granted broadcast. | Granting permission outside the app is a separate recovery gap. |

Android documents that expedited work can be delayed by load or quota. If a trace
shows receiver entry but no worker start, investigate that handoff instead of
attributing the miss to force-stop.
[WorkManager execution limits](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#deferred-expedited).

## Recovery and troubleshooting additions

These are proposed changes, not implemented by this research.

1. **Add a Vivo background setup guide in Settings.** Show the steps above on
   Vivo devices and keep general notification troubleshooting accessible on other
   phones. Offer system Settings shortcuts with safe fallbacks and manual
   directions. Do not rely on undocumented Vivo activity names as a universal
   route. Do not mark Autostart as granted merely because a Settings page opened
   or the user checked a box; no public, cross-version verification API was
   established in this research.
2. **Show accurately scoped checks.** Notification access, channel state, exact
   scheduling access, Android background restriction, and Doze exemption should
   remain distinct from manually reviewed Vivo controls. Recheck supported
   statuses when returning from Settings. Android provides a general battery
   optimization Settings intent; a direct exemption request has narrower
   eligibility and is not necessary just to provide guidance.
   [Android battery optimization guidance](https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases).
3. **Handle exact-alarm access being granted.** Listen for
   `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, recheck
   `canScheduleExactAlarms()`, and reschedule eligible reminders using the
   existing scheduler. Keep the fallback when access is absent. This addresses
   permission recovery, not Vivo's stopped state.
   [Android permission-change guidance](https://developer.android.com/develop/background-work/services/alarms).
4. **Add a delayed background delivery test.** Clearly distinguish immediate
   notification display, an alarm-triggered background probe, and the real
   alarm-to-worker-to-network reminder path. Use separate test identities so a
   probe cannot mark a daily challenge reminder delivered or change retry state.
   An immediate ping alone is not a background reliability test.
5. **Use the existing delivery audit to select further changes.** On Android 15+
   consider adding `ApplicationStartInfo.wasForceStopped()` to recovery evidence.
   It identifies the first process launch after a force-stop, but does not
   identify Vivo as the actor. Older exit reasons remain inconclusive.
   [Android start information](https://developer.android.com/reference/android/app/ApplicationStartInfo#wasForceStopped()).

If the receiver runs but the worker is repeatedly delayed, review how the app
handles deadline-sensitive notifications. A local status-unknown notice followed
by asynchronous verification is a possible product change, but it must preserve
known-completed suppression and make uncertainty explicit. Do not silently replace
the current confirmed-status notification and retry policy based on an unproven
diagnosis.

## How to establish that the mitigation works

First record model, Android version, Funtouch/OriginOS version, app version,
permission state, background settings, and expected reminder times. Use an
unsolved challenge and enable the existing local diagnostics where available.
Collect a baseline miss before changing settings if practical.

Use real scheduled reminders with sufficient time remaining in their delivery
windows. Disconnect charging/USB for natural idle tests. Observe delivery before
reopening the app, because reopening changes both scheduling and device state.

| Scenario | Required observation | Status |
| --- | --- | --- |
| Screen off, app backgrounded | Scheduled reminder arrives within its valid window without opening StreakGuard. | Not run |
| Clear recent apps, with the app's card locked | Document actual cleanup behavior and check scheduled delivery. | Not run |
| Overnight idle, repeated for several nights | Each eligible reminder arrives; compare before/after settings. | Not run |
| Reboot, then unlock without opening StreakGuard | Future reminders are restored and delivered. | Not run |
| Temporary network failure | Existing failure notices/retries work without duplicate or stale reminders. | Not run |
| Exact-alarm permission revoked and restored | Record fallback behavior and verify re-registration after grant. | Not run |
| Explicit Force stop | Expect interruption; reopen and confirm future scheduling recovers. This tests the platform boundary. | Not run |
| Challenge already completed | No missed-challenge reminder or ringing. | Not run |

For a miss, follow [the delivery-audit guide](notification-diagnostics.md).
If a development device is available, capture package state *before reopening*
with `adb shell dumpsys package com.streakguard.app`; inspect the correct Android
user's `stopped` flag. Also capture relevant alarm and job state. Use
`com.streakguard.app.qa` for the isolated optimized test variant.

No receiver checkpoint means delivery is unobserved, not that the cause is known.
Receiver entry without worker start points to scheduling after the broadcast.
Worker entry with a network failure points to connectivity or the API. Successful
posting is still not proof that the user saw or heard the notification.

A passing result supports only the tested model, firmware, settings, and build.
Repeat on additional Vivo models and at least one other manufacturer before
making broader compatibility claims.

This research does not change the app version, reminder behavior, or existing
diagnostics edits. Alarm ringing remains disabled with `AlarmFeature.ENABLED = false`.
