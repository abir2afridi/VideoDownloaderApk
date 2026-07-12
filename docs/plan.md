# MASTER PROMPT (EXHAUSTIVE, ZERO-PLACEHOLDER) — "NexDown"
### Production-Ready Android Video Downloader — Full File/Class-Level Specification

> Use this prompt in Cursor/Windsurf/Cline. Generate module-by-module in the order given in Section 14. Every file listed below must be fully implemented — no `TODO`, no stub bodies, no placeholder comments.

---

## 0. COMPLIANCE FRAME (STRUCTURAL, NOT OPTIONAL)

- No DRM circumvention (Widevine/FairPlay/PlayReady) — ever, under any feature.
- No extraction from platforms whose ToS forbid downloading (YouTube, Instagram, TikTok, Facebook, Netflix, Spotify, etc.) unless via their official public API with explicit permission.
- Supported sources only: direct public URLs (mp4/mp3/webm/mkv), open HLS (`.m3u8`) and DASH (`.mpd`) manifests that are unauthenticated and unencrypted, user-owned cloud/CDN links, Creative Commons/open media.
- Every download request passes through `SourceValidator` (Section 5) before the engine touches it. This is enforced in code, not just policy — `DownloadEngine.enqueue()` must call `sourceValidator.validate(url)` and throw `UnsupportedSourceException` on failure, with no override path.
- In-app disclaimer screen (`OnboardingLegalScreen.kt`) shown once, ToS acceptance stored in `UserPrefsDataStore`.

This section governs every module below. If a requested feature would require bypassing it, the correct implementation is to surface a clear "not supported" state to the user, not to weaken the validator.

---

## 1. TECH STACK & VERSIONS

| Layer | Choice |
|---|---|
| Language | Kotlin 2.x, explicit API mode on library modules |
| UI | Jetpack Compose (BOM latest stable), Material3 |
| DI | Hilt |
| Async | Coroutines + Flow + StateFlow/SharedFlow |
| Local DB | Room + SQLCipher (`net.zetetic:android-database-sqlcipher`) |
| Prefs | Jetpack DataStore (Preferences), EncryptedFile/MasterKey for secrets |
| Background | WorkManager (CoroutineWorker, constraint-driven) |
| Media | Media3 (ExoPlayer, Transformer, session) |
| Networking | OkHttp (raw range requests) + Retrofit (JSON metadata endpoints) |
| Image | Coil3 |
| Testing | JUnit5, MockK, Turbine, Compose UI Test, Robolectric |
| Build | Gradle Version Catalog, convention plugins in `build-logic` |
| Min/Target SDK | 26 / latest stable |

---

## 2. GRADLE MODULE TREE (EXACT)

```
settings.gradle.kts includes:
:app
:core:common
:core:ui
:core:network
:core:database
:core:datastore
:core:analytics
:core:testing
:domain
:data:download-engine
:data:source-validators
:data:media-repository
:data:file-repository
:feature:browser
:feature:downloads
:feature:player
:feature:file-manager
:feature:vault
:feature:settings
:feature:onboarding
:build-logic:convention
```

`build-logic/convention` provides plugins: `nexdown.android.application`, `nexdown.android.library`, `nexdown.android.library.compose`, `nexdown.android.hilt`, `nexdown.android.room`, `nexdown.android.feature` (applies library+compose+hilt together for feature modules).

---

## 3. DOMAIN MODULE (`:domain`) — Pure Kotlin, no Android imports

**`model/`**
- `MediaSource.kt` — sealed class: `DirectFile`, `HlsStream(masterPlaylistUrl, variants: List<HlsVariant>)`, `DashStream(manifestUrl, adaptationSets: List<DashAdaptationSet>)`
- `HlsVariant.kt` — `data class(bandwidth: Int, resolution: String?, codecs: String?, uri: String)`
- `DashAdaptationSet.kt` — `data class(mimeType: String, representations: List<DashRepresentation>)`
- `DownloadItem.kt` — `data class(id: String, sourceUrl: String, mediaType: MediaType, status: DownloadStatus, totalBytes: Long, downloadedBytes: Long, chunkCount: Int, priority: Int, createdAt: Instant, filePath: String, category: MediaCategory, checksumExpected: String?)`
- `DownloadStatus.kt` — sealed: `Queued`, `Connecting`, `Downloading(speedBps: Long, etaSeconds: Long)`, `Paused(reason: PauseReason)`, `Completed`, `Failed(error: DownloadError)`, `Cancelled`
- `DownloadError.kt` — sealed: `NetworkUnavailable`, `ServerError(code: Int)`, `ClientError(code: Int)`, `StorageFull`, `IntegrityMismatch`, `Unsupported(reason: String)`, `Unknown(message: String)`
- `PauseReason.kt` — enum: `USER`, `BATTERY_LOW`, `METERED_NETWORK`, `STORAGE_FULL`
- `MediaCategory.kt` — enum: `VIDEO`, `AUDIO`, `IMAGE`, `OTHER`
- `FileEntry.kt`, `VaultItem.kt`, `HistoryEntry.kt`, `Bookmark.kt`, `Tag.kt`

