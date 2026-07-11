package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.BookmarkEntity
import com.example.data.database.DownloadEntity
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

data class DetectedMedia(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.downloadDao()

    // Public downloads flow
    val publicDownloads: StateFlow<List<DownloadEntity>> = dao.getPublicDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Private vault downloads flow
    val privateDownloads: StateFlow<List<DownloadEntity>> = dao.getPrivateDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookmarks flow
    val bookmarks: StateFlow<List<BookmarkEntity>> = dao.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Detected media on current web page
    private val _detectedMediaList = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMediaList: StateFlow<List<DetectedMedia>> = _detectedMediaList.asStateFlow()

    // Browser state
    val currentWebUrl = MutableStateFlow("https://google.com")
    val isIncognito = MutableStateFlow(false)

    // Settings & Security state
    val isTrackerBlocking = MutableStateFlow(true)
    val isHttpsOnly = MutableStateFlow(true)
    val isWifiOnly = MutableStateFlow(false)
    val isAmoledMode = MutableStateFlow(false)
    val maxActiveDownloads = MutableStateFlow(3)
    val selectedAccentColor = MutableStateFlow("Bento") // Theme color option
    val selectedThemeMode = MutableStateFlow("System") // "System", "Light", "Dark"

    // Biometric/PIN vault lock
    val isVaultLocked = MutableStateFlow(true)
    val vaultPin = MutableStateFlow<String?>(null)
    val pinHint = MutableStateFlow<String?>(null)

    init {
        // Load secure vault PIN if set from SharedPrefs
        val prefs = application.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        vaultPin.value = prefs.getString("pin", null)
        pinHint.value = prefs.getString("hint", null)
        
        // Start WorkManager background integrity and connection checks
        scheduleIntegrityChecks()
    }

    fun scheduleIntegrityChecks() {
        try {
            val workManager = WorkManager.getInstance(getApplication())
            
            // 1. Enqueue periodic background check (every 15 minutes)
            val periodicRequest = PeriodicWorkRequestBuilder<DownloadIntegrityWorker>(15, TimeUnit.MINUTES)
                .addTag("DownloadIntegrityPeriodic")
                .build()
                
            workManager.enqueueUniquePeriodicWork(
                "DownloadIntegrityPeriodicWork",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            
            // 2. Run a one-time check immediately on startup
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
            Log.d(TAG, "Enqueued immediate DownloadIntegrityWorker run")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run immediate integrity check", e)
        }
    }

    /**
     * Clear scanned list when moving to a new webpage
     */
    fun clearDetectedMedia() {
        _detectedMediaList.value = emptyList()
    }

    /**
     * Detects media from web page bridge
     */
    fun addDetectedMedia(url: String, title: String) {
        val currentList = _detectedMediaList.value
        if (currentList.any { it.url == url }) return // skip duplicate urls

        val newMedia = DetectedMedia(url, title)
        _detectedMediaList.value = currentList + newMedia
    }

    /**
     * Add and queue a new download
     */
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
            
            // Clean filename initially
            var filename = MediaUtils.cleanFilename(url, null, extension)
            var category = if (isAudioOnly) "Audio" else "Video"

            // Target storage directory
            val targetDir = if (isPrivate) {
                File(getApplication<Application>().filesDir, "vault")
            } else {
                File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SmartDownloader")
            }
            targetDir.mkdirs()

            val filepath = File(targetDir, filename).absolutePath

            // Create Entity
            val download = DownloadEntity(
                url = url,
                title = suggestedTitle,
                filename = filename,
                filepath = filepath,
                mimeType = if (isAudioOnly) "audio/mpeg" else "video/mp4",
                category = category,
                status = "QUEUED",
                totalBytes = 0L,
                downloadedBytes = 0L,
                isPrivate = isPrivate,
                threads = threads,
                quality = quality
            )

            val downloadId = dao.insertDownload(download).toInt()
            
            // Trigger background download immediately
            DownloadEngine.startDownload(getApplication(), downloadId)
        }
    }

    /**
     * Pause download
     */
    fun pauseDownload(downloadId: Int) {
        DownloadEngine.pauseDownload(getApplication(), downloadId)
    }

    /**
     * Resume download
     */
    fun resumeDownload(downloadId: Int) {
        DownloadEngine.startDownload(getApplication(), downloadId)
    }

    /**
     * Delete download and file
     */
    fun deleteDownload(downloadId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = dao.getDownloadById(downloadId)
            if (download != null) {
                DownloadEngine.cancelDownload(getApplication(), downloadId)
                dao.deleteDownload(download)
            }
        }
    }

    /**
     * Favorites/Bookmarks management
     */
    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentBookmarks = bookmarks.value
            val existing = currentBookmarks.find { it.url == url }
            if (existing != null) {
                dao.deleteBookmark(existing)
            } else {
                dao.insertBookmark(BookmarkEntity(url = url, title = title))
            }
        }
    }

    /**
     * Check if active URL is favorited
     */
    fun isUrlBookmarked(url: String): Boolean {
        return bookmarks.value.any { it.url == url }
    }

    /**
     * Vault Security PIN Management
     */
    fun setVaultPin(pin: String, hint: String) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("pin", pin).putString("hint", hint).apply()
            vaultPin.value = pin
            pinHint.value = hint
            isVaultLocked.value = false
        }
    }

    fun unlockVault(pin: String): Boolean {
        return if (vaultPin.value == pin) {
            isVaultLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockVault() {
        isVaultLocked.value = true
    }

    fun isVaultConfigured(): Boolean {
        return vaultPin.value != null
    }

    fun resetVault() {
        val prefs = getApplication<Application>().getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        vaultPin.value = null
        pinHint.value = null
        isVaultLocked.value = true
    }
}
