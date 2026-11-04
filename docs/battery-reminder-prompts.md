# Battery and reminder permissions

Version 0.4.6 (Android version code 14), on branch `optimizartions`, replaces the
two battery dialogs with Android's direct exemption request and one later reminder.

## First launch

After the splash, onboarding requests notification permission and then opens
exact-alarm access when needed. It waits for the exact-alarm screen to return
before completing onboarding. Android's battery-exemption dialog follows, unless
the exemption is already granted or automatic battery prompts were suppressed.
Skipping onboarding still permits the initial battery request. Accepting or
declining that request ends the flow for this visit; declining does not immediately
open another app dialog.

The first native-request attempt is saved in DataStore before launching Android.
Existing installs without that flag receive the initial native request on their
next eligible visit. Existing “Don't show again” preferences remain respected.

## Later app opens

While the standard battery exemption is missing, each new foreground visit shows
one **Allow background reminders** dialog with **Not now**, **Continue**, and an
unchecked **Don't show again** checkbox. The previous first dialog and the
second-dismissal confirmation are removed entirely.

Continue launches Android's approval dialog. Any dismissal ends this visit's
prompt. Checking Don't show again saves automatic-prompt suppression for either
action, Back, close, or outside dismissal. Preference-save failures leave an error
and allow retry. Navigation, rotation, and returning from a battery request do not
restart the prompt in the same visit. A later foreground visit checks again.

## Missing-permission warning and Settings

Today shows a non-dismissible warning while notifications or exact-alarm access
are missing. Its copy identifies the missing access; **Review permissions** opens
Settings. It refreshes on resume and after onboarding and disappears only when
both grants are present. Missing battery exemption alone does not show this bar.

Settings → Permissions contains only missing notifications, exact-alarm access,
and battery exemption. The battery **Allow** action launches the same Android
dialog and remains available after automatic-prompt suppression. Each granted
row disappears; when all three are granted the entire module disappears.

## Android limitations and distribution

The manifest declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and the app launches
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with its package URI. If a device
cannot handle the direct request, the battery-optimisation list and app details
are fallback destinations. Grants are read from Android, not inferred from a
button press or activity result code.

This exemption is distinct from Android's background restriction and proprietary
manufacturer controls. An existing battery exemption hides the battery row and
stops exemption reminders even if a separate background restriction remains;
diagnostics still record that restriction. An exemption does not guarantee
delivery or override Vivo/other manufacturer controls.

Google Play permits direct exemption requests only for qualifying core-function
needs. This branch implements the requested UX; it does not establish Play
eligibility. Before Play distribution, verify and document why Doze/App Standby
breaks core reminder checks despite supported scheduling approaches. See
[Android's exemption guidance](https://developer.android.com/training/monitoring-device-state/doze-standby#support_for_other_use_cases).

Ordinary reminders, notifications, retries, and `AlarmFeature.ENABLED = false`
are preserved. Clearing local app data resets battery prompt preferences.

## Validation

Run `testDebugUnitTest`, `assembleDebug`, and `lintDebug`. Device checks cover
first-launch permission ordering, native denial without an immediate second popup,
repeat visits, all reminder dismissal paths, suppression across process restart,
manual requests despite suppression, rotation, and grant-driven Settings visibility.

Validation on 2026-11-03: debug APK, all 51 unit tests, and lint passed (0 errors,
26 warnings, 1 hint). A first lint attempt crashed inside the analyzer; the final
run completed normally. The API 37 Pixel emulator verified notification →
exact-alarm screen → battery-dialog ordering, native denial without an immediate
extra popup, the single reminder after background/reopen and process restart,
suppression after a completed save and restart, manual battery approval despite
suppression, individual granted-row removal, an absent Permissions module when
all effective access was available, and notification/exact-alarm-specific home
warnings clearing when access became available. The session used a read-only AVD
and was closed without saving test data. Manufacturer devices and older Android
versions still need device validation; rotation and all alternative dismissal
paths remain manual checks beyond the pure state tests.
