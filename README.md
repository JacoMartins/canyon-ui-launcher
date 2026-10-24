# Canyon UI

**Canyon Handheld UI Launcher Open Source Project.** An open source Android home screen launcher
that aims to give everyone premium-level design and usability. Written in Kotlin; developed and
tested first on a Galaxy A03 (Android 13, arm64).

- `applicationId`: `io.github.jacomartins.canyonui`
- minSdk 29 · targetSdk 37 · compileSdk 37 (compileSdk is required by Haze 2.0.1, see `docs/stage0-report.md`)
- Kotlin 2.4.21, AGP 9.4.1, Gradle 9.8.2, Compose BOM 2026.09.00, Haze 2.0.1 (+ `haze-blur`, `haze-glass`)

**Code map:** https://jacomartins.github.io/claude-launcher/ is an interactive map of the source
(dependency graph, search, and each file with its snippets). It lives in `site/` and is published to
the `gh-pages` branch by `.github/workflows/pages.yml` on every push to `dev`.

## Build

Requires JDK 17+ and an Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`.

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug lintDebug testDebugUnitTest   # debug APK, lint, unit tests
./gradlew assembleRelease                             # R8-optimized, signed with the debug key
```

Both build types are signed with `app/debug.keystore`, a throwaway key committed on purpose so that
APKs built on any machine upgrade each other with `adb install -r`. Never use it for a real release.

## APKs

Each stage puts its APKs in `dist/`:

- `launcher-stageN-debug.apk`: debuggable build.
- `launcher-stageN-optimized.apk`: R8-optimized and non-debuggable. Use this one to judge smoothness
  and frame times; debug Compose builds are much slower.
