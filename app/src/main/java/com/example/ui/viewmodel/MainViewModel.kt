package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.BookmarkEntity
import com.example.data.database.DownloadEntity
import com.example.data.database.HistoryEntity
import com.example.data.download.DownloadEngine
import com.example.data.download.MediaUtils
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import com.example.data.download.DownloadIntegrityWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import org.json.JSONArray

data class DetectedMedia(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TabData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "https://google.com",
    val isIncognito: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.downloadDao()
    private val settingsPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val browserPrefs = application.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    // ─── Downloads ───────────────────────────────────────────────────────────
    val publicDownloads: StateFlow<List<DownloadEntity>> = dao.getPublicDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privateDownloads: StateFlow<List<DownloadEntity>> = dao.getPrivateDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Bookmarks ───────────────────────────────────────────────────────────
    val bookmarks: StateFlow<List<BookmarkEntity>> = dao.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── History ─────────────────────────────────────────────────────────────
    val browsingHistory: StateFlow<List<HistoryEntity>> = dao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Detected Media ──────────────────────────────────────────────────────
    private val _detectedMediaList = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMediaList: StateFlow<List<DetectedMedia>> = _detectedMediaList.asStateFlow()

    // ─── Tab Gallery ─────────────────────────────────────────────────────────
    private val _initialTab = TabData()
    val tabs = MutableStateFlow(listOf(_initialTab))
    val activeTabId = MutableStateFlow<String?>(_initialTab.id)

    // Browser state (synced from active tab)
    val currentWebUrl = MutableStateFlow("https://google.com")
    val isIncognito = MutableStateFlow(false)

    // ─── App Settings (Persisted in app_settings) ────────────────────────────
    val isTrackerBlocking = MutableStateFlow(settingsPrefs.getBoolean("tracker_blocking", true))
    val isHttpsOnly = MutableStateFlow(settingsPrefs.getBoolean("https_only", true))
    val isWifiOnly = MutableStateFlow(settingsPrefs.getBoolean("wifi_only", false))
    val isAmoledMode = MutableStateFlow(settingsPrefs.getBoolean("amoled_mode", false))
    val maxActiveDownloads = MutableStateFlow(settingsPrefs.getInt("max_downloads", 3))
    val selectedAccentColor = MutableStateFlow(settingsPrefs.getString("accent_color", "Bento") ?: "Bento")
    val selectedThemeMode = MutableStateFlow(settingsPrefs.getString("theme_mode", "System") ?: "System")
    val browserTogglePosition = MutableStateFlow(settingsPrefs.getString("browser_toggle_pos", "Bottom Center") ?: "Bottom Center")
    val isForceDarkWeb = MutableStateFlow(settingsPrefs.getBoolean("force_dark_web", false))
    val downloadFolderPath = MutableStateFlow(
        settingsPrefs.getString("download_path", null)
            ?: defaultDownloadPath(application)
    )

    // ─── Time Settings ───────────────────────────────────────────────────────
    val isTime24Hour = MutableStateFlow(settingsPrefs.getBoolean("time_24h", true))
    val hourColor = MutableStateFlow(settingsPrefs.getString("hour_color", "Default") ?: "Default")
    val minuteColor = MutableStateFlow(settingsPrefs.getString("minute_color", "Default") ?: "Default")
    val secondColor = MutableStateFlow(settingsPrefs.getString("second_color", "Default") ?: "Default")

    // ─── Browser Settings (Persisted in browser_settings) ───────────────────

    // Privacy & Blocking
    val isAdBlocking = MutableStateFlow(browserPrefs.getBoolean("ad_blocking", true))
    /** Cookie mode: "All" = accept all | "NoThirdParty" = block 3rd party | "None" = block all */
    val cookieMode = MutableStateFlow(browserPrefs.getString("cookie_mode", "NoThirdParty") ?: "NoThirdParty")
    val isSecureDns = MutableStateFlow(browserPrefs.getBoolean("secure_dns", false))
    /** Secure DNS provider: "Cloudflare" | "Google" | "AdGuard" | "NextDNS" */
    val secureDnsProvider = MutableStateFlow(browserPrefs.getString("secure_dns_provider", "Cloudflare") ?: "Cloudflare")

    // Clear Data on Exit
    val clearDataOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_on_exit", false))
    val clearHistoryOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_history_exit", true))
    val clearTabsOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_tabs_exit", false))
    val clearSearchOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_search_exit", true))
    val clearCookiesOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_cookies_exit", true))
    val clearCacheOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_cache_exit", false))
    val clearAutofillOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_autofill_exit", false))
    val clearDownloadsOnExit = MutableStateFlow(browserPrefs.getBoolean("clear_downloads_exit", false))

    // Content Settings
    /** Text zoom percent: 50..200, default 100 */
    val textSizePercent = MutableStateFlow(browserPrefs.getInt("text_size", 100))
    val isTextWrap = MutableStateFlow(browserPrefs.getBoolean("text_wrap", true))
    val isBlockPopups = MutableStateFlow(browserPrefs.getBoolean("block_popups", true))
    /** User agent: "Mobile" | "Desktop" */
    val userAgentMode = MutableStateFlow(browserPrefs.getString("user_agent", "Mobile") ?: "Mobile")
    val isDataSaving = MutableStateFlow(browserPrefs.getBoolean("data_saving", false))

    // Search Settings
    /** Default engine: "Google" | "Bing" | "DuckDuckGo" | "Yahoo" | "Brave" | "Ecosia" */
    val defaultSearchEngine = MutableStateFlow(browserPrefs.getString("search_engine", "Google") ?: "Google")
    val isTrendingSearches = MutableStateFlow(browserPrefs.getBoolean("trending_searches", true))
    val isRecentSearches = MutableStateFlow(browserPrefs.getBoolean("recent_searches", true))
    val isSuggestedSites = MutableStateFlow(browserPrefs.getBoolean("suggested_sites", true))

    // Homepage Settings
    val isSpeedDialEnabled = MutableStateFlow(browserPrefs.getBoolean("speed_dial", true))
    val isLargeIcons = MutableStateFlow(browserPrefs.getBoolean("large_icons", false))
    val isSuggestedSitesHome = MutableStateFlow(browserPrefs.getBoolean("suggested_sites_home", true))
    val isNewsEnabled = MutableStateFlow(browserPrefs.getBoolean("news_enabled", true))

    // Autofill
    val isAutofillEnabled = MutableStateFlow(browserPrefs.getBoolean("autofill_enabled", true))
    val isAutofillAddress = MutableStateFlow(browserPrefs.getBoolean("autofill_address", true))
    val isAutofillCards = MutableStateFlow(browserPrefs.getBoolean("autofill_cards", false))
    val isAutofillContacts = MutableStateFlow(browserPrefs.getBoolean("autofill_contacts", true))

    // Site Permissions (default policy) – per-site stored as JSON in browserPrefs
    /** Location permission default: "Ask" | "Allowed" | "Denied" */
    val locationPermission = MutableStateFlow(browserPrefs.getString("perm_location", "Ask") ?: "Ask")
    /** Notification permission default: "Ask" | "Allowed" | "Denied" */
    val notificationPermission = MutableStateFlow(browserPrefs.getString("perm_notification", "Ask") ?: "Ask")
    /** Microphone permission default: "Ask" | "Allowed" | "Denied" */
    val microphonePermission = MutableStateFlow(browserPrefs.getString("perm_microphone", "Ask") ?: "Ask")
    /** External apps: "Ask" | "Always" | "Never" */
    val externalAppsPolicy = MutableStateFlow(browserPrefs.getString("perm_external_apps", "Ask") ?: "Ask")
    /** Protected content: "Ask" | "Allowed" | "Denied" */
    val protectedContentPolicy = MutableStateFlow(browserPrefs.getString("perm_protected_content", "Allowed") ?: "Allowed")
    val isAutoDownload = MutableStateFlow(browserPrefs.getBoolean("auto_download", false))
    /** Crypto wallet: "Ask" | "Enabled" | "Disabled" */
    val cryptoWalletPolicy = MutableStateFlow(browserPrefs.getString("crypto_wallet", "Ask") ?: "Ask")

    // Clear Data on Exit – confirmation dialog
    val showClearDataConfirmation = MutableStateFlow(browserPrefs.getBoolean("clear_data_confirm", false))

    // Web3 Network
    val isWeb3Enabled = MutableStateFlow(browserPrefs.getBoolean("web3_enabled", false))
    /** Web3 network: "Ethereum Mainnet" | "Polygon" | "BSC" | "Arbitrum" | "Optimism" | "Avalanche" */
    val web3Network = MutableStateFlow(browserPrefs.getString("web3_network", "Ethereum Mainnet") ?: "Ethereum Mainnet")

    // Per-site notification allowlist (JSON array of origin strings)
    private val _allowedNotificationSites = MutableStateFlow(
        browserPrefs.getString("notif_sites_json", "[]")?.let { json ->
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        } ?: emptyList()
    )
    val allowedNotificationSites: StateFlow<List<String>> = _allowedNotificationSites.asStateFlow()

    // Vault
    val isVaultLocked = MutableStateFlow(true)
    val vaultPin = MutableStateFlow<String?>(null)
    val pinHint = MutableStateFlow<String?>(null)
    val isBiometricAvailable = MutableStateFlow(false)
    val isBiometricEnabled = MutableStateFlow(false)
    val vaultDir: File

    // ─────────────────────────────────────────────────────────────────────────
    init {
        vaultDir = File(application.filesDir, "vault").also { it.mkdirs() }
        val vaultPrefs = application.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        vaultPin.value = vaultPrefs.getString("pin", null)
        pinHint.value = vaultPrefs.getString("hint", null)
        isBiometricEnabled.value = vaultPrefs.getBoolean("biometric_enabled", false)
        checkBiometricAvailability(application)

        // Persist app_settings on change
        persistFlow(isTrackerBlocking)    { putBoolean("tracker_blocking", it) }
        persistFlow(isHttpsOnly)          { putBoolean("https_only", it) }
        persistFlow(isWifiOnly)           { putBoolean("wifi_only", it) }
        persistFlow(isAmoledMode)         { putBoolean("amoled_mode", it) }
        persistFlow(maxActiveDownloads)   { putInt("max_downloads", it) }
        persistFlow(selectedAccentColor)  { putString("accent_color", it) }
        persistFlow(selectedThemeMode)    { putString("theme_mode", it) }
        persistFlow(browserTogglePosition){ putString("browser_toggle_pos", it) }
        persistFlow(isForceDarkWeb)       { putBoolean("force_dark_web", it) }
        persistFlow(downloadFolderPath)   { putString("download_path", it) }

        // Persist time settings
        persistFlow(isTime24Hour)         { putBoolean("time_24h", it) }
        persistFlow(hourColor)            { putString("hour_color", it) }
        persistFlow(minuteColor)          { putString("minute_color", it) }
        persistFlow(secondColor)          { putString("second_color", it) }

        // Persist browser_settings on change
        persistBrowserFlow(isAdBlocking)            { putBoolean("ad_blocking", it) }
        persistBrowserFlow(cookieMode)              { putString("cookie_mode", it) }
        persistBrowserFlow(isSecureDns)             { putBoolean("secure_dns", it) }
        persistBrowserFlow(secureDnsProvider)       { putString("secure_dns_provider", it) }
        persistBrowserFlow(clearDataOnExit)         { putBoolean("clear_on_exit", it) }
        persistBrowserFlow(clearHistoryOnExit)      { putBoolean("clear_history_exit", it) }
        persistBrowserFlow(clearTabsOnExit)         { putBoolean("clear_tabs_exit", it) }
        persistBrowserFlow(clearSearchOnExit)       { putBoolean("clear_search_exit", it) }
        persistBrowserFlow(clearCookiesOnExit)      { putBoolean("clear_cookies_exit", it) }
        persistBrowserFlow(clearCacheOnExit)        { putBoolean("clear_cache_exit", it) }
        persistBrowserFlow(clearAutofillOnExit)     { putBoolean("clear_autofill_exit", it) }
        persistBrowserFlow(clearDownloadsOnExit)    { putBoolean("clear_downloads_exit", it) }
        persistBrowserFlow(textSizePercent)         { putInt("text_size", it) }
        persistBrowserFlow(isTextWrap)              { putBoolean("text_wrap", it) }
        persistBrowserFlow(isBlockPopups)           { putBoolean("block_popups", it) }
        persistBrowserFlow(userAgentMode)           { putString("user_agent", it) }
        persistBrowserFlow(isDataSaving)            { putBoolean("data_saving", it) }
        persistBrowserFlow(defaultSearchEngine)     { putString("search_engine", it) }
        persistBrowserFlow(isTrendingSearches)      { putBoolean("trending_searches", it) }
        persistBrowserFlow(isRecentSearches)        { putBoolean("recent_searches", it) }
        persistBrowserFlow(isSuggestedSites)        { putBoolean("suggested_sites", it) }
        persistBrowserFlow(isSpeedDialEnabled)      { putBoolean("speed_dial", it) }
        persistBrowserFlow(isLargeIcons)            { putBoolean("large_icons", it) }
        persistBrowserFlow(isSuggestedSitesHome)    { putBoolean("suggested_sites_home", it) }
        persistBrowserFlow(isNewsEnabled)           { putBoolean("news_enabled", it) }
        persistBrowserFlow(isAutofillEnabled)       { putBoolean("autofill_enabled", it) }
        persistBrowserFlow(isAutofillAddress)       { putBoolean("autofill_address", it) }
        persistBrowserFlow(isAutofillCards)         { putBoolean("autofill_cards", it) }
        persistBrowserFlow(isAutofillContacts)      { putBoolean("autofill_contacts", it) }
        persistBrowserFlow(locationPermission)      { putString("perm_location", it) }
        persistBrowserFlow(notificationPermission)  { putString("perm_notification", it) }
        persistBrowserFlow(microphonePermission)    { putString("perm_microphone", it) }
        persistBrowserFlow(externalAppsPolicy)      { putString("perm_external_apps", it) }
        persistBrowserFlow(protectedContentPolicy)  { putString("perm_protected_content", it) }
        persistBrowserFlow(isAutoDownload)          { putBoolean("auto_download", it) }
        persistBrowserFlow(cryptoWalletPolicy)      { putString("crypto_wallet", it) }
        persistBrowserFlow(showClearDataConfirmation){ putBoolean("clear_data_confirm", it) }
        persistBrowserFlow(isWeb3Enabled)           { putBoolean("web3_enabled", it) }
        persistBrowserFlow(web3Network)             { putString("web3_network", it) }

        scheduleIntegrityChecks()
    }

    // ─── Persist helpers ─────────────────────────────────────────────────────

    private inline fun <T> persistFlow(
        flow: StateFlow<T>,
        crossinline edit: android.content.SharedPreferences.Editor.(T) -> Unit
    ) {
        viewModelScope.launch {
            flow.collect { value ->
                settingsPrefs.edit().apply { edit(value) }.apply()
            }
        }
    }

    private inline fun <T> persistBrowserFlow(
        flow: StateFlow<T>,
        crossinline edit: android.content.SharedPreferences.Editor.(T) -> Unit
    ) {
        viewModelScope.launch {
            flow.collect { value ->
                browserPrefs.edit().apply { edit(value) }.apply()
            }
        }
    }

    // ─── WorkManager ─────────────────────────────────────────────────────────

    fun scheduleIntegrityChecks() {
        try {
            val workManager = WorkManager.getInstance(getApplication())
            val periodicRequest = PeriodicWorkRequestBuilder<DownloadIntegrityWorker>(15, TimeUnit.MINUTES)
                .addTag("DownloadIntegrityPeriodic")
                .build()
            workManager.enqueueUniquePeriodicWork(
                "DownloadIntegrityPeriodicWork",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            runImmediateIntegrityCheck()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager integrity checks", e)
        }
    }

    fun runImmediateIntegrityCheck() {
        try {
            val workManager = WorkManager.getInstance(getApplication())
            val immediateRequest = OneTimeWorkRequestBuilder<DownloadIntegrityWorker>()
                .addTag("DownloadIntegrityOneTime")
                .build()
            workManager.enqueue(immediateRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run immediate integrity check", e)
        }
    }

    // ─── Search Engine Helpers ────────────────────────────────────────────────

    /** Returns the search URL for the given engine and query */
    fun buildSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return when (defaultSearchEngine.value) {
            "Bing"       -> "https://www.bing.com/search?q=$encoded"
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
            "Yahoo"      -> "https://search.yahoo.com/search?p=$encoded"
            "Brave"      -> "https://search.brave.com/search?q=$encoded"
            "Ecosia"     -> "https://www.ecosia.org/search?q=$encoded"
            "Yandex"     -> "https://yandex.com/search/?text=$encoded"
            else         -> "https://www.google.com/search?q=$encoded"
        }
    }

    /** Returns the search engine home URL */
    fun searchEngineHomeUrl(): String = when (defaultSearchEngine.value) {
        "Bing"       -> "https://www.bing.com"
        "DuckDuckGo" -> "https://duckduckgo.com"
        "Yahoo"      -> "https://search.yahoo.com"
        "Brave"      -> "https://search.brave.com"
        "Ecosia"     -> "https://www.ecosia.org"
        "Yandex"     -> "https://yandex.com"
        else         -> "https://www.google.com"
    }

    // ─── Tab Gallery ─────────────────────────────────────────────────────────

    fun addTab(isIncognito: Boolean = false) {
        val newTab = TabData(isIncognito = isIncognito)
        tabs.value = tabs.value + newTab
        activeTabId.value = newTab.id
        currentWebUrl.value = newTab.url
        this.isIncognito.value = newTab.isIncognito
    }

    fun switchTab(tabId: String) {
        val tab = tabs.value.find { it.id == tabId } ?: return
        activeTabId.value = tabId
        currentWebUrl.value = tab.url
        isIncognito.value = tab.isIncognito
    }

    fun closeTab(tabId: String) {
        val currentTabs = tabs.value
        if (currentTabs.size <= 1) return
        val index = currentTabs.indexOfFirst { it.id == tabId }
        val newList = currentTabs.filter { it.id != tabId }
        tabs.value = newList
        if (tabId == activeTabId.value) {
            val newIndex = minOf(index, newList.size - 1)
            val newTab = newList[newIndex]
            activeTabId.value = newTab.id
            currentWebUrl.value = newTab.url
            isIncognito.value = newTab.isIncognito
        }
    }

    fun updateActiveTabUrl(url: String) {
        currentWebUrl.value = url
        activeTabId.value?.let { activeId ->
            tabs.value = tabs.value.map {
                if (it.id == activeId) it.copy(url = url) else it
            }
        }
    }

    fun updateActiveTabTitle(title: String) {
        activeTabId.value?.let { activeId ->
            tabs.value = tabs.value.map {
                if (it.id == activeId) it.copy(title = title) else it
            }
        }
    }

    fun toggleActiveTabIncognito() {
        val activeId = activeTabId.value ?: return
        val newState = !isIncognito.value
        isIncognito.value = newState
        tabs.value = tabs.value.map {
            if (it.id == activeId) it.copy(isIncognito = newState) else it
        }
    }

    // ─── History ─────────────────────────────────────────────────────────────

    fun addHistoryEntry(url: String, title: String) {
        if (isIncognito.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.insertHistory(HistoryEntity(url = url, title = title))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert history", e)
            }
        }
    }

    fun deleteHistoryEntry(entry: HistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteHistory(entry) }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearAllHistory() }
    }

    // ─── Clear Data on Exit ───────────────────────────────────────────────────

    /**
     * Called when app exits (from Application.onTerminate or lifecycle observer).
     * Clears browser data based on user preferences.
     */
    fun performClearDataOnExit(webView: android.webkit.WebView?) {
        if (!clearDataOnExit.value) return
        viewModelScope.launch(Dispatchers.IO) {
            if (clearHistoryOnExit.value) dao.clearAllHistory()
            if (clearTabsOnExit.value) {
                val firstTab = TabData()
                tabs.value = listOf(firstTab)
                activeTabId.value = firstTab.id
            }
        }
        webView?.let { wv ->
            if (clearCookiesOnExit.value) {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
            }
            if (clearCacheOnExit.value) wv.clearCache(true)
            if (clearAutofillOnExit.value) wv.clearFormData()
            wv.clearHistory()
        }
    }

    // ─── Media Detection ─────────────────────────────────────────────────────

    fun clearDetectedMedia() { _detectedMediaList.value = emptyList() }

    fun addDetectedMedia(url: String, title: String) {
        val currentList = _detectedMediaList.value
        if (currentList.any { it.url == url }) return
        _detectedMediaList.value = currentList + DetectedMedia(url, title)
    }

    // ─── Downloads ───────────────────────────────────────────────────────────

    fun addDownload(
        url: String,
        suggestedTitle: String,
        threads: Int = 4,
        isPrivate: Boolean = false,
        quality: String = "Auto",
        isAudioOnly: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val extension = if (isAudioOnly) "mp3" else "mp4"
            val filename = MediaUtils.cleanFilename(url, null, extension)
            val category = if (isAudioOnly) "Audio" else "Video"
            val targetDir = if (isPrivate) {
                File(getApplication<Application>().filesDir, "vault")
            } else {
                File(downloadFolderPath.value)
            }
            targetDir.mkdirs()
            val filepath = File(targetDir, filename).absolutePath
            val download = DownloadEntity(
                url = url, title = suggestedTitle, filename = filename,
                filepath = filepath,
                mimeType = if (isAudioOnly) "audio/mpeg" else "video/mp4",
                category = category, status = "QUEUED",
                totalBytes = 0L, downloadedBytes = 0L,
                isPrivate = isPrivate, threads = threads, quality = quality
            )
            val downloadId = dao.insertDownload(download).toInt()
            DownloadEngine.startDownload(getApplication(), downloadId)
        }
    }

    fun pauseDownload(downloadId: Int)  { DownloadEngine.pauseDownload(getApplication(), downloadId) }
    fun resumeDownload(downloadId: Int) { DownloadEngine.startDownload(getApplication(), downloadId) }

    fun deleteDownload(downloadId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val download = dao.getDownloadById(downloadId) ?: return@launch
                DownloadEngine.cancelDownload(getApplication(), downloadId)
                File(download.filepath).delete()
                dao.deleteDownload(download)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete download", e)
            }
        }
    }

    fun pauseAllDownloads() {
        publicDownloads.value.filter { it.status in listOf("DOWNLOADING", "QUEUED") }
            .forEach { pauseDownload(it.id) }
    }

    fun resumeAllDownloads() {
        publicDownloads.value.filter { it.status in listOf("PAUSED", "FAILED") }
            .forEach { resumeDownload(it.id) }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            publicDownloads.value.forEach { entity ->
                DownloadEngine.cancelDownload(getApplication(), entity.id)
                File(entity.filepath).delete()
                dao.deleteDownload(entity)
            }
        }
    }

    fun retryAllFailed() {
        publicDownloads.value.filter { it.status == "FAILED" }.forEach { resumeDownload(it.id) }
    }

    // ─── Bookmarks ───────────────────────────────────────────────────────────

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = bookmarks.value.find { it.url == url }
            if (existing != null) dao.deleteBookmark(existing)
            else dao.insertBookmark(BookmarkEntity(url = url, title = title))
        }
    }

    fun isUrlBookmarked(url: String) = bookmarks.value.any { it.url == url }

    // ─── Notification Site Allowlist ──────────────────────────────────────────

    fun addNotificationSite(origin: String) {
        val current = _allowedNotificationSites.value
        if (origin !in current) {
            val updated = current + origin
            _allowedNotificationSites.value = updated
            persistNotifSites(updated)
        }
    }

    fun removeNotificationSite(origin: String) {
        val updated = _allowedNotificationSites.value.filter { it != origin }
        _allowedNotificationSites.value = updated
        persistNotifSites(updated)
    }

    fun clearAllNotificationSites() {
        _allowedNotificationSites.value = emptyList()
        persistNotifSites(emptyList())
    }

    private fun persistNotifSites(sites: List<String>) {
        val json = JSONArray(sites).toString()
        browserPrefs.edit().putString("notif_sites_json", json).apply()
    }

    // ─── Vault ───────────────────────────────────────────────────────────────

    private fun checkBiometricAvailability(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val mgr = BiometricManager.from(context)
            isBiometricAvailable.value = when {
                mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> true
                mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
        }
    }

    fun setVaultPin(pin: String, hint: String, enableBiometric: Boolean) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("pin", pin).putString("hint", hint).putBoolean("biometric_enabled", enableBiometric).apply()
            vaultPin.value = pin
            pinHint.value = hint
            isBiometricEnabled.value = enableBiometric
            isVaultLocked.value = false
        }
    }

    fun unlockVault(pin: String): Boolean {
        return if (vaultPin.value == pin) { isVaultLocked.value = false; true } else false
    }

    fun lockVault() { isVaultLocked.value = true }
    fun isVaultConfigured() = vaultPin.value != null

    fun resetVault() {
        val prefs = getApplication<Application>().getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        vaultPin.value = null
        pinHint.value = null
        isBiometricEnabled.value = false
        isVaultLocked.value = true
    }

    fun importToVault(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = if (displayName.isNotBlank()) displayName
                    else uri.lastPathSegment?.substringAfterLast('/') ?: "imported_file"

                val targetFile = File(vaultDir, filename)
                var index = 1
                while (targetFile.exists()) {
                    val base = filename.substringBeforeLast('.')
                    val ext = filename.substringAfterLast('.', "")
                    val renamed = if (ext.isNotEmpty()) "${base}_($index).$ext" else "${base}_($index)"
                    val tmp = File(vaultDir, renamed)
                    if (!tmp.exists()) { tmp; break }
                    index++
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val category = when {
                    mimeType.startsWith("video") -> "Video"
                    mimeType.startsWith("audio") -> "Audio"
                    mimeType.startsWith("image") -> "Images"
                    else -> "Other"
                }

                val entity = DownloadEntity(
                    url = uri.toString(),
                    title = filename.substringBeforeLast('.'),
                    filename = filename,
                    filepath = targetFile.absolutePath,
                    mimeType = mimeType,
                    category = category,
                    status = "COMPLETED",
                    totalBytes = targetFile.length(),
                    downloadedBytes = targetFile.length(),
                    isPrivate = true,
                    integrityStatus = "OK"
                )
                dao.insertDownload(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import file to vault", e)
            }
        }
    }

    fun exportFromVault(downloadId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getDownloadById(downloadId) ?: return@launch
                val sourceFile = File(entity.filepath)
                if (!sourceFile.exists()) return@launch
                val targetDir = File(downloadFolderPath.value)
                targetDir.mkdirs()
                val targetFile = File(targetDir, entity.filename)
                var exportFile = targetFile
                var idx = 1
                while (exportFile.exists()) {
                    val base = entity.filename.substringBeforeLast('.')
                    val ext = entity.filename.substringAfterLast('.', "")
                    val renamed = if (ext.isNotEmpty()) "${base}_($idx).$ext" else "${base}_($idx)"
                    exportFile = File(targetDir, renamed)
                    idx++
                }
                sourceFile.copyTo(exportFile, overwrite = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export file from vault", e)
            }
        }
    }

    fun deleteVaultItem(downloadId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getDownloadById(downloadId) ?: return@launch
                DownloadEngine.cancelDownload(getApplication(), downloadId)
                File(entity.filepath).delete()
                dao.deleteDownloadById(downloadId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete vault item", e)
            }
        }
    }

    fun deleteMultipleVaultItems(ids: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> deleteVaultItem(id) }
        }
    }

    private fun defaultDownloadPath(app: Application): String {
        val appName = getAppName(app)
        return tryCreatePublicDownloadDir(app, appName)
            ?: tryCreateExternalFilesDir(app)
            ?: (app.filesDir.absolutePath + "/$appName")
    }

    private fun getAppName(app: Application): String {
        return try {
            app.packageManager.getApplicationLabel(app.applicationInfo).toString()
        } catch (e: Exception) {
            "NexLoad"
        }
    }

    private fun tryCreatePublicDownloadDir(app: Application, appName: String): String? {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(dir, appName)
            if (appDir.mkdirs() || appDir.exists()) {
                Log.d(TAG, "Download directory created: ${appDir.absolutePath}")
                appDir.absolutePath
            } else {
                Log.w(TAG, "Failed to create public download directory, will use fallback")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating public download directory", e)
            null
        }
    }

    private fun tryCreateExternalFilesDir(app: Application): String? {
        return try {
            val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir?.let {
                it.mkdirs()
                if (it.exists()) {
                    Log.d(TAG, "Fallback download directory: ${it.absolutePath}")
                    it.absolutePath
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback download directory", e)
            null
        }
    }

    fun tryMigrateToPublicStorage() {
        val app = getApplication<Application>()
        val currentPath = downloadFolderPath.value
        val appName = getAppName(app)

        val publicPath = tryCreatePublicDownloadDir(app, appName)
        if (publicPath != null && currentPath != publicPath) {
            Log.d(TAG, "Migrating download path from $currentPath to $publicPath")
            downloadFolderPath.value = publicPath
        }
    }
}
