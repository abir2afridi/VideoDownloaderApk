# Changelog

## [1.1.0] — 2026-07-16

### 🚀 New Features

- **Multi-Tab Browser Support** — Opera Mini-style tab gallery with normal and private (incognito) modes, tab switching, and tab closure
- **Browser Privacy & Ad Blocking Engine** — Comprehensive tracker blocking with custom block list management
- **Persistent Browsing History Storage** — Browse history is now saved and accessible from the browser menu
- **Bulk Download Actions** — Select multiple downloads for batch pause, resume, retry, or delete
- **Configurable Download Path** — Choose custom storage location for downloaded files
- **About Screen** — App information, developer credits, and links
- **Force-Dark Web Mode** — Force dark mode on all web content
- **Dynamic Browser Navigation Positioning** — Move browser navigation bar to top, bottom, or custom position

### ✨ Improvements

- **Redesigned Dashboard** — New analyze-first UX with dynamic platform detection and favicon support
- **Multi-Item Selection Mode** — Select multiple files in library for batch operations
- **Enhanced Download Monitoring** — Real-time progress, speed indicators, ETA, and health badges
- **Redesigned Tab Headers** — Improved typography and theme toggle in section headers
- **AMOLED Dark Mode** — True black theme for OLED displays
- **13 Accent Colors** — Customizable accent color picker with persistence
- **Dynamic Greeting** — Personalized welcome message on dashboard
- **Improved Layout Spacing** — Consistent padding and content spacing across all screens
- **Bottom Navigation Bar** — Refined appearance with collapsible browser navigation

### ⚡ Performance

- **Multi-Threaded Download Engine** — Segmented (chunked) downloads with adaptive threading
- **Automatic File Extension & MIME Type Resolution** — Smart media type detection
- **Media Scan on Completion** — Automatic media store notification after download
- **WorkManager Background Checks** — Periodic file integrity and connection health monitoring

### 🐞 Bug Fixes

- **Download State Management** — Fixed download queue state inconsistencies
- **Media Scanner Trigger** — Fixed media scan not firing on file completion
- **Default Download Path** — Fixed fallback path resolution

### 🛠 Refactoring

- **Unified VideoExtractor** — Replaced platform-specific extractors with a generic multi-strategy engine supporting 20+ platforms
- **Removed Dedicated Login Activities** — Consolidated Instagram/Facebook authentication into WebView-based cookie management
- **Browser Toolbar UI** — Refactored layout for better spacing and responsive design
- **Screen Padding** — Standardized padding across all screens

### 📦 Dependencies

- AGP 9.3.0
- Kotlin 2.2.10
- Gradle 9.6.1
- Compose BOM 2024.09.00
- Room 2.7.0
- OkHttp 4.10.0
- Retrofit 2.12.0
- Moshi 1.15.2
- WorkManager 2.9.1

### 📱 Android

- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36 (Android 16)
- **Compile SDK:** 36
- **Package:** `com.aistudio.videodownloader.pxqtrv`

---

## [1.0.0] — 2026-07-12

### 🚀 Initial Release

- First public release of NexLoad
- In-App Browser with WebView, HTTPS-only toggle, tracker blocking
- Multi-platform video downloader (TikTok, Instagram, Facebook, Twitter/X, and more)
- Download queue management with progress tracking
- Private vault with PIN protection
- File library with category filtering
- Material 3 design with customizable themes

[1.1.0]: https://github.com/abir2afridi/NexLoad/releases/tag/v1.1.0
[1.0.0]: https://github.com/abir2afridi/NexLoad/releases/tag/v1.0.0
