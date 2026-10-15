# Versioning

App versions use `major.minor.patch` in `app/build.gradle.kts`.

- Small changes and fixes increment the last number (patch), for example `0.4.2` → `0.4.3`.
- Larger changes increment the middle number (minor) and reset the patch number, for example `0.4.3` → `0.5.0`.
- Before any future version bump, tell the user the exact proposed version and ask: "I'm version bumping to X.Y.Z. Should I?" Wait for confirmation before editing the version, unless the user has already explicitly approved that exact version.
- Increment Android's `versionCode` whenever an approved `versionName` bump is applied.

The user requested a patch bump with the timezone rollback: `0.4.2` → `0.4.3`, with `versionCode` changing from `10` to `11`.
