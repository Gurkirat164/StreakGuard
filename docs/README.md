# StreakGuard docs

Documentation for the current repository build: **0.4.7** (`versionCode` **15**).
Current behavior is distinguished from the implemented but disabled ringing system.
Pending work and verification are tracked in [TODO.md](../TODO.md).

- [Overview](overview.md) — what StreakGuard does today
- [Features](features.md) — reminders, retries, sync, screens, and inactive ringing
- [Architecture](architecture.md) — packages, platform support, workers, and state
- [Design system](design-system.md) — shared theme and Settings-specific styling
- [Battery reminder prompts](battery-reminder-prompts.md) — native battery requests, launch reminders, permission warnings, and Settings visibility
- [Data & storage](data-storage.md) — Room schema, preferences, durable state, and time rules
- [Building](building.md) — configured toolchain, build/check commands, and release limitations
- [Debug diagnostics](diagnostics.md) — local logs, retention, sharing, access policies, and future delivery extensions
- [Investigating missing reminders](notification-diagnostics.md) — delivery audit, worker evidence, Android restrictions, and report collection
- [Versioning](versioning.md) — patch/minor bumps and required user confirmation
- [Privacy policy](../Policies/privacy-policy.html) — local data and platform requests

Alarm ringing remains disabled with `AlarmFeature.ENABLED = false`. Enabling it
requires dedicated Settings controls and explicit user approval. Ordinary reminder
notifications and API retries remain active.
