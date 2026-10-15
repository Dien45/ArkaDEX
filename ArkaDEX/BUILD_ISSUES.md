## Build Issues

### Resolved (project restructure)

The project previously could not build at all. Fixed:

1. **No `AndroidManifest.xml`** — added `ArkaDEX/app/src/main/AndroidManifest.xml`
   (launcher + optional HOME filter, 4 built-in apps, overlay permission, `<queries>` for app listing on API 30+).
2. **No dependencies / Kotlin plugin** — the old root `build.gradle` had zero
   dependencies; the nested one referenced an undefined `$kotlin_version`.
   Now: root `build.gradle` (plugin versions) + `app/build.gradle` (Kotlin
   1.9.22, appcompat, core-ktx, material, gson), `minSdk 26` (freeform +
   `TYPE_APPLICATION_OVERLAY` require API 26+).
3. **Broken `settings.gradle`** (`include ':ArkaDEX'` pointing nowhere) —
   rewritten with repository management + `include ':app'`.
4. **`gradle.properties` forced `-Xint` interpreted mode** — replaced with sane defaults.
5. **Duplicate source trees** (`src/compose` identical to `src/main`) — removed; canonical tree is `app/src/main`.
6. **CI gaps** — added `platforms;android-34` + `build-tools;34.0.0` packages
   (platform-tools alone cannot compile) and fixed the artifact path to
   `ArkaDEX/app/build/outputs/apk/debug/app-debug.apk`.
7. **Junk in repo** — removed committed session snapshots (`root/...`,
   `ArkaDEX/root/...`), triple-nested project copies and duplicate PRDs.
8. **Missing LICENSE** — added Apache 2.0 (`LICENSE`), per PRD assumption 9.

### Desktop launcher gaps (this pass)

Diagnosis against the goal "an Android launcher that behaves like a desktop":
the manifest already declared `HOME`, but the desktop experience was skeletal.
Fixed, without adding any permission:

1. **Windows were not movable/resizable** — `moveWindow()`/`resizeWindow()`
   existed but nothing ever called them (no `OnTouchListener` anywhere, despite
   the README claiming drag/resize). Added a title-bar drag listener and a
   28dp corner resize grip in `FreeformWindowManager.kt`, clamped to a 200x160dp
   minimum. Touch is attached to the title bar, not the `_`/`[]`/`X` buttons, so
   button clicks still win (children consume their own touches).
2. **Desktop icons were text-only** — the grid used
   `android.R.layout.simple_list_item_1`. Added `res/layout/item_app_icon.xml`
   (glyph + label), a `Drawable` field on `DesktopApp`, and icon loading via
   `loadIcon`/`getActivityIcon` behind `runCatching`, with
   `defaultActivityIcon` as fallback.
3. **No wallpaper** — background was a solid `setBackgroundColor`. Added
   `res/drawable/desktop_wallpaper.xml` (ClayMorphism gradient) and
   `applyWallpaper()`, which prefers the system wallpaper and falls back to the
   gradient. Entirely inside `runCatching` so a wallpaper failure cannot blank
   the home screen.
4. **Taskbar** — DEX button now acts as "show desktop" (restores all minimized
   windows) instead of only scrolling the grid; window buttons carry state for
   screen readers.

### Known limitations (not bugs)

- Windows use `TYPE_APPLICATION_OVERLAY`, so they float above other apps and
  require `SYSTEM_ALERT_WINDOW`. Intentional for this iteration; embedding them
  in the launcher activity is the next refactor.
- The taskbar only tracks ArkaDEX's own overlay windows. Third-party apps
  launched with `launchBounds` run as separate tasks outside its control.

### Verification status

Static checks only in this environment: brace balance on edited files, no
stale `simple_list_item_1`/solid-background references, and all `R.id.*`
lookups matched against declared layout ids. **Not compiled here** — no JDK,
Gradle, or Android SDK is installed (see the environment note below), so CI
remains the compile gate for this change.

### Environment note (unchanged)

Local builds on systems with a grsecurity/PaX kernel fail because the JVM
needs executable memory pages for its JIT:

```
Error occurred during initialization
Failed mark memory page executable check grsecurity/PaX enabled
```

Options: disable PaX for the Java binary, build in a container/VM without
PaX, or rely on CI (GitHub Actions `ubuntu-latest` has no PaX).
