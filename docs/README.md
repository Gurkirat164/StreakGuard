# StreakGuard docs

Documentation for the current repository build: **0.4.5** (`versionCode` **13**).
Current behavior is distinguished from the implemented but disabled ringing system.
Pending work and verification are tracked in [TODO.md](../TODO.md).

- [Overview](overview.md) — what StreakGuard does today
- [Features](features.md) — reminders, retries, sync, screens, and inactive ringing
- [Architecture](architecture.md) — packages, platform support, workers, and state
- [Design system](design-system.md) — shared theme and Settings-specific styling
- [Battery reminder prompts](battery-reminder-prompts.md) — Settings v3 battery card, launch prompts, suppression, and Android settings checks
- [Data & storage](data-storage.md) — Room schema, preferences, durable state, and time rules
- [Building](building.md) — configured toolchain, build/check commands, and release limitations
- [Debug diagnostics](diagnostics.md) — local logs, retention, sharing, access policies, and future delivery extensions
- [Versioning](versioning.md) — patch/minor bumps and required user confirmation
- [Privacy policy](../Policies/privacy-policy.html) — local data and platform requests

Alarm ringing remains disabled with `AlarmFeature.ENABLED = false`. Enabling it
requires dedicated Settings controls and explicit user approval. Ordinary reminder
notifications and API retries remain active.
