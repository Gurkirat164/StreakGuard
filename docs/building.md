# Building

## Configured toolchain

| Component | Repository configuration |
|-----------|--------------------------|
| Android Gradle Plugin | 9.4.1 |
| Gradle wrapper | 9.7.1 |
| Kotlin Compose plugin | 2.4.20 |
| KSP | 2.3.12 |
| Java/JVM target | 17 |
| Android compile / target SDK | 37 / 37 |
| Minimum Android SDK | 26 (Android 8.0) |
| App version | 0.4.4 (`versionCode` 12) |

Use JDK 17 and an Android Studio version compatible with the configured Android
Gradle Plugin. Install Android SDK platform 37 and let Gradle resolve dependencies.
These are repository settings, not a claim that a build has been verified on every host.

## Android Studio

1. Open the repository folder containing `settings.gradle.kts`.
2. Let Gradle sync; first-time dependency downloads require internet access.
3. Build the debug APK using **Build > Build Bundle(s) / APK(s) > Build APK(s)**
   (menu wording may vary by Studio version).
4. Find it at `app/build/outputs/apk/debug/app-debug.apk`.
5. Install on a device/emulator, launch once, configure the username, and grant
   notification/exact-alarm access. Use Settings' test notification to check delivery.
6. For a signed release use **Build > Generate Signed Bundle / APK** and supply
   your signing configuration through Android Studio.

## Command line

From the repository root with Java and the Android SDK configured:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

On macOS/Linux use `./gradlew` instead. An unsigned release bundle can be built
with `bundleRelease`; a distributable release must be signed.

## Assets and release notes

- Fonts and the bundled alarm sound are tracked under `app/src/main/res/`.
  A normal checkout should include them; no manual font-copy step is required.
  Font and icon notices are included in `res/raw/`.
- Release builds enable R8 optimization/obfuscation and resource shrinking using
  `proguard-android-optimize.txt` and the narrow project rules in `app/proguard-rules.pro`.
  Worker names remain stable for work persisted before an app upgrade; Room and
  WorkManager consumer rules protect reflective entry points.
- Keep the exact release's `app/build/outputs/mapping/release/mapping.txt` with
  its APK/AAB. `assembleRelease` and `bundleRelease` also archive mapping and
  shrinker reports under `app/build/outputs/release-symbols/`, named by version/code.
  App Bundles include the R8 mapping for Play crash deobfuscation. Do not substitute
  a later build's mapping, even if its version number is the same.
- Edge-to-edge uses AndroidX `enableEdgeToEdge`, light system icons, a safe-drawing
  Scaffold, consumed content padding, and inset-safe splash content. Layout checks
  on Android 15 and supported older OS versions remain part of release validation.
  Build success alone does not verify background delivery or alarm playback.
- Alarm ringing must remain disabled with `AlarmFeature.ENABLED = false`.
  Ordinary reminder notifications and retries remain active.
- If a launcher icon remains stale after reinstalling, a clean reinstall may help;
  uninstalling also removes the installed app's local data.
- Before changing the app version, follow [Versioning](versioning.md): propose the
  exact new version and obtain confirmation unless that version is already approved.

Settings V2 validation: debug APK, all 21 unit tests, and Android lint completed successfully using the installed JBR 21 runtime. A temporary API 37 Pixel emulator verified the popup, minute entry, saved 18:25 UTC after app restart, 12/24-hour switching, alarm-disabled UI, footer background, and two-tab navigation. Lint retains existing warnings. The installed Java 26 runtime triggered an internal lint analyzer crash; use JBR 21 for these checks on this host.

## Optimized device smoke test

Build the isolated, debug-signed `.qa` package with the release optimization rules:

```powershell
.\gradlew.bat assembleOptimizedTest assembleOptimizedTestAndroidTest testOptimizedTestUnitTest -PoptimizedSmoke
```

Install both APKs from `app/build/outputs/apk/optimizedTest/` and
`app/build/outputs/apk/androidTest/optimizedTest/` on a test emulator, then run:

```powershell
adb shell am instrument -w com.streakguard.app.qa.test/com.streakguard.app.OptimizedSmokeRunner
adb shell am force-stop com.streakguard.app.qa
adb shell am instrument -w -e restart true com.streakguard.app.qa.test/com.streakguard.app.OptimizedSmokeRunner
```

The smoke runner refuses production packages. It checks real Room persistence,
persisted solved-day suppression, manual refresh, account changes, expired UTC-day
markers, retry/poll cancellation, stale delivery, and reflective WorkManager startup
without calling a live API. Never publish the `.qa` build. Verify Play Console's
DEX optimization metrics only after uploading the signed production bundle.

Validation on 2026-10-18: debug APK, optimized release APK/AAB, symbol archive,
24 unit tests, and lint passed (0 errors, 24 existing warnings, 1 hint). The AAB
contains `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`.
The API 37 optimized QA smoke test and a second run after process termination passed.
Today, Settings, and footer layouts were checked with gesture and three-button
navigation; the original gesture mode was restored. Android 15/older OS and splash
visual checks, plus Play Console metric confirmation after a signed upload, remain pending.
