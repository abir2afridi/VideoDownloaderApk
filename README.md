# Smart Video Downloader

An Android application for downloading videos and media from the web with a built-in browser, multi-threaded download engine, and private vault.

## Features

- **In-App Browser** — Full WebView with incognito mode, HTTPS-only toggle, tracker blocking, and bookmarking
- **Media Detection** — Automatically detects downloadable media on web pages
- **Multi-Threaded Downloads** — Segmented (chunked) download engine with pause/resume support
- **Private Vault** — PIN/biometric-protected storage for sensitive downloads
- **File Browser** — Completed downloads with video playback, category filtering, and health indicators
- **Health Monitoring** — Background integrity checks via WorkManager that verify file and connection health
- **Theming** — AMOLED dark mode, multiple accent color themes (Teal, Blue, Orange, Bento)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room |
| Networking | OkHttp + Retrofit + Moshi |
| Background | WorkManager |
| Navigation | Navigation Compose |
| Image Loading | Coil |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 (Android 16) |

## Building

1. Open the project in Android Studio.
2. Build and run the `app` module.

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
├── MainActivity.kt                  # Entry point
├── ui/
│   ├── viewmodel/MainViewModel.kt   # Central ViewModel
│   ├── screens/
│   │   ├── DashboardTab.kt          # Home dashboard
│   │   ├── BrowserTab.kt            # WebView browser
│   │   ├── DownloadsTab.kt          # Download queue
│   │   ├── FilesTab.kt              # File browser
│   │   ├── VaultTab.kt              # Private vault
│   │   └── SettingsTab.kt           # Settings
│   ├── components/                  # Reusable composables
│   └── theme/                       # Colors, typography, theming
├── data/
│   ├── database/                    # Room entities and DAOs
│   └── download/                    # Download engine + integrity worker
```

## License

This project is provided for educational purposes.