**`repository/` (interfaces only)**
- `DownloadRepository.kt` — `fun observeQueue(): Flow<List<DownloadItem>>`, `suspend fun enqueue(source: MediaSource): Result<DownloadItem>`, `suspend fun pause(id: String)`, `suspend fun resume(id: String)`, `suspend fun cancel(id: String)`, `suspend fun retry(id: String)`
- `FileRepository.kt`, `VaultRepository.kt`, `BrowserRepository.kt`, `SettingsRepository.kt`

**`usecase/`**
- `EnqueueDownloadUseCase.kt`, `ObserveDownloadQueueUseCase.kt`, `PauseDownloadUseCase.kt`, `ResumeDownloadUseCase.kt`, `RetryDownloadUseCase.kt`, `DetectDuplicateUseCase.kt`, `GenerateSmartFilenameUseCase.kt`, `CategorizeMediaUseCase.kt`, `EstimateNetworkQualityUseCase.kt`, `MoveToVaultUseCase.kt`, `SecureDeleteUseCase.kt`

---

## 4. CORE MODULES

**`:core:database`**
- `NexDownDatabase.kt` — Room DB, `@Database(entities = [DownloadEntity::class, ChunkEntity::class, HistoryEntity::class, BookmarkEntity::class, VaultItemEntity::class, TagEntity::class, FileTagCrossRef::class], version = 1)`
- `entity/DownloadEntity.kt`, `entity/ChunkEntity.kt` (id, downloadId, startByte, endByte, downloadedBytes, etag, status)
- `dao/DownloadDao.kt` — `@Query` for FTS-backed search (`FileSearchFts` virtual table for `:feature:file-manager`)
- `Converters.kt` — Instant/enum type converters
- `SqlCipherDatabaseFactory.kt` — passphrase sourced from `EncryptedFile`/Keystore, never hardcoded

**`:core:network`**
- `NexDownHttpClient.kt` — OkHttp singleton via Hilt `@Provides`, configured with connection pool, DoH/DoT interceptor hook
- `DohInterceptor.kt`, `DotDnsResolver.kt` — user-selectable resolver (Cloudflare/Quad9/custom), wired via `Dns` interface implementation
- `RangeRequestClient.kt` — wraps OkHttp for `Range:` header requests, detects `Accept-Ranges` support
- `NetworkQualityMonitor.kt` — rolling throughput sampler, exposes `Flow<NetworkQuality>`

**`:core:datastore`**
- `UserPrefsDataStore.kt`, `SecurePrefsStore.kt` (EncryptedFile-backed, for vault PIN hash, biometric flags)

**`:core:analytics`**
- `AnalyticsRecorder.kt` — no-op by default; `OptInAnalyticsRecorder.kt` implementation only active when user opts in, all events logged to local Room table, nothing leaves device

**`:core:ui`**
- `theme/Color.kt`, `theme/Type.kt`, `theme/Theme.kt` (dynamic color + AMOLED variant)
- `component/DownloadProgressCard.kt`, `component/NexButton.kt`, `component/EmptyState.kt`, `component/NexTopBar.kt`

---

## 5. `:data:source-validators` (BUILD THIS BEFORE THE ENGINE)

