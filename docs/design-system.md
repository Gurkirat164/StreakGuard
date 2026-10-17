# Design system

Source: the StreakGuard Developer Suite palette. Always-dark theme, no
dynamic color.

## Colors

| Token       | Hex       | Use                                  |
|-------------|-----------|--------------------------------------|
| Primary     | `#FF5722` | Buttons, accents, brand mark         |
| OnPrimary   | `#2A1200` | Text/icons on primary surfaces       |
| Secondary   | `#10B981` | Success, SOLVED pill                 |
| Tertiary    | `#F59E0B` | Warning, PENDING pill, highlights    |
| AppBackground      | `#0B111E` | App background, status bar    |
| CardBackground     | `#131A29` | Cards                         |
| CardInnerBackground| `#1B2334` | Inset strips, icon tiles      |
| PillBackground     | `#263049` | Pills, disabled states        |
| TextPrimary | `#E9EDF7` | Headings, primary text               |
| TextMuted   | `#8B93A7` | Secondary text, captions             |
| DangerLight | `#FFB5A0` | Errors                               |

In code these live in `ui/theme/Color.kt` and are wired into a Material 3
`darkColorScheme` in `ui/theme/Theme.kt`. The launcher icon background is the
same neutral (`#0B111E`, via `ic_launcher_background`), and the cold-start
window background + status bar use it too, so there is no white flash.

## Fonts

| Role     | Typeface      | File(s)                                     |
|----------|---------------|---------------------------------------------|
| Headline | Geist         | `res/font/geist.xml` (400/500)              |
| Body     | Geist         | `res/font/geist.xml` (400/500)              |
| Label    | JetBrains Mono| `res/font/jetbrains_mono.xml` (400/500)     |

`Theme.kt` defines `GeistFontFamily` and `JetBrainsMonoFontFamily` and a
`Typography` that applies Geist to display/headline/title/body styles and
JetBrains Mono to all label styles. Mono is also used directly for pills,
captions, buttons and timers via `JetBrainsMonoFontFamily`.

## Components

- **AppHeader** — flame mark in a rounded tile, "StreakGuard" wordmark (Geist
  bold), version pill (JetBrains Mono, tertiary).
- **Status banner** — platform card: icon tile, name + status pill
  (SOLVED = secondary, PENDING = tertiary, UNKNOWN = muted), mono inspection
  line, SOLVE NOW button (primary), "day closes in" live countdown strip.
- **Bottom nav** — Today / Settings. The former LeetCode detail tab has been removed.

## Settings-specific styling

Settings uses its own scoped palette in `ui/settings/SettingsScreen.kt`, including
canvas `#0D1320`, cards `#121826`, inset surfaces `#192033`, and text `#F1F5F9`.
It does not use the shared palette for every surface.

| Role | Typeface / resource |
|------|---------------------|
| Settings cards/actions | Geist regular, medium, and `settings_geist_bold.ttf` |
| Countdown badges | Liberation Mono regular/bold |
| Reminder times and popup labels | JetBrains Mono, with bold clock values |
| Countdown descriptions | Nimbus Sans |
| Permission descriptions | Roboto Mono |

Settings modules include LeetCode Connection, Streak Reminders, Sync Interval,
a conditional Permissions block, and Others. The permission block lists only
missing notification/exact-alarm access. The reminder timezone selector remains
inside Streak Reminders, and reminder times keep time and timezone on one line.
Alarm Settings has a bold outlined clock icon, OFF badge, disabled ringing toggle, and availability note. A compact 12 hr / 24 hr segmented control is first under Others, with the same 40 dp icon tile and text alignment as adjacent action rows. Reminder clock values are bold in their tier colors; timezone suffixes stay regular in `#94A3B8`.

The custom notification-time popup shares the Streak Reminders icon, 16 dp card corners, dark inset rows, and monospaced bold time values. Cancel and Save have equal widths and 36 dp visible heights. It opens native time pickers. The shared screen background extends through the version/privacy footer. A separate timezone block remains pending; see [TODO.md](../TODO.md).

Bundled font/icon licences and asset-source notes live in `res/raw/`.
MainActivity configures edge-to-edge with AndroidX and light system icons.
The root Scaffold owns safe-drawing padding and consumes it before screen content;
the navigation bar owns its bottom insets. Splash content uses safe-drawing padding.
Remaining OS/navigation-mode verification is tracked in TODO 8.3.
