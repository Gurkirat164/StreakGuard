# Battery reminder reliability

The approved Settings v3 battery card (variant 2) is always available under
**Settings → Permissions → Battery optimisation**. It shows a battery icon,
RESTRICTED/UNRESTRICTED state, a compact Settings action, and the warning
“Battery restrictions may delay or stop reminders.” Automatic-prompt suppression
does not hide this card.

After the splash and onboarding, each foreground visit checks Android's battery
optimisation allowlist and, on Android 9+, the app's background restriction.
Either restriction shows “Keep reminders reliable”. Not now, Back, close, or an
outside tap opens “Leave battery settings unchanged?”. Dismissing that second
dialog closes prompts for the visit; the next visit checks again.

The second dialog has an unchecked **Don’t show again** checkbox above the two
buttons. Selecting it and choosing either action, or dismissing the dialog,
saves permanent automatic-prompt suppression in DataStore. Failed preference
writes keep the dialog open with an error. Clearing local app data also resets
this preference, consistently with the other user settings.

Open settings uses Android's battery-optimisation list, or the app details page
when background restrictions are detected, with fallbacks for vendor devices.
Returning rechecks both states and updates the Settings card. If restrictions
remain, the originating prompt is retained; if both are clear, prompts stop.
A manual Settings round trip does not reopen dismissed prompts. Rotation and
navigation within a visit do not restart prompts.

No direct battery-exemption permission is requested. Android/OEM settings vary;
the user makes the change in Android settings. The app cannot detect every
manufacturer's proprietary background controls or guarantee reminder delivery.
Ordinary reminder scheduling, notifications, retries, and
`AlarmFeature.ENABLED = false` are unchanged. This feature ships in the approved
version 0.4.5 (Android version code 13).

Validation: run `testDebugUnitTest`, `assembleDebug`, and `lintDebug`. Device checks
should cover both dialog dismissals, suppression across a process restart,
rotation, return from Android settings with and without changes, and the card
remaining available when all other permissions are granted.
