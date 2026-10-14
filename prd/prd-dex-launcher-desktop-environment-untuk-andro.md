# PRD: DEX — Launcher Desktop Environment untuk Android (Layar 4.9"+, Rasio Apa Pun, Dioptimalkan Penuh)
## Ringkasan
DEX (Desktop Environment for Android eXperience) adalah **launcher** Android open-source yang mengubah perangkat Android menjadi desktop sungguhan. Pengguna membuka aplikasi dalam bentuk
jendela (window) yang bisa digerakkan, di-resize, diminimalkan, dimaksimalkan, dan ditumpuk, lengkap dengan taskbar, wallpaper, dan ikon desktop yang meniru perilaku Windows/macOS/Linux. DEX dioptimalkan untuk **semua perangkat dengan layar di atas 4.9 inci pada rasio layar apa pun**, dengan tampilan yang **otomatis menyesuaikan** terhadap ukuran dan rasio layar serta **smooth** saat interaksi maupun rotasi. DEX memakai **landscape sebagai tata letak utama** dengan **potret didukung penuh**, dapat diaktifkan sebagai launcher default **secara opsional**, menyimpan state desktop **lokal** dengan **ekspor/impor** untuk cadangan & pindah perangkat, bersifat **open source penuh (gratis)** dengan lisensi **Apache 2.0**, dan dipublikasikan di GitHub agar bisa di-build sendiri.
## Asumsi
1. DEX adalah **launcher Android** (bukan virtual machine / emulator) aplikasi yang "dibuka" adalah aplikasi Android biasa yang dirender dalam jendela.
2. Pengaturan sebagai launcher default bersifat **opsional** pengguna memilih sendiri, tanpa paksaan.
3. Target perangkat: **semua layar atas 4.9 inci dengan rasio apa pun** ponsel besar 4.9"), tablet kecil/besar, foldable, dan rasio tidak umum (ultrawide/sempit). Layar 4.9 inci tidak didukung.
4. **Tampilan otomatis menyesuaikan** terhadap ukuran **dan rasio** layar; tidak ada asumsi rasio tetap. Landscape tetap tata letak utama, potret didukung penuh.
5. Dukungan multi-window/resize memanfaatkan Android **Freeform Window Mode** (stabil sejak Android 8.0/API 26+).
6. "Aplikasi dibuka" mencakup aplikasi bawaan DEX sendiri (Notepad, File Manager, Kalkulator, Browser) **dan** aplikasi Android pihak ketiga yang di-install pengguna.
7. **State desktop disimpan lokal** perangkat, tanpa akun/server/cloud. Perpindahan antar perangkat dilakukan lewat **ekspor/impor** manual.
8. Aplikasi **gratis penuh** tanpa paywall, iklan, atau fitur berbayar; keberlanjutan proyek lewat donasi/sponsor opsional.
9. Proyek **open source** dengan lisensi **Apache 2.0**; seluruh kode dipublikasikan GitHub dan bisa di-build oleh siapa pun.
10. **Cloud sync** state synchronization via GitHub Releases tersedia sebagai fitur opsional (lihat SyncService.kt) untuk pengguna yang ingin menyimpan dan menyinkronkan state desktop antar perangkat melalui cloud.
## Latar Belakang Masalah
Android dirancang sebagai sistem operasi mobile: satu aplikasi layar penuh, navigasi gesture, dan tanpa konsep jendela bertumpuk.
Perangkat berlayar besar (tablet, ponsel besar, foldable) keterbatasan ini terasa jelas pengguna tidak bisa membuka banyak aplikasi sekaligus dengan bebas, tidak bisa memposisikan jendela, dan harus terus-meneru berpindah lewat app switcher yang memecah fokus.
Pengguna yang terbiasa dengan desktop (pelajar, pekerja kantoran, developer) kehilangan produktivitas karena metafora "desktop" tidak tersedia.
Banyak solusi desktop hanya mengoptimalkan satu faktor bentuk atau rasio layar tertentu, sehingga tampilannya rusak atau kaku perangkat lain, apalagi saat diputar.
DEX menjawab masalah ini dengan menghadirkan launcher desktop: jendela bisa di-resize, taskbar, dan ruang kerja persisten **yang otomatis menyesuaikan terhadap ukuran dan rasio layar apa pun** serta **halus saat diputar**, sehingga perangkat Android mana pun bisa dipakai layaknya desktop sungguhan.
Karena open source dan gratis, siapa pun bisa membangun, mengubah, dan berkontribusi sendiri lewat GitHub.
## Tujuan (Goals) dan Bukan Tujuan (Non-Goals)
### Tujuan
**G1.** Menyediakan **launcher** desktop dengan ikon, wallpaper, dan taskbar yang dapat dikustomisasi, serta **dapat** (opsional) diaktifkan sebagai launcher default.
**G2.** Memungkinkan setiap aplikasi dibuka dalam **je