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
- **Bottom nav** — Today / LeetCode / Settings.
