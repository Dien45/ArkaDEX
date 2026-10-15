# ArkaDEX

A lightweight desktop application built with Kotlin/Compose for Android, featuring:

## Theme
- **ClayMorphism** aesthetic with **Light Blue** color palette
- Primary: `#4A90E2` (Light Blue)
- Secondary: `#5B7D8B` (Muted Steel Blue)
- Accent: `#88C0D0` (Soft Cyan)
- Background: `#FAF9FF` (very light blue-ish)

## Features
- **Freeform Window Manager** – resizable, movable, minimize/maximize, snap-to-floor
- **Cloud Sync** – state synchronization with GitHub Releases (distribution) and local persistence
- **ClayMorphism UI** – organic, tactile design inspired by clay sculpture

## Architecture
```
ArkaDEX/
├── theme.yaml              # ClayMorphism color palette
├── src/compose/
│   ├── MainActivity.kt     # Entry point, initializes WindowManager
│   ├── WindowManager.kt    # Freeform window arrangement (resize, move, focus)
│   └── SyncService.kt      # Cloud sync service (GitHub Releases + local storage)
└── data/
    ├── state/              # Local state persistence (JSON)
    ├── sync/               # Cloud sync configuration
    └── config/             # App configuration
```

## Cloud Sync
- Saves application state to local file (`<package_name>_state.<version>`)
- Pushes state to GitHub Releases (distribution channel)
- Loads previously saved state on app startup
- Offline-first: works without internet, syncs when online

## Development
```bash
cd ArkaDEX
gradlew build
```

## Dependencies
- Kotlin 1.9+
- Android Studio / Compose (Desktop)
- No external cloud SDK required for basic functionality
