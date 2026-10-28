# Project instructions

Read and follow [docs/versioning.md](docs/versioning.md) before changing the app version. Propose the exact new version and ask the user for confirmation before applying any future bump, unless that exact version has already been explicitly approved.

Alarm ringing must remain disabled with `AlarmFeature.ENABLED = false` until dedicated alarm toggle options have been added to Settings and the user explicitly approves re-enabling it. Preserve ordinary reminder notifications and retries.
