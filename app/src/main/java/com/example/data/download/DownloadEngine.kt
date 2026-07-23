package com.example.data.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.webkit.MimeTypeMap
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.DownloadEntity
import com.yausername.youtubedl_android.DownloadProgressCallback
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object DownloadEngine {
    private const val TAG = "DownloadEngine"
    @Volatile
    var lastPageUrl: String = ""
    private val activeJobs = ConcurrentHashMap<Int, Job>()

    private fun parseCustomHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.getString(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Uncaught coroutine exception in DownloadEngine", t)
    })

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(TikTokCookieStore.cookieJar)
            .addInterceptor { chain ->
                val originalUrl = chain.request().url.toString()
                val requestBuilder = chain.request().newBuilder()
                    .header("Accept", "*/*")
                    .header("Connection", "keep-alive")
                val isFacebookCdn = originalUrl.contains("fbcdn") || originalUrl.contains("scontent")
                if (isFacebookCdn) {
                    // =========================================================================
                    // FACEBOOK 403 FIX — DO NOT DELETE OR MODIFY THIS BLOCK
                    // =========================================================================
                    // PROBLEM: Facebook CDN (fbcdn.net / scontent) returns HTTP 403 Forbidden
                    // when downloading video files. This happens because Facebook's CDN
                    // rate-limits requests that use a browser User-Agent string.
                    //
                    // ROOT CAUSE: Facebook checks the User-Agent header on CDN requests.
                    // Browser UAs (Chrome, Firefox etc.) trigger 403 because Facebook treats
                    // them as potential scrapers/bots. Only the official Facebook crawler
                    // UA "facebookexternalhit/1.1" is allowed without rate-limiting.
                    //
                    // SOLUTION (from yt-dlp issue #8197 — the authoritative fix):
                    // 1. Use User-Agent: facebookexternalhit/1.1 (NOT a browser UA)
                    // 2. Set Referer: https://www.facebook.com/ (required by CDN)
                    // 3. Inject Facebook session cookies from FacebookCookieStore
                    //
                    // WHY OTHER APPS WORK: Apps like SnapSave/FDown use server-side proxies
                    // that send the correct UA + cookies. We achieve the same by using the
                    // crawler UA directly from the Android client.
                    //
                    // RULES:
                    // - NEVER change this UA to a browser string for fbcdn/scontent URLs
                    // - NEVER remove the Referer header for Facebook CDN requests
                    // - NEVER remove the Cookie injection for Facebook requests
                    // - If 403 returns, first check if cookies are fresh (re-login via Settings)
                    // =========================================================================
                    requestBuilder.header("User-Agent", "facebookexternalhit/1.1")
                    requestBuilder.header("Referer", "https://www.facebook.com/")
                    val fbCookies = FacebookCookieStore.getCookies()
                    if (fbCookies.isNotBlank()) {
                        requestBuilder.header("Cookie", fbCookies)
                    }
                } else if (originalUrl.contains("tiktokcdn.com") || originalUrl.contains("tiktok.com") ||
                    originalUrl.contains("byteoversea.com") || originalUrl.contains("ibyteimg.com") ||
                    originalUrl.contains("tiktokv.com") || originalUrl.contains("musically.com") ||
                    originalUrl.contains("tikwm.com") || originalUrl.contains("tikcdn.io")) {
                    requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    requestBuilder.header("Referer", "https://www.tiktok.com/")
                } else {
                    requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                }
                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    // Map to track progress speed calculations
    private val speedMap = ConcurrentHashMap<Int, Long>()

    fun startDownload(context: Context, downloadId: Int) {
        Log.d(TAG, "startDownload: downloadId=$downloadId activeJobs.size=${activeJobs.size}")
        if (activeJobs.containsKey(downloadId)) {
            Log.d(TAG, "Download $downloadId is already running")
            return
        }

        val job = scope.launch {
            try {
                startDownloadSuspend(context, downloadId)
            } catch (e: CancellationException) {
                Log.d(TAG, "Download $downloadId was cancelled/paused")
                pauseDownload(context, downloadId)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading $downloadId", e)
                pauseDownload(context, downloadId)
            } finally {
                activeJobs.remove(downloadId)
                speedMap.remove(downloadId)
            }
        }
        activeJobs[downloadId] = job
    }

    suspend fun startDownloadSuspend(context: Context, downloadId: Int) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()
        val download = dao.getDownloadById(downloadId) ?: return
        if (download.status == "COMPLETED") return
        val downloadingEntity = download.copy(status = "DOWNLOADING", errorMessage = null)
        dao.updateDownload(downloadingEntity)

        // For Facebook: re-extract CDN URL right before download (tokens expire fast)
        val isFbSource = download.sourceUrl?.let {
            it.contains("facebook.com") || it.contains("fb.watch") || it.contains("fb.com")
        } ?: false
        val isFbCdn = download.url?.let {
            it.contains("fbcdn") || it.contains("scontent")
        } ?: false

        val finalEntity = if ((isFbSource || isFbCdn) && !download.url.isNullOrBlank()) {
            val sourceForReextract = download.sourceUrl ?: download.url
            try {
                val freshUrl = extractFacebook(sourceForReextract)
                    ?.let { it.videoUrlNoWatermark ?: it.videoUrl }
                if (freshUrl != null && freshUrl != download.url) {
                    Log.d(TAG, "Facebook: got fresh CDN URL for download")
                    dao.updateDownload(downloadingEntity.copy(url = freshUrl))
                    downloadingEntity.copy(url = freshUrl)
                } else {
                    downloadingEntity
                }
            } catch (e: Exception) {
                Log.w(TAG, "Facebook re-extraction failed, using stored URL", e)
                downloadingEntity
            }
        } else {
            downloadingEntity
        }

        try {
            executeDownload(context, finalEntity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $downloadId", e)
            val currentDownload = dao.getDownloadById(downloadId)
            if (currentDownload != null && currentDownload.errorMessage == null) {
                dao.updateDownload(
                    currentDownload.copy(
                        status = "FAILED",
                        errorMessage = e.message ?: e.toString()
                    )
                )
            }
        }
    }

    fun pauseDownload(context: Context, downloadId: Int) {
        val job = activeJobs.remove(downloadId)
        if (job != null) {
            job.cancel()
            Log.d(TAG, "Download $downloadId job cancelled")
        }
        
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.downloadDao()
            val download = dao.getDownloadById(downloadId)
            if (download != null && (download.status == "DOWNLOADING" || download.status == "QUEUED")) {
                dao.updateDownload(download.copy(status = "PAUSED", speed = 0))
            }
        }
    }

    fun cancelDownload(context: Context, downloadId: Int) {
        pauseDownload(context, downloadId)
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.downloadDao()
            val download = dao.getDownloadById(downloadId)
            if (download != null) {
                // Delete actual local file
                val file = File(download.filepath)
                if (file.exists()) {
                    file.delete()
                }
                dao.deleteDownload(download)
            }
        }
    }

    private suspend fun executeDownload(context: Context, download: DownloadEntity) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()

        val file = File(download.filepath)
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            Log.e(TAG, "Failed to create download directory: ${parentDir.absolutePath}")
            dao.updateDownload(download.copy(status = "FAILED", errorMessage = "Cannot create download directory"))
            return@withContext
        }

        val sourceUrl = download.sourceUrl ?: ""
        val downloadUrl = download.url ?: ""
        val isYoutubeSource = sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be")
        val isFacebookSource = sourceUrl.contains("facebook.com") || sourceUrl.contains("fb.watch") || sourceUrl.contains("fb.com") ||
            downloadUrl.contains("fbcdn") || downloadUrl.contains("scontent")

        val hasDirectUrl = !download.url.isNullOrBlank()
        val hasHeaders = !download.customHeaders.isNullOrBlank()

        Log.d(TAG, "executeDownload: url='${download.url}' sourceUrl='$sourceUrl' platform=${if (isYoutubeSource) "youtube" else if (isFacebookSource) "facebook" else "other"} hasDirectUrl=$hasDirectUrl")

        // YouTube uses yt-dlp
        if (isYoutubeSource) {
            ytDlpDownload(context, download)
            return@withContext
        }

        // Facebook: if we have a direct CDN URL, download via OkHttp (already refreshed in startDownloadSuspend)
        // Only fall back to yt-dlp when extraction didn't find a direct URL
        if (isFacebookSource && !hasDirectUrl) {
            Log.d(TAG, "Facebook without direct URL, using yt-dlp fallback")
            ytDlpDownload(context, download)
            return@withContext
        }

        // For all other extractors that found a direct CDN URL: use OkHttp direct download
        // This includes TikTok, Instagram, Twitter, Reddit, Pinterest, etc.
        if (hasDirectUrl) {
            Log.d(TAG, "Using OkHttp direct download for ${download.sourceUrl ?: download.url}")
        } else {
            ytDlpDownload(context, download)
            return@withContext
        }

        // 1. Initial HEAD or quick GET request to inspect the headers
        val parsedHeaders = parseCustomHeaders(download.customHeaders)
        val checkRequestBuilder = Request.Builder()
            .url(download.url)
            .header("Range", "bytes=0-0")
        parsedHeaders.forEach { (k, v) -> checkRequestBuilder.header(k, v) }
        val checkRequest = checkRequestBuilder.build()

        var totalLength = download.totalBytes
        var acceptRanges = false
        var contentType: String? = null

        try {
            client.newCall(checkRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val contentRange = response.header("Content-Range")
                    val fullLenHeader = response.header("Content-Length")
                    contentType = response.header("Content-Type")
                    
                    acceptRanges = response.code == 206 || contentRange != null
                    
                    if (contentRange != null) {
                        val parts = contentRange.split("/")
                        if (parts.size == 2) {
                            totalLength = parts[1].toLongOrNull() ?: totalLength
                        }
                    } else if (fullLenHeader != null) {
                        totalLength = fullLenHeader.toLongOrNull() ?: totalLength
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed range check request, will proceed with single-thread fallback", e)
        }

        // Update filename/extension based on Content-Type
        val resolvedContentType = contentType
        var resolvedDownload = download
        if (!resolvedContentType.isNullOrBlank()) {
            val mimeExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedContentType)
            val category = MediaUtils.getCategoryFromMimeType(resolvedContentType)
            val resolvedExt = if (!mimeExt.isNullOrBlank()) mimeExt else "mp4"
            
            if (resolvedExt != download.filename.substringAfterLast('.', "mp4")) {
                val baseName = download.filename.substringBeforeLast('.', download.filename)
                val correctedName = "$baseName.$resolvedExt"
                val correctedPath = File(download.filepath).parent?.let { File(it, correctedName).absolutePath } ?: download.filepath
                resolvedDownload = download.copy(
                    filename = correctedName,
                    filepath = correctedPath,
                    mimeType = resolvedContentType,
                    category = category
                )
                dao.updateDownload(resolvedDownload)
            } else {
                // Update mimeType and category even if extension matches
                resolvedDownload = download.copy(mimeType = resolvedContentType, category = category)
                dao.updateDownload(resolvedDownload)
            }
        }

        // 2. Storage Check
        if (totalLength > 0) {
            val stat = StatFs(file.parentFile?.absolutePath ?: context.filesDir.absolutePath)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            if (bytesAvailable < totalLength) {
                dao.updateDownload(resolvedDownload.copy(status = "FAILED", errorMessage = "Insufficient storage space"))
                return@withContext
            }
        }

        val updatedDownload = resolvedDownload.copy(totalBytes = totalLength)
        dao.updateDownload(updatedDownload)

        // 3. Multi-thread or Single-thread download
        if (acceptRanges && totalLength > 2 * 1024 * 1024 && updatedDownload.threads > 1) {
            // Multi-thread Download
            executeMultiThreadDownload(context, updatedDownload, totalLength)
        } else {
            // Single-thread Download
            executeSingleThreadDownload(context, updatedDownload, totalLength)
        }
    }

    private suspend fun executeSingleThreadDownload(
        context: Context,
        download: DownloadEntity,
        totalLength: Long
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()
        val file = File(download.filepath)

        val existingBytes = if (file.exists()) file.length() else 0L
        
        val requestBuilder = Request.Builder().url(download.url)
        if (existingBytes > 0 && totalLength > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        parseCustomHeaders(download.customHeaders).forEach { (k, v) -> requestBuilder.header(k, v) }
        
        val request = requestBuilder.build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP unexpected code ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty response body")
            val isAppend = response.code == 206 || (existingBytes > 0 && totalLength == 0L)
            
            var raf: RandomAccessFile? = null
            var downloadedBytes = if (isAppend) existingBytes else 0L
            try {
                raf = RandomAccessFile(file, "rw")
                if (isAppend) {
                    raf.seek(existingBytes)
                } else {
                    raf.setLength(0) // clear
                }
                
                val inputStream = body.byteStream()
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                
                var lastUpdate = System.currentTimeMillis()
                var speedBytes = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive() // cooperatively handle cancel
                    raf.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    speedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 500) {
                        val elapsed = (now - lastUpdate) / 1000.0
                        val currentSpeed = (speedBytes / elapsed).toLong()
                        speedBytes = 0L
                        lastUpdate = now

                        val latest = dao.getDownloadById(download.id)
                        if (latest != null && (latest.status == "DOWNLOADING" || latest.status == "QUEUED")) {
                            dao.updateDownload(
                                latest.copy(
                                    status = "DOWNLOADING",
                                    totalBytes = totalLength,
                                    downloadedBytes = downloadedBytes,
                                    speed = currentSpeed
                                )
                            )
                        }
                    }
                }
            } finally {
                raf?.close()
            }

            // Scan the file so it appears in the gallery/file manager
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)

            // Completion
            dao.updateDownload(
                download.copy(
                    status = "COMPLETED",
                    totalBytes = totalLength,
                    downloadedBytes = totalLength.coerceAtLeast(downloadedBytes),
                    speed = 0
                )
            )
        }
    }

    private suspend fun executeMultiThreadDownload(
        context: Context,
        download: DownloadEntity,
        totalLength: Long
    ) = coroutineScope {
        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()
        val file = File(download.filepath)

        // Pre-allocate file size
        var rafSetup: RandomAccessFile? = null
        try {
            rafSetup = RandomAccessFile(file, "rw")
            rafSetup.setLength(totalLength)
        } finally {
            rafSetup?.close()
        }

        val numThreads = download.threads
        val chunkSize = totalLength / numThreads
        val downloadedAccumulator = AtomicLong(0L)
        val parsedHeaders = parseCustomHeaders(download.customHeaders)

        val speedTracker = AtomicLong(0L)
        val speedJob = launch {
            while (isActive) {
                delay(500)
                val currentSpeed = (speedTracker.getAndSet(0L) * 2) // Approximate for 500ms
                val currentProgress = downloadedAccumulator.get()
                
                // Get updated copy from db
                val currentDownload = dao.getDownloadById(download.id)
                if (currentDownload != null && (currentDownload.status == "DOWNLOADING" || currentDownload.status == "QUEUED")) {
                    dao.updateDownload(
                        currentDownload.copy(
                            status = "DOWNLOADING",
                            downloadedBytes = currentProgress,
                            speed = currentSpeed
                        )
                    )
                }
            }
        }

        try {
            val partJobs = (0 until numThreads).map { index ->
                val start = index * chunkSize
                val end = if (index == numThreads - 1) totalLength - 1 else (index + 1) * chunkSize - 1
                
                launch(Dispatchers.IO) {
                    downloadPart(download.url, file, start, end, downloadedAccumulator, speedTracker, parsedHeaders)
                }
            }
            
            partJobs.joinAll()
            
            speedJob.cancel()

            // Scan the file so it appears in the gallery/file manager
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            
            // Finished successfully
            dao.updateDownload(
                download.copy(
                    status = "COMPLETED",
                    totalBytes = totalLength,
                    downloadedBytes = totalLength,
                    speed = 0
                )
            )
        } catch (e: Exception) {
            speedJob.cancel()
            throw e
        }
    }

    private suspend fun ytDlpDownload(
        context: Context,
        download: DownloadEntity
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()
        val file = File(download.filepath)

        dao.updateDownload(download.copy(status = "DOWNLOADING", errorMessage = null, downloadedBytes = 0, speed = 0))

        try {
            withTimeout(30_000L) {
                YoutubeDL.getInstance().updateYoutubeDL(context)
                Log.d(TAG, "yt-dlp updated to latest version")
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "yt-dlp update timed out, using bundled version")
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed, using bundled version", e)
        }

        val request = YoutubeDLRequest(download.sourceUrl ?: download.url)
        val parentDir = file.parent ?: ""
        val fileNameWithoutExt = file.nameWithoutExtension
        request.addOption("-o", "$parentDir/$fileNameWithoutExt.%(ext)s")
        request.addOption("--no-warnings")
        request.addOption("--no-check-certificates")
        request.addOption("--no-playlist")
        request.addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        request.addOption("--extractor-retries", "3")
        request.addOption("--retries", "5")

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("rate_limit", false)) {
            val maxRate = prefs.getString("max_rate", "1000") ?: "1000"
            if (maxRate.toIntOrNull() != null && maxRate.toInt() in 1..1_000_000) {
                request.addOption("-r", "${maxRate}K")
            }
        }
        if (prefs.getBoolean("proxy", false)) {
            val proxyUrl = prefs.getString("proxy_url", "") ?: ""
            if (proxyUrl.isNotBlank()) {
                request.addOption("--proxy", proxyUrl)
            }
        }
        if (prefs.getBoolean("aria2c", false)) {
            request.addOption("--downloader", "libaria2c.so")
        } else {
            val fragments = prefs.getInt("concurrent_fragments", 8)
            if (fragments > 1) {
                request.addOption("--concurrent-fragments", fragments)
            }
        }
        if (prefs.getBoolean("force_ipv4", false)) {
            request.addOption("-4")
        }
        if (prefs.getBoolean("cookies", false)) {
            request.addOption("--cookies", context.cacheDir.resolve("cookies.txt").absolutePath)
        }

        val lastProgress = AtomicLong(0L)

        val progressJob = launch {
            while (isActive) {
                try {
                    delay(500)
                    val bytes = lastProgress.get()
                    val current = dao.getDownloadById(download.id)
                    if (current != null && (current.status == "DOWNLOADING" || current.status == "QUEUED")) {
                        dao.updateDownload(current.copy(downloadedBytes = bytes, speed = 0))
                    }
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { }
            }
        }

        try {
            withTimeout(180_000L) {
                YoutubeDL.getInstance().execute(request, object : DownloadProgressCallback {
                    override fun onProgressUpdate(progress: Float, etaInSeconds: Long, line: String?) {
                        lastProgress.set((progress * 10_000_000).toLong())
                    }
                })
            }
            progressJob.cancel()

            val dirFile = File(parentDir)
            val actualFile = if (dirFile.isDirectory) {
                dirFile.listFiles()?.find { it.name.startsWith(fileNameWithoutExt) && it.lastModified() > System.currentTimeMillis() - 60000 }
            } else null
            val resolvedFile = actualFile ?: file
            val targetFile = if (resolvedFile.exists() && resolvedFile.absolutePath != file.absolutePath) {
                resolvedFile.renameTo(file)
                file
            } else {
                resolvedFile
            }

            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)

            val finalSize = if (targetFile.exists()) targetFile.length() else 0L
            dao.updateDownload(
                download.copy(
                    status = "COMPLETED",
                    totalBytes = finalSize,
                    downloadedBytes = finalSize,
                    filepath = targetFile.absolutePath,
                    speed = 0
                )
            )
            Log.d(TAG, "yt-dlp download complete: ${download.filename}")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "yt-dlp timed out after 180s", e)
            dao.updateDownload(
                download.copy(
                    status = "FAILED",
                    errorMessage = "yt-dlp timed out. The video may be private or require a login."
                )
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            dao.updateDownload(
                download.copy(
                    status = "FAILED",
                    errorMessage = "Download was interrupted"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp download failed", e)
            val errorMsg = e.message ?: e.cause?.message ?: e.cause?.cause?.message ?: e.toString()
            dao.updateDownload(
                download.copy(
                    status = "FAILED",
                    errorMessage = "yt-dlp error: $errorMsg"
                )
            )
        }
    }

    private suspend fun downloadPart(
        url: String,
        file: File,
        start: Long,
        end: Long,
        totalProgress: AtomicLong,
        speedTracker: AtomicLong,
        headers: Map<String, String> = emptyMap()
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.code != 206 && response.code != 200) {
                throw Exception("Server failed chunk support, response code: ${response.code}")
            }

            val body = response.body ?: throw Exception("Chunk body null")
            val inputStream = body.byteStream()
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var currentWritePosition = start

            var raf: RandomAccessFile? = null
            try {
                raf = RandomAccessFile(file, "rw")
                raf.seek(start)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    currentWritePosition += bytesRead
                    raf.write(buffer, 0, bytesRead)
                    totalProgress.addAndGet(bytesRead.toLong())
                    speedTracker.addAndGet(bytesRead.toLong())
                    
                    // Cooperative cancel check
                    yield()
                }
            } finally {
                raf?.close()
            }
        }
    }
}
