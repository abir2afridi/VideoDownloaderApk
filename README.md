# NexLoad — Smart Video Downloader

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/abir2afridi/NexLoad/releases/tag/v1.1.0)
[![Release](https://img.shields.io/github/release/abir2afridi/NexLoad.svg)](https://github.com/abir2afridi/NexLoad/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![API](https://img.shields.io/badge/API-24%E2%80%9336-blueviolet.svg)](https://developer.android.com/studio/releases/platforms)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org/)
[![Build](https://img.shields.io/badge/build-passing-success.svg)](.github/workflows/release.yml)

[⬇️ Download Latest APK](https://github.com/abir2afridi/NexLoad/releases/latest/download/app-release.apk)
&nbsp;|&nbsp;
[📜 Release Notes](https://github.com/abir2afridi/NexLoad/releases/latest)
&nbsp;|&nbsp;
[📋 Changelog](CHANGELOG.md)

An Android application for downloading videos and media from the web with a built-in browser, multi-threaded download engine, and private vault.

## Features

- **In-App Browser** — Full WebView with incognito mode, HTTPS-only toggle, tracker blocking, bookmarking, and media detection via JavaScript bridge
- **Multi-Threaded Download Engine** — Segmented (chunked) downloads with pause/resume, real-time speed tracking, and adaptive threading
- **Analyze-First UX** — Paste any link on the dashboard, tap Analyze, see platform info + quality options, then download
- **Multi-Platform Video Downloader** — Supports TikTok, Instagram, Facebook, Twitter/X, Reddit, Pinterest, SoundCloud, Vimeo, Twitch, Dailymotion, Tumblr, and ANY website via generic fallback extraction
- **TikTok Downloader** — TikWM API + 9 fallback strategies for HD no-watermark, watermarked, and audio-only downloads
- **Instagram Downloader** — 4-strategy chain: third-party API (primary), embed /captioned/, GraphQL with session warmup, regular embed
- **Facebook Downloader** — Third-party API (yt-dlp based), embed plugin, touch/mbasic/mobile pages with cookie support
- **Twitter/X Downloader** — og:video, twitter:player:stream, and CDN URL extraction
- **Generic Fallback** — 10 extraction strategies for ANY website (og:video, JSON-LD, video tags, CDN patterns, etc.)
- **Social Media Authentication** — WebView-based Instagram and Facebook login for cookie-captured extraction
- **Media Detection** — Automatically detects `<video>` and downloadable media links on web pages
- **Download Queue** — Active downloads section with progress bars, speed indicators, estimated remaining time, and health badges
- **Private Vault** — PIN-protected secure storage for sensitive downloads; files hidden from device gallery
- **File Library** — Completed downloads browser with category filtering (Video/Audio/Images/Other), video playback, file sharing, and move-to-vault
- **Download Health Monitoring** — Background WorkManager worker that periodically verifies file integrity and connection health
- **Customizable Theme** — Light, Dark, System, and AMOLED Black modes with 13 accent colors
- **Storage Management** — Visual storage overview with video/audio size breakdown and one-tap cache optimization

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM with Repository Pattern |
| Database | Room (SQLite) via Kotlin Coroutines Flow |
| Networking | OkHttp 4.x, Moshi |
| Browser Engine | Android WebView with JavaScript Interface |
| Background Work | WorkManager 2.9.x |
| Image Loading | Coil |
| Security | Encrypted SharedPreferences, FileProvider |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 (Android 16) |

## Installation

### Latest Release — v1.1.0

| File | Size | SHA-256 |
|------|------|---------|
| [app-release.apk](https://github.com/abir2afridi/NexLoad/releases/latest/download/app-release.apk) | 15.13 MB | `CE23E9DBE52D6DE27076B0472C92A6ED289059DFDB51E7AD6D99A128D069694F` |
| [app-release.aab](https://github.com/abir2afridi/NexLoad/releases/latest/download/app-release.aab) | 15.40 MB | `D0C014B13541EECC143D482F399E37D5754C0429923CDB39AC272B27673FE0E9` |

### Requirements
- Android 7.0 (API 24) or higher
- ARM64 / ARM / x86_64

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
│   │   ├── DashboardTab.kt            # Home dashboard with analyze-first link input
│   │   ├── BrowserTab.kt              # WebView browser with media detection
│   │   ├── DownloadsTab.kt            # Download queue (active + completed)
│   │   ├── FilesTab.kt                # File library with categories
│   │   ├── VaultTab.kt                # PIN-protected private vault
│   │   ├── SettingsTab.kt             # Preferences, theme, social media login, about
│   │   ├── InstagramLoginActivity.kt  # WebView-based Instagram login for cookie capture
│   │   └── FacebookLoginActivity.kt   # WebView-based Facebook login for cookie capture
│   ├── components/                    # Reusable composables
│   │   ├── TabHeader.kt               # Section header with category + title
│   │   ├── DownloadHealthIndicators.kt # Integrity & connection health badges
│   │   └── VideoPlayerDialog.kt       # Full-screen video player
│   └── theme/                         # Colors, typography, theme system
├── data/
│   ├── database/                      # Room entities, DAOs, database
│   │   ├── Entities.kt                # DownloadEntity, BookmarkEntity
│   │   ├── DAOs.kt                    # DownloadDao, queries
│   │   └── AppDatabase.kt             # Room database singleton
│   └── download/                      # Download engine + utilities
│       ├── VideoExtractor.kt          # Multi-platform video extraction (20+ platforms)
│       ├── DownloadEngine.kt          # Multi/single-thread download manager
│       ├── TikTokCookieStore.kt       # Shared CookieJar for TikTok requests
│       ├── MediaUtils.kt              # Formatting, filename parsing
│       └── DownloadIntegrityWorker.kt # Periodic health checks via WorkManager
```

## Supported Platforms

| Platform | Extraction Method |
|----------|------------------|
| TikTok | TikWM API + 9 fallback strategies |
| Instagram | Third-party API + embed + GraphQL |
| Facebook | Third-party API + embed + touch/mbasic/mobile |
| Twitter/X | og:video + twitter:player:stream + CDN |
| Reddit | JSON API extraction |
| Pinterest | og:video + CDN |
| SoundCloud | oEmbed + og:audio |
| Vimeo | oEmbed extraction |
| Twitch | og:video + CDN |
| Dailymotion | oEmbed extraction |
| Tumblr | og:video + CDN |
| Any Website | 10-strategy generic fallback |

## Developer

**Abir Hasan Siam**

- GitHub: [github.com/abir2afridi](https://github.com/abir2afridi)
- Portfolio: [abir2afridi.vercel.app](https://abir2afridi.vercel.app/)
- Computer Science · Independent University of Bangladesh

## License

MIT License — feel free to use, modify, and distribute.
