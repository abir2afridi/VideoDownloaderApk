# NexLoad v1.1.0 Release Notes

**Release Date:** July 16, 2026

## Overview

NexLoad v1.1.0 is a major feature release bringing a multi-tab browser experience, comprehensive privacy controls, bulk download management, and a completely revamped download engine. This release introduces over 30 new features, enhancements, and fixes since the initial v1.0.0 launch.

## Highlights

- 🗂️ **Multi-Tab Browser** — Opera Mini-style tab gallery with normal and private (incognito) modes
- 🛡️ **Ad Blocking Engine** — Customizable tracker blocking with site-level controls
- 📜 **Browsing History** — Persistent history storage accessible from the browser menu
- 📥 **Bulk Download Actions** — Batch pause, resume, retry, and delete
- 🌓 **Force-Dark Web** — Force dark mode on all websites
- 🎨 **13 Accent Colors** — Full color customization with AMOLED black mode

## Features

### Browser
- Multi-tab support with tab gallery (tap tab icon in header)
- Private/Incognito tabs with automatic cookie/cache isolation
- Browsing history with persistent storage
- Customizable ad/tracker blocking with site allowlist/blocklist
- Force-dark web rendering
- Search engine selection
- Homepage customization
- Content settings (JavaScript, cookies, pop-ups)

### Download Engine
- Multi-threaded segmented downloads with adaptive chunking
- Support for 20+ platforms (TikTok, Instagram, Facebook, Twitter/X, Reddit, Pinterest, SoundCloud, Vimeo, Twitch, Dailymotion, Tumblr, and generic fallback)
- Smart MIME type and file extension resolution
- Background integrity checks via WorkManager
- Download queue with pause/resume/retry

### UI/UX
- Redesigned dashboard with analyze-first UX
- Dynamic platform detection with favicon support
- Multi-item selection in file library
- Real-time download monitoring with speed and ETA
- AMOLED dark mode and 13 accent colors
- Configurable browser navigation position

### Security & Privacy
- Private vault with PIN protection
- Incognito browsing mode
- Ad/tracker blocking engine
- Force-dark web rendering
- HTTPS-only toggle

## Installation

### Download

| File | Size | SHA-256 |
|------|------|---------|
| `app-release.apk` | 15.13 MB | `CE23E9DBE52D6DE27076B0472C92A6ED289059DFDB51E7AD6D99A128D069694F` |
| `app-release.aab` | 15.40 MB | `D0C014B13541EECC143D482F399E37D5754C0429923CDB39AC272B27673FE0E9` |

### Requirements

- **Minimum:** Android 7.0 (API 24)
- **Target:** Android 16 (API 36)
- **Architecture:** ARM64, ARM, x86_64 (universal APK)

### Install via APK

1. Download `app-release.apk`
2. Enable "Install from unknown sources" in Settings
3. Open the APK and follow installation prompts

### Install via App Bundle (AAB)

Use the AAB with [bundletool](https://developer.android.com/studio/command-line/bundletool) or upload to Google Play Console.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for complete changelog.

## Compatibility

- Android 7.0 — Android 16
- All screen sizes and orientations
- ARM64, ARM, x86_64
- No root required

## Known Issues

- ProGuard/R8 minification is currently disabled for easier debugging
- `google-services.json` is not bundled; Firebase features may be limited
- Debug keystore is used for local release builds (replace with production keystore for distribution)

## Developer Notes

### Building from Source

```bash
git clone https://github.com/abir2afridi/NexLoad.git
cd NexLoad
./gradlew assembleRelease
```

### Signing

For production releases, set the following environment variables:
- `KEYSTORE_PATH` — Path to your keystore
- `STORE_PASSWORD` — Keystore password
- `KEY_ALIAS` — Key alias
- `KEY_PASSWORD` — Key password

### CI/CD

Automated builds are configured via GitHub Actions (`.github/workflows/release.yml`).  
Trigger a release by pushing a tag matching `v*` (e.g., `v1.1.0`).

## Credits

Developed by **Abir Hasan Siam**

- GitHub: [github.com/abir2afridi](https://github.com/abir2afridi)
- Portfolio: [abir2afridi.vercel.app](https://abir2afridi.vercel.app/)

## License

MIT License
