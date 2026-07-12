# Vortex Engine — Smart Video Downloader

An Android application for downloading videos and media from the web with a built-in browser, multi-threaded download engine, and private vault.

## Features

- **In-App Browser** — Full WebView with incognito mode, HTTPS-only toggle, tracker blocking, bookmarking, and media detection via JavaScript bridge
- **Multi-Threaded Download Engine** — Segmented (chunked) downloads with pause/resume, real-time speed tracking, and adaptive threading
- **Media Detection** — Automatically detects `<video>` and downloadable media links (`.mp4`, `.mp3`, `.m4a`) on web pages
- **Download Queue** — Active downloads section with progress bars, speed indicators, estimated remaining time, and health badges
- **Private Vault** — PIN-protected secure storage for sensitive downloads; files hidden from device gallery
- **File Library** — Completed downloads browser with category filtering (Video/Audio/Images/Other), video playback, file sharing, and move-to-vault
- **Download Health Monitoring** — Background WorkManager worker that periodically verifies file integrity and connection health
- **Customizable Theme** — Light, Dark, System, and AMOLED Black modes with 13 accent colors (Bento, Teal, Blue, Orange, Red, Pink, Purple, Indigo, Cyan, Green, Yellow, Orange, Brown)
- **Storage Management** — Visual storage overview with video/audio size breakdown and one-tap cache optimization

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM with Repository Pattern |
| Database | Room (SQLite) via Kotlin Coroutines Flow |
| Networking | OkHttp 4.x, Retrofit 2.x, Moshi |
| Browser Engine | Android WebView with JavaScript Interface |
| Background Work | WorkManager 2.9.x |
| Image Loading | Coil |
| Security | Encrypted SharedPreferences, FileProvider |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 (Android 16) |

## Building

1. Open the project in Android Studio.
2. Sync Gradle and build the `app` module.

### Signing

Release builds require the following environment variables:
- `KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Debug builds use a local `debug.keystore` with default credentials.

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt                    # Entry point with bottom navigation
├── ui/
│   ├── viewmodel/MainViewModel.kt     # Central ViewModel (StateFlow + Room)
│   ├── screens/
│   │   ├── DashboardTab.kt            # Home dashboard (storage, streams, recent)
│   │   ├── BrowserTab.kt              # WebView browser with media detection
│   │   ├── DownloadsTab.kt            # Download queue (active + completed)
│   │   ├── FilesTab.kt                # File library with categories
│   │   ├── VaultTab.kt                # PIN-protected private vault
│   │   └── SettingsTab.kt             # Preferences, theme, storage path, about
│   ├── components/                    # Reusable composables
│   │   ├── TabHeader.kt               # Section header with category + title
│   │   ├── DownloadHealthIndicators.kt # Integrity & connection health badges
│   │   └── VideoPlayerDialog.kt       # Full-screen video player
│   └── theme/                         # Colors, typography, theme system
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── data/
│   ├── database/                      # Room entities, DAOs, database
│   │   ├── Entities.kt                # DownloadEntity, BookmarkEntity
│   │   ├── DAOs.kt                    # DownloadDao, queries
│   │   └── AppDatabase.kt             # Room database singleton
│   └── download/                      # Download engine + utilities
│       ├── DownloadEngine.kt          # Multi/single-thread download manager
│       ├── MediaUtils.kt              # Formatting, filename parsing
│       └── DownloadIntegrityWorker.kt # Periodic health checks via WorkManager
```

## Developer

**Abir Hasan Siam**

- GitHub: [github.com/abir2afridi](https://github.com/abir2afridi)
- Portfolio: [abir2afridi.vercel.app](https://abir2afridi.vercel.app/)
- Computer Science · Independent University of Bangladesh

## License

MIT License — feel free to use, modify, and distribute.
