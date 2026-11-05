# Project instructions

Read and follow [docs/versioning.md](docs/versioning.md) before changing the app version. Propose the exact new version and ask the user for confirmation before applying any future bump, unless that exact version has already been explicitly approved.

Alarm ringing must remain disabled with `AlarmFeature.ENABLED = false` until dedicated alarm toggle options have been added to Settings and the user explicitly approves re-enabling it. Preserve ordinary reminder notifications and retries.

## Code and repository hygiene

- Fix the underlying problem; do not use patchwork fixes.
- Do not create unnecessary files, research dumps, images, or other artifacts that are not required for the app or the requested work.
- When code or a feature is requested to be removed, delete it completely, including unused functions, imports, resources, and references. Do not comment it out or leave dead code behind.

## Commit messages

Start every commit message with the app version prefixed by `v`, followed by `:`, a space, the change type (such as `feat`, `fix`, `addition`, `docs`, or `chore`), and a concise description. After a blank line, include bullet points describing the changes and relevant details.

Use the current app version unless a new version has been explicitly approved. This format does not authorize a version bump.

Example:

```text
v0.4.5: fix Commit message format

- Require the app version at the start of commit messages.
- Include the change type and a concise description.
- Describe the changes in bullet points.
```
