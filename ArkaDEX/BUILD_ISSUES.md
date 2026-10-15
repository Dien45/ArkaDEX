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

### Environment note (unchanged)

Local builds on systems with a grsecurity/PaX kernel fail because the JVM
needs executable memory pages for its JIT:

```
Error occurred during initialization
Failed mark memory page executable check grsecurity/PaX enabled
```

Options: disable PaX for the Java binary, build in a container/VM without
PaX, or rely on CI (GitHub Actions `ubuntu-latest` has no PaX).