- `SourceValidator.kt` — interface: `suspend fun validate(url: String): ValidationResult`
- `ValidationResult.kt` — sealed: `Allowed(mediaType, isEncrypted: Boolean)`, `Blocked(reason: BlockReason)`
- `BlockReason.kt` — enum: `DRM_PROTECTED`, `KNOWN_TOS_RESTRICTED_DOMAIN`, `AUTH_REQUIRED`, `UNSUPPORTED_PROTOCOL`
- `RestrictedDomainList.kt` — bundled, updatable JSON list of domains whose ToS forbid third-party downloading (informational blocklist, ships with app, optional remote update via signed config)
- `ManifestInspector.kt` — parses HLS/DASH manifest headers for `#EXT-X-KEY` (HLS encryption tag) / DRM `ContentProtection` elements in DASH; if present → `Blocked(DRM_PROTECTED)`
- `DefaultSourceValidator.kt` — implementation wiring the above

---

## 6. `:data:download-engine`

- `DownloadEngine.kt` — public API: `enqueue`, `pause`, `resume`, `cancel`; internally delegates to `WorkManager`
- `worker/SegmentedDownloadWorker.kt` — `CoroutineWorker`, handles direct-file multi-range downloads; input data: downloadId; constraints: configurable network type, storage-not-low
- `worker/HlsDownloadWorker.kt` — parses playlist via `HlsPlaylistParser.kt`, downloads segments sequentially/parallel (configurable), concatenates via `Media3Muxer.kt`
- `worker/DashDownloadWorker.kt` — parses manifest via `DashManifestParser.kt`, downloads init+media segments per selected `DashRepresentation`
- `chunk/ChunkPlanner.kt` — decides chunk count from `NetworkQualityMonitor` reading + file size (e.g., 2–8 chunks, capped by config)
- `retry/RetryPolicy.kt` — exponential backoff with jitter: `baseDelayMs * 2^attempt + random(0, jitterMs)`, capped at `maxAttempts` (default 5), classifies error via `DownloadError` sealed hierarchy to decide retry vs fail-fast
- `integrity/IntegrityVerifier.kt` — compares final size to `Content-Length`, verifies `ETag` if present
- `queue/QueuePrioritizer.kt` — reorder strategy: user manual order (default) or "smart" (remaining-bytes-ascending), toggle in settings
- `battery/BatteryAwareScheduler.kt` — observes `BatteryManager` broadcast, pauses non-charging large downloads below threshold (`WorkManager` constraint: `setRequiresBatteryNotLow(true)` plus custom threshold logic for user-configured %)

---

## 7. `:feature:browser`

- `BrowserScreen.kt`, `BrowserViewModel.kt` (`BrowserUiState`: tabs, currentUrl, isIncognito, adBlockEnabled)
- `NexWebViewClient.kt` — overrides `shouldInterceptRequest`, feeds observed media URLs into `MediaSnifferBus.kt` (a `SharedFlow<DetectedMedia>`), each candidate passed through `SourceValidator` before surfacing
- `AdBlockEngine.kt` — EasyList-format rule matcher, bundled list + optional update
- `TrackerBlockList.kt`
- `ClipboardLinkWatcher.kt` — optional, off by default; observes clipboard via `ClipboardManager.OnPrimaryClipChangedListener` only while app foregrounded
- `ShareIntentActivity.kt` — handles `ACTION_SEND`/`ACTION_VIEW`, routes to `EnqueueDownloadUseCase`
- `FloatingDownloadButton.kt` — Compose overlay (`Box` + `AnimatedVisibility`) rendered on top of the WebView; appears whenever `MediaSnifferBus` emits a validated `DetectedMedia` item while the user is on that page. Tap → opens `DownloadOptionsBottomSheet.kt`.
- `DownloadOptionsBottomSheet.kt` — shows all detected renditions for the current page (resolution/bitrate list from `HlsVariant`/`DashRepresentation`, or single option for a direct file), file size estimate, format (video/audio-only), and an "Enqueue" button wired to `EnqueueDownloadUseCase`.
- `MediaSnifferBus.kt` — `SharedFlow<DetectedMedia>`; `DetectedMedia = data class(pageUrl, sources: List<MediaSource>, thumbnailUrl: String?)`, only ever emits items that already passed `SourceValidator.validate()`.

**Important limitation to build in explicitly, not discover later:** this pattern only works for sites that serve media through plain, unauthenticated, unencrypted URLs or open `.m3u8`/`.mpd` manifests — the same boundary as Section 0. Major platforms (YouTube, Instagram, TikTok, Facebook) deliberately serve tokenized, expiring, or DRM-wrapped URLs through internal APIs specifically to prevent this exact button from working, and reverse-engineering those internal APIs to make the button "work everywhere" is the ToS-violating extraction this prompt excludes. Design `FloatingDownloadButton` to simply stay hidden on pages where `MediaSnifferBus` has nothing validated to show — a silent "no button" is the correct, honest behavior on those sites, not a bug to fix.

