# Building

## Android Studio (recommended)

1. Pull the repo and open it in Android Studio.
2. Let Gradle sync (first sync downloads dependencies).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`
   (Android Studio also shows a "locate" link when the build finishes).
5. For a release build use **Build > Generate Signed Bundle / APK**.

## Notes

- `compileSdk` / `targetSdk` 37, `minSdk` 26, Java 17.
- Font files (`res/font/*.ttf`) are binary and can't be pushed through the
  GitHub API integration — if they are missing after a pull, copy them into
  `app/src/main/res/font/` manually (see the release notes / chat).
- If the launcher icon looks stale on a device after reinstalling, uninstall
  the app once first — Android caches launcher icons aggressively.
