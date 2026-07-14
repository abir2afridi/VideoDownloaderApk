package com.example.data.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.StatFs
import android.webkit.MimeTypeMap
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.DownloadEntity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object DownloadEngine {
    private const val TAG = "DownloadEngine"
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(TikTokCookieStore.cookieJar)
        .addInterceptor { chain ->
            val originalUrl = chain.request().url.toString()
            val requestBuilder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
            if (originalUrl.contains("tiktokcdn.com") || originalUrl.contains("tiktok.com")) {
                requestBuilder.header("Referer", "https://www.tiktok.com/")
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    // Map to track progress speed calculations
    private val speedMap = ConcurrentHashMap<Int, Long>()

    fun startDownload(context: Context, downloadId: Int) {
        if (activeJobs.containsKey(downloadId)) {
            Log.d(TAG, "Download $downloadId is already running")
            return
        }

        val db = AppDatabase.getDatabase(context)
        val dao = db.downloadDao()

        val job = scope.launch {
            try {
                val download = dao.getDownloadById(downloadId) ?: return@launch
                if (download.status == "COMPLETED") return@launch

                val downloadingEntity = download.copy(status = "DOWNLOADING", errorMessage = null)
                dao.updateDownload(downloadingEntity)
                
                // Perform download
                executeDownload(context, downloadingEntity)
            } catch (e: CancellationException) {
                Log.d(TAG, "Download $downloadId was cancelled/paused")
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading $downloadId", e)
                val currentDb = AppDatabase.getDatabase(context)
                val currentDao = currentDb.downloadDao()
                val currentDownload = currentDao.getDownloadById(downloadId)
                if (currentDownload != null) {
                    currentDao.updateDownload(
                        currentDownload.copy(
                            status = "FAILED",
                            errorMessage = e.localizedMessage ?: "Unknown network error"
                        )
                    )
                }
            } finally {
                activeJobs.remove(downloadId)
                speedMap.remove(downloadId)
            }
        }
        activeJobs[downloadId] = job
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
        // Ensure parent directory exists
        file.parentFile?.mkdirs()

        // 1. Initial HEAD or quick GET request to inspect the headers
        val checkRequest = Request.Builder()
            .url(download.url)
            .header("Range", "bytes=0-0")
            .build()

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
        
        val request = requestBuilder.build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP unexpected code ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty response body")
            val isAppend = response.code == 206 || (existingBytes > 0 && totalLength == 0L)
            
            val raf = RandomAccessFile(file, "rw")
            if (isAppend) {
                raf.seek(existingBytes)
            } else {
                raf.setLength(0) // clear
            }
            
            val inputStream = body.byteStream()
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var downloadedBytes = if (isAppend) existingBytes else 0L
            
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
            raf.close()

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
        val rafSetup = RandomAccessFile(file, "rw")
        rafSetup.setLength(totalLength)
        rafSetup.close()

        val numThreads = download.threads
        val chunkSize = totalLength / numThreads
        val downloadedAccumulator = AtomicLong(0L)

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
                    downloadPart(download.url, file, start, end, downloadedAccumulator, speedTracker)
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

    private suspend fun downloadPart(
        url: String,
        file: File,
        start: Long,
        end: Long,
        totalProgress: AtomicLong,
        speedTracker: AtomicLong
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code != 206 && response.code != 200) {
                throw Exception("Server failed chunk support, response code: ${response.code}")
            }

            val body = response.body ?: throw Exception("Chunk body null")
            val inputStream = body.byteStream()
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var currentWritePosition = start

            val raf = RandomAccessFile(file, "rw")
            raf.seek(start)

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                currentWritePosition += bytesRead
                raf.write(buffer, 0, bytesRead)
                totalProgress.addAndGet(bytesRead.toLong())
                speedTracker.addAndGet(bytesRead.toLong())
                
                // Cooperative cancel check
                yield()
            }
            raf.close()
        }
    }
}