---

## 8. `:feature:downloads`

- `DownloadsScreen.kt`, `DownloadsViewModel.kt` (`DownloadsUiState`: activeItems, queuedItems, completedItems, filter)
- `DownloadDetailSheet.kt` — bottom sheet: speed graph, chunk visualization, pause/resume/cancel/retry actions
- `DownloadNotificationManager.kt` — per-download progress notification with actions (pause/resume/cancel), grouped summary notification
- `DownloadWidgetProvider.kt` — home screen glance widget (Glance API) showing active download count/progress
- `QuickSettingsTileService.kt` — toggles Wi-Fi-only mode

---

## 9. `:feature:player`

- `PlayerScreen.kt`, `PlayerViewModel.kt`
- `NexDownPlayerService.kt` — Media3 `MediaSessionService` for background audio + PIP
- `SubtitleTrackSelector.kt` — only surfaces subtitle tracks embedded in the downloaded file/manifest
- `GestureController.kt` — seek/brightness/volume swipe gestures
- `CastButtonProvider.kt` — optional Chromecast module (`feature/player/cast/`)

---

## 10. `:feature:file-manager`

- `FileManagerScreen.kt`, `FileManagerViewModel.kt`
- `FileOperationsUseCases.kt` — rename/move/copy/delete/compress (via `java.util.zip`), all wrapped in `Result`
- `FileSearchFtsDao.kt` — Room FTS4 search
- `TagManagerScreen.kt`

---

## 11. `:feature:vault`

- `VaultUnlockScreen.kt` — `BiometricPrompt` + PIN fallback (`VaultAuthViewModel.kt`)
- `VaultRepositoryImpl.kt` — moves file bytes into app-private `EncryptedFile`, removes public `MediaStore` entry, `SecureDeleteUseCase` overwrites original before removal
- `VaultGridScreen.kt`

---

## 12. `:feature:settings` / `:feature:onboarding`

- `SettingsScreen.kt` with sub-screens: `GeneralSettings.kt`, `DownloadSettings.kt` (filename template editor, default folder per category), `NetworkSettings.kt` (DoH/DoT picker, Wi-Fi-only), `PrivacySettings.kt` (dashboard: permission log, contacted-domains log), `AppearanceSettings.kt` (theme/accent/AMOLED/font-scale), `BackupRestoreScreen.kt` (encrypted local JSON export/import), `DeveloperOptionsScreen.kt`
- `OnboardingLegalScreen.kt` — ToS/disclaimer acceptance (required, Section 0)
- `OnboardingPermissionsScreen.kt` — rationale per permission before requesting

---

## 13. TESTING DELIVERABLES (must ship alongside code, not after)

- `data/download-engine/src/test/` — `RetryPolicyTest.kt`, `ChunkPlannerTest.kt`, `HlsPlaylistParserTest.kt`, `DashManifestParserTest.kt`
- `data/source-validators/src/test/` — `DefaultSourceValidatorTest.kt` (covers DRM-tag detection, restricted-domain blocking)
- `domain/src/test/` — use-case tests with fake repositories
- `feature/downloads/src/androidTest/` — Compose UI test: enqueue → pause → resume → complete flow
- `core/database/src/androidTest/` — Room migration test scaffold (even at v1, include the test harness for future migrations)

---

## 14. GENERATION ORDER (follow exactly; do not skip ahead)

1. `build-logic/convention` + root `settings.gradle.kts` + version catalog
2. `core:common`, `core:network`, `core:database`, `core:datastore`
3. `domain` (all models, repository interfaces, use-cases)
4. `data:source-validators` (full implementation + tests)
5. `data:download-engine` (full implementation + tests)
6. `data:media-repository`, `data:file-repository`
7. `feature:downloads` (screen + WorkManager wiring + notifications)
8. `feature:browser`
9. `feature:player`, `feature:file-manager`, `feature:vault`
10. `feature:settings`, `feature:onboarding`
11. `app` module — nav graph (`NexDownNavHost.kt`), `MainActivity.kt`, Hilt `@HiltAndroidApp` Application class, final wiring

At each numbered step, generate every file listed for that module completely before moving to the next number.