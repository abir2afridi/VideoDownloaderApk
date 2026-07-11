package com.example.data.download

import android.content.Context
import android.os.StatFs
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

                dao.updateDownload(download.copy(status = "DOWNLOADING", errorMessage = null))
                
                // Perform download
                executeDownload(context, download)
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
            if (download != null && download.status == "DOWNLOADING") {
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
            .header("Range", "bytes=0-0") // Safe way to see if range is supported
            .build()

        var totalLength = download.totalBytes
        var acceptRanges = false

        try {
            client.newCall(checkRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val contentRange = response.header("Content-Range")
                    val fullLenHeader = response.header("Content-Length")
                    
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

        // 2. Storage Check
        if (totalLength > 0) {
            val stat = StatFs(file.parentFile?.absolutePath ?: context.filesDir.absolutePath)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            if (bytesAvailable < totalLength) {
                dao.updateDownload(download.copy(status = "FAILED", errorMessage = "Insufficient storage space"))
                return@withContext
            }
        }

        dao.updateDownload(download.copy(totalBytes = totalLength))

        // 3. Multi-thread or Single-thread download
        if (acceptRanges && totalLength > 2 * 1024 * 1024 && download.threads > 1) {
            // Multi-thread Download
            executeMultiThreadDownload(context, download, totalLength)
        } else {
            // Single-thread Download
            executeSingleThreadDownload(context, download, totalLength)
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
                if (now - lastUpdate >= 1000) {
                    val elapsed = (now - lastUpdate) / 1000.0
                    val currentSpeed = (speedBytes / elapsed).toLong()
                    speedBytes = 0L
                    lastUpdate = now

                    dao.updateDownload(
                        download.copy(
                            status = "DOWNLOADING",
                            downloadedBytes = downloadedBytes,
                            speed = currentSpeed
                        )
                    )
                }
            }
            raf.close()

            // Completion
            dao.updateDownload(
                download.copy(
                    status = "COMPLETED",
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
                delay(1000)
                val currentSpeed = speedTracker.getAndSet(0L)
                val currentProgress = downloadedAccumulator.get()
                
                // Get updated copy from db
                val currentDownload = dao.getDownloadById(download.id)
                if (currentDownload != null && currentDownload.status == "DOWNLOADING") {
                    dao.updateDownload(
                        currentDownload.copy(
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
            
            // Finished successfully
            dao.updateDownload(
                download.copy(
                    status = "COMPLETED",
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
