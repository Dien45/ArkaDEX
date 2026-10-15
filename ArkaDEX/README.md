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

- Desktop launcher (punya intent-filter `HOME`, muncul di picker launcher bawaan Android)
- **Wallpaper desktop**: memakai wallpaper sistem bila bisa dibaca, fallback ke gradasi ClayMorphism
- **Ikon aplikasi asli** (`loadIcon`) + label di grid desktop, dengan glyph fallback
- Jendela overlay: **digeser via title bar**, **di-resize via grip sudut** (min 200×160dp),
  minimize + restore, **maximize = toggle** (kembali ke ukuran sebelumnya),
  close, taskbar per-jendela
- Taskbar: tombol per jendela dengan status minimize + label untuk screen reader;
  tombol **DEX** = *show desktop* (restore semua jendela yang diminimalkan);
  tombol **⋮** = menu export/import (tema app memakai `NoActionBar`, jadi menu
  ini sengaja dipasang di taskbar, bukan action bar)
- Aplikasi bawaan: Notepad, Kalkulator, File Manager, Browser
- Aplikasi pihak ketiga dibuka freeform (launchBounds, API 24+) atau normal
- State desktop tersimpan lokal (judul + geometri + isi jendela), lalu
  **dikembalikan** saat launcher dibuka lagi; **ekspor/impor** via menu
  (tanpa izin storage)
- **Tombol Back** menutup jendela desktop teratas dulu, baru keluar
- Tema ClayMorphism dari `assets/theme.yaml` (fallback bawaan bila rusak)

### Arah: desktop freeform native (fase 1, belum aktif)

Arsitektur sekarang memakai jendela overlay, yang **tidak bisa** berisi app pihak
ketiga — Android tidak mengizinkan menanam Activity milik aplikasi lain ke dalam
view hierarchy sendiri. Jadi app pihak ketiga selalu terbuka fullscreen di luar
kendali taskbar.

Jalan keluarnya adalah freeform windowing bawaan Android (tersedia sejak API 24,
mati secara default). Diaktifkan lewat setting global:

```
settings put global enable_freeform_support 1
settings put secure force_resizable_activities 1
am start --windowingMode 5 <activity>
```

Menulis setting itu butuh `WRITE_SECURE_SETTINGS`, yang dimiliki user shell ADB
(uid 2000). ArkaDEX memakai **Shizuku** (Apache-2.0) untuk mencapainya — **tanpa
root**, cukup pairing Shizuku.

**Bukan semua HP pasti jalan.** Sebagian OEM skin mengabaikan setting ini
diam-diam. Karena itu fase 1 hanya berisi *probe*: menu ⋮ → **Desktop mode**
mengaktifkan freeform lalu meluncurkan aktivitas pengukur diri di
`windowingMode 5`, dan menanyakan langsung ke `dumpsys` mode apa yang benar-benar
diterima tugas itu. Hasilnya `FREEFORM WORKS` atau `NOT VIABLE` beserta log shell
mentahnya, sehingga keputusan berdasar pengukuran di perangkat Anda sendiri,
bukan asumsi.

Fase berikutnya (taskbar yang memantau task sungguhan, trackpad, mode tabletop)
hanya masuk akal kalau probe menjawab `FREEFORM WORKS`.

### Batasan yang disadari

- Jendela dibangun dengan `TYPE_APPLICATION_OVERLAY`, jadi **melayang di atas app lain**
  dan butuh izin `SYSTEM_ALERT_WINDOW`. Ini pilihan arsitektur iterasi pertama
  (risiko rendah); embed langsung ke activity launcher adalah arah refactor berikutnya.
- Taskbar hanya menampilkan jendela overlay yang dikelola ArkaDEX (built-in).
  App pihak ketiga yang dibuka freeform berjalan sebagai task terpisah di luar
  kendali taskbar ini.

## Izin

- `SYSTEM_ALERT_WINDOW` — untuk jendela overlay (diminta saat runtime).
- `INTERNET` — untuk Browser bawaan.
- Tanpa izin storage: file hanya di direktori privat app + dokumen pilihan user.

## Lisensi

Apache 2.0 — lihat [LICENSE](../LICENSE).
