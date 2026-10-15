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

**CI caught a bug static review missed** (run #12, commit `2b63093`):
`PackageManager.getActivityIcon()` accepts a `ComponentName` or `Intent`,
not a `Class`, so the four built-in-app icon lookups passed
`NotepadActivity::class.java` and failed `:app:compileDebugKotlin` with
`Type mismatch: inferred type is Unit but Drawable was expected`. This is
exactly the class of error a brace-balance/id check cannot see. Fixed by
building the `ComponentName` once in a `builtin()` helper and reusing it for
both the `component` field and the icon lookup. Re-verified: no remaining
`getActivityIcon(<Class>)` call sites.

### Startup force-close (this pass)

After `2b63093` the app crashed the moment it was opened. Root cause was the
new desktop icon grid:

- `ArrayAdapter`'s 3-arg constructor `(context, resource, objects)` sets
  `mFieldId = 0`.
- With `mFieldId == 0`, `createViewFromResource()` casts the **entire item
  root** to `TextView`: `text = (TextView) view`.
- `item_app_icon.xml`'s root is a `LinearLayout`, so that cast threw
  `ClassCastException`, rethrown by ArrayAdapter as
  `IllegalStateException("ArrayAdapter requires the resource ID to be a
  TextView")`.
- `GridView` lays out children on the first frame → crash before any UI
  appeared.

The pre-existing grid used `android.R.layout.simple_list_item_1`, whose root
*is* a `TextView` — which is why `mFieldId = 0` was harmless before and broke
only when `item_app_icon` was introduced.

**Fix** (`4cafe6f`): pass `R.id.app_label` as the field id via the 4-arg
constructor, the documented way to use a complex layout. The lookup becomes
`findViewById(app_label)`, which resolves to a real `TextView`, so the root
cast is never evaluated.

**Why CI stayed green:** this is a *runtime* crash. `assembleDebug` only
proves the code compiles; it cannot detect `ClassCastException` in `getView()`.
Green CI therefore never means "the app runs".

### Verification limits — read before trusting a green build

Local static checks (brace balance, `R.id`/`R.layout`/`R.drawable` resolution,
stale-reference greps) **cannot** find this class of bug, and CI only gates
compilation. Neither proves the launcher starts. Two caveats on the earlier
static analysis in this document:

1. A regex pass reporting `R.layout.simple_list_item_1` as a missing project
   resource was a **false positive** — the real call is
   `android.R.layout.simple_list_item_1`, a framework resource whose `TextView`
   root makes `mFieldId = 0` safe there.
2. A `comm`-based id comparison emitted spurious rows because the extraction
   regex left a trailing `)`; a corrected script confirms all `R.id`,
   `R.layout` and `R.drawable` references resolve.

**Definitive runtime verification needs logcat from a device/emulator**
(`adb logcat -s AndroidRuntime`). No JDK/SDK/emulator exists in this
environment, so the FC fix is currently confirmed by source-level reasoning
against AOSP `ArrayAdapter` only — it still needs an on-device check.

### Desktop state round-trip (this pass)

Three defects made the documented "state desktop tersimpan lokal" and
export/import features non-functional. None are reachable by `assembleDebug`;
all three surfaced from reading the code.

1. **The export/import menu could never open.** `MainActivity` implemented
   `onCreateOptionsMenu` / `onOptionsItemSelected`, but the app theme is
   `Theme.Material3.DayNight.NoActionBar`. With no action bar the system never
   calls those callbacks, so the menu was dead UI and both features were
   unreachable. Fixed with a taskbar overflow button (`btn_overflow`) that
   inflates `res/menu/desktop_menu.xml`; the action-bar overrides are removed.

2. **Overlay windows outlived the activity.** Views added through the system
   `WindowManager` are not torn down when the activity dies, and
   `FreeformWindowManager.closeAll()` had no caller. After `recreate()`
   (which import triggers) old windows stayed on screen next to the new
   desktop, and a `uiMode` change - absent from `configChanges` - recreated the
   activity and left windows holding a destroyed `Activity` context. Fixed with
   `closeAll()` in `onDestroy`, after detaching `onWindowsChanged` so teardown
   does not touch the view tree.

3. **Persisted state was write-only.** `onPause` saved window *titles* and
   nothing ever read them back; `Dextop.state` had no reader outside its own
   declaration. Persistence now stores title, bounds and a `contentKey` (the
   hosting activity class name, so a restored window rebuilds the same app
   rather than guessing from its label), and `restoreWindows()` reopens each
   window once `onWindowsChanged` is wired.

Two ordering hazards found while fixing #3, both silent:

- **Import was undone by the very next `onPause`.** `recreate()` runs
  `onPause -> onDestroy -> onCreate`, so a pause-time write overwrote the
  freshly imported state before anything could read it. A one-shot
  `skipNextPersist` flag suppresses that single write.
- **Persisting would erase a layout when permission was revoked.** Without
  overlay permission no window can be open, so an unconditional write stores an
  empty snapshot over a previously saved desktop. Persist now requires
  `canDrawOverlays()`.

Geometry read back from JSON is clamped to 200x160dp - 2000dp: an imported file
is arbitrary user input, and `LayoutParams` throws on a non-positive size.

Also corrected: built-in windows and the welcome panel were positioned and
sized in raw pixels while every other dimension in the codebase is dp, so on a
3x-density display they opened as postage stamps.

### Verification status (state round-trip)

Static only, same limits as the rest of this document: brace/paren balance
across all 10 Kotlin files, every `R.id` / `R.layout` / `R.menu` / `R.string` /
`R.drawable` reference resolved against declared resources, no unused or
dangling imports, and stale-reference greps for the removed action-bar menu.
**Not compiled** - no JDK, Gradle or Android SDK in this environment.

The restore path is reasoned about, not observed. Needs a device check:
(a) open three windows, force-stop, relaunch, confirm they return with their
geometry; (b) export, change the desktop, import, confirm the imported layout
wins over the current one; (c) revoke the overlay permission, relaunch, and
confirm the saved layout is not erased.

### Window behaviour, threading and cleanup (this pass)

P1/P2 from the review pass. Again none reachable by `assembleDebug`.

- **Minimized windows stopped swallowing taps.** `minimizeWindow()` shrank the
  window to 1x1 but left it at its old coordinates. With `FLAG_NOT_TOUCH_MODAL`
  that invisible sliver still sat on top of whatever was underneath and ate the
  next tap there. The window is now parked off-screen.
- **Maximize is a toggle.** Pressing `[]` twice previously did nothing the
  second time, and maximizing a minimized window left a stale `savedBounds`
  that a later restore resurrected. `ManagedWindow` now tracks `maximized`.
- **Browser windows leaked a renderer process each.** Closing a window only
  removed the view from the WindowManager, which does not end the WebView's
  renderer. `openWindow` takes an `onClose` hook; `BrowserActivity.releaseView()`
  stops and destroys the WebView.
- **Icon loading moved off the main thread.** `collectApps()` runs
  `queryIntentActivities` + `loadIcon` for every installed package and ran
  twice at startup (onCreate, then onResume). It now runs on a single-thread
  executor behind a one-load-in-flight guard. The adapter is only rebound when
  the app set actually changes, identified by label + component, because
  comparing `DesktopApp` directly never matches (it holds a `Drawable`, and a
  fresh load always produces new instances).
- **The clock ticked in the background.** Callbacks were only cleared in
  `onDestroy`, so a 30s tick kept firing behind whatever the user opened.
  Tick is now posted in `onStart` and removed in `onStop`; the
  `SimpleDateFormat` moved out of the tick loop.
- **Back closes the topmost window** before finishing the launcher, via
  `OnBackPressedCallback`.
- **Cloud sync removed.** `syncState`, `configureCloud`, `pushToCloud` and
  `getSyncedState` were unreachable stubs behind a `configureCloud()` no UI
  ever called; `getSyncedState()` even returned an empty map once configured.
  The README listed it as a working feature. Removed rather than shipped as
  documentation.
- **`Panel.resize` / `Panel.move`** removed, never called.
- Resize grip reduced to 22dp and made fainter; it is added last, so it draws
  above app content and was covering the bottom-right corner of every window.
- `Calculator.Engine` extracted to `CalculatorEngine.kt` and covered by
  `CalculatorEngineTest.kt` (7 cases), because a pure engine is testable on a
  plain JVM and the nested companion class was not. CI now runs
  `testDebugUnitTest` before `assembleDebug` and uploads the reports on failure.

### Two review findings that were wrong

Both were listed as defects and then dropped rather than "fixed":

1. **"Calculator output breaks under a comma-decimal locale"** — false
   positive. `format()` uses `Double.toString()`, which the JVM defines as
   locale-independent; the locale-sensitive path is `String.format` /
   `NumberFormat`, neither of which is used here. `CalculatorEngineTest`
   pins the guarantee under `Locale.GERMANY` so it cannot be broken later by
   someone "improving" the formatting.
2. **"The four built-in activities are dead manifest entries"** — false
   positive. `PackageManager.getActivityIcon(ComponentName)` resolves through
   the manifest and throws `NameNotFoundException` for an undeclared
   component. Removing those entries would have silently swapped every
   built-in app icon for the generic default. Kept.

### Verification status (window behaviour pass)

Static only, same limits: brace balance across all 12 Kotlin files, full
`R.*` resolution, an unused-import scan, and targeted greps for each fix.
Not compiled here (no JDK/Gradle/SDK). The unit tests are new code that has
never been executed locally, so they are themselves unverified until CI runs
them -- worth watching the first run.

On-device still outstanding, in addition to the restore checks above:
(a) minimize a window then tap where it used to sit; (b) press `[]` twice and
confirm it toggles back; (c) open and close five Browser windows and confirm
memory does not climb.

### Environment note (unchanged)

Local builds on systems with a grsecurity/PaX kernel fail because the JVM
needs executable memory pages for its JIT:

```
Error occurred during initialization
Failed mark memory page executable check grsecurity/PaX enabled
```

Options: disable PaX for the Java binary, build in a container/VM without
PaX, or rely on CI (GitHub Actions `ubuntu-latest` has no PaX).
