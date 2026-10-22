# Stage 0 report

## Environment

| Item | Result |
|---|---|
| Host | x86_64 Ubuntu 24.04 cloud VM, 4 vCPU, 15 GiB RAM. Android build tools run natively (no QEMU). |
| JDK | OpenJDK 21 (AGP 9 supports 17+; bytecode targets Java 17). |
| SDK | `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools`; licenses accepted. aapt2 2.20. |
| Network | dl.google.com, maven.google.com, repo.maven.apache.org, plugins.gradle.org, services.gradle.org all reachable. |
| Maven Central | `repo.maven.apache.org` returns HTTP 429 (rate limit) on Gradle's bursty downloads. The build uses Google's official Maven Central mirror (`maven-central.storage-download.googleapis.com`) first, with Maven Central as fallback. |

## Version decision

Haze 2.0.1 exists on Maven Central. Its Android artifact depends on Kotlin stdlib 2.4.20, Compose
Multiplatform 1.12.1, `androidx.activity` 1.13.0 (`minCompileSdk=36`) and
`lifecycle-runtime-compose` 2.11.0 (`minCompileSdk=37`, AGP 9.1+). compileSdk 34 is therefore
impossible with 2.0.1. Only Haze ≤1.3.x builds against compileSdk 34, and it has no Glass effect.

Chosen: Haze 2.0.1 with compileSdk 37, AGP 9.4.1, Kotlin 2.4.21. minSdk 29 and targetSdk 33 are
unchanged, so runtime behaviour on the phone is the same as with compileSdk 34.

## Haze 2.0.1 summary (from the docs site and the 2.0.1 sources jars)

- **Pairing.** `Modifier.hazeSource(state)` marks Compose content to capture. An effect modifier
  reads it through an explicit input: `HazeInput.Sources(state)` (exact captured sources),
  `HazeInput.Backdrop(state)` (earlier pixels in the window, source capture as fallback) or
  `HazeInput.Content` (own content). Sources and effects must be in the same Compose hierarchy
  (dialogs are supported); classic Views and the system wallpaper are not capturable.
- **Effects are separate modules in 2.x.** `haze-blur` provides `Modifier.hazeBlur(input, HazeBlurStyle { blurRadius(); noiseFactor(); colorEffects(listOf(HazeColorEffect.tint(...))); fallbackColorEffect() })`.
  `haze-glass` provides `Modifier.hazeGlass(input, GlassStyle.regular / .clear / GlassStyle { ... })`:
  refraction, depth blur, tint, Fresnel/ambient lift, specular rim, optional chromatic aberration.
  Glass is `@ExperimentalHazeApi`.
- **Android versions.** Blur uses `RenderEffect`: real blur on API 31+, on by default from
  API 32 (Android 12 uses a pre-draw invalidation workaround). Below API 31 the effect draws its
  fallback tint/scrim. Liquid Glass needs `RuntimeShader`, so API 33+; on older versions it keeps
  tint, shape, rim and a radial highlight but drops refraction and blur. The A03 (API 33) gets
  both real blur and real glass. Native "window backdrop" sampling (which could include the
  wallpaper) needs Android 37.2 and an experimental flag, so it is not available on the A03.
- **Performance.** Cost scales with the covered area, the number of effects and how often the
  source changes. A stable source reuses processed output; a scrolling or animating source must be
  re-captured and re-filtered every frame. Glass keeps several offscreen layers per surface;
  `refractionDetailIntensity(0f)` (Regular's default) removes the extra detail layers.
  `HazePerformanceMode.Performance/Balanced/Quality` trades resolution for cost, and Balanced is
  the default. Progressive blur costs more than a mask. The docs recommend measuring first
  appearance separately from steady-state animation.

## Glass plan per surface (to confirm on the device)

The home grid and widgets are Views, so Haze cannot blur them directly. Options per surface:

| Surface | Plan |
|---|---|
| Dock, home search bar | Glass over a Compose-drawn copy of the wallpaper, **if** the app can read the wallpaper (see probe below); otherwise tinted scrim. Source is static, so the cost after the first frame is low. Paging does not move the source. |
| Folder popup, long-press menus, bottom sheets, edit toolbar | Compose overlay over the same wallpaper copy. Icons/widgets underneath are Views and are not refracted; the popup gets a dimmed scrim behind it. |
| Drawer header / search | The drawer list is Compose-drawn, so the header can blur the list scrolling under it (real `hazeSource`). Highest-risk surface for frame time because the source moves. |
| Page overview cards | Flat tinted cards (thumbnails are bitmaps; blurring them adds cost for little gain). |

## Wallpaper access

Since Android 13, `WallpaperManager.getDrawable()` returns the default wallpaper or throws unless
the caller holds `MANAGE_EXTERNAL_STORAGE` ("All files access"). Stage 0 contains a probe that
shows what the A03 actually returns.
`WallpaperManager.getWallpaperColors()` works without any permission and is enough for dynamic colors.

## Theme Park / Good Lock icons

- No public API, content provider or documented intent exposes Theme Park icon sets to other apps.
  Theme Park builds theme/overlay APKs that Samsung's theme service applies. They are not icon
  packs in the ADW/Nova format, and applying them needs Samsung's privileged theme service.
- Reading those packages' resources directly would mean guessing an undocumented internal
  layout, which is not done here.
- The one legitimate route: if One UI applies the themed icons at the framework level,
  `LauncherApps` returns the themed icons to every launcher with no extra work. The Stage 0
  "Probe icons" card tests exactly that on the phone.
- Fallbacks if it does not work: the icon-pack chooser (Stage 4), the user's own icons, and
  "match system colors" (dynamic color and wallpaper colors).
