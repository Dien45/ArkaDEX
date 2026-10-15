# ArkaDEX

DEX (Desktop Environment for Android eXperience): launcher Android open-source
yang mengubah perangkat (layar 4.9"+, rasio apa pun) menjadi desktop sungguhan —
jendela freeform yang bisa digerakkan/di-resize/diminimalkan/dimaksimalkan,
taskbar, wallpaper, dan ikon desktop. Gratis penuh, lisensi Apache 2.0.

## Struktur

```
ArkaDEX/            <- proyek Gradle (root)
  settings.gradle
  gradle.properties
  app/
    build.gradle
    src/main/
      AndroidManifest.xml
      kotlin/com/ardex/dex/
        MainActivity.kt            <- launcher desktop (ikon, taskbar, jam)
        Dextop.kt                  <- controller desktop (load state)
        FreeformWindowManager.kt   <- jendela overlay (move/resize/min/max/close)
        Panel.kt                   <- panel ClayMorphism
        SyncService.kt             <- state lokal + ekspor/impor + cloud opsional
        Theme.kt                   <- loader assets/theme.yaml
        apps/
          NotepadActivity.kt       <- catatan teks
          CalculatorActivity.kt    <- kalkulator (+ - * / kurung)
          FileManagerActivity.kt   <- jelajah file privat app
          BrowserActivity.kt       <- WebView + address bar
      res/                         <- layout, themes, strings, colors, icon
      assets/theme.yaml            <- tema ClayMorphism
prd/                <- PRD (bahasa Indonesia)
```

## Build (butuh Android SDK + JDK 17)

```bash
cd ArkaDEX
gradle assembleDebug
# APK: ArkaDEX/app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/build.yml`) build otomatis tiap push/PR ke `main`
di `ubuntu-latest` dan mengunggah APK sebagai artifact.

## Fitur

- Desktop launcher (bisa dipilih jadi launcher default — opsional)
- Jendela overlay: minimize + restore, maximize, close, taskbar per-jendela
- Aplikasi bawaan: Notepad, Kalkulator, File Manager, Browser
- Aplikasi pihak ketiga dibuka freeform (API 28+) atau normal
- State desktop tersimpan lokal; **ekspor/impor** via menu (tanpa izin storage)
- Sync cloud via GitHub Releases: opsional, hanya jika dikonfigurasi
- Tema ClayMorphism dari `assets/theme.yaml` (fallback bawaan bila rusak)

## Izin

- `SYSTEM_ALERT_WINDOW` — untuk jendela overlay (diminta saat runtime).
- `INTERNET` — untuk Browser bawaan.
- Tanpa izin storage: file hanya di direktori privat app + dokumen pilihan user.

## Lisensi

Apache 2.0 — lihat [LICENSE](../LICENSE).
