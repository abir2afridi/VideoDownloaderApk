package com.example.data.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.database.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class DownloadIntegrityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DownloadIntegrityWorker"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "DownloadIntegrityWorker running...")
        val context = applicationContext
        val database = AppDatabase.getDatabase(context)
        val dao = database.downloadDao()

        // Get snapshot of all downloads
        val allDownloads = dao.getPublicDownloads().firstOrNull() ?: emptyList()
        val privateDownloads = dao.getPrivateDownloads().firstOrNull() ?: emptyList()
        val combinedDownloads = allDownloads + privateDownloads

        Log.d(TAG, "Found ${combinedDownloads.size} downloads to check integrity and connectivity.")

        for (download in combinedDownloads) {
            try {
                // 1. Check File Integrity
                val file = File(download.filepath)
                val integrity = when {
                    file.exists() -> {
                        val fileLength = file.length()
                        if (download.status == "COMPLETED") {
                            if (fileLength > 0) {
                                if (download.totalBytes > 0 && fileLength < download.totalBytes) {
                                    "CORRUPTED" // File is shorter than expected
                                } else {
                                    "OK"
                                }
                            } else {
                                "CORRUPTED" // File size is 0 but marked COMPLETED
                            }
                        } else {
                            // Ongoing or paused
                            if (fileLength >= download.downloadedBytes) {
                                "OK"
                            } else {
                                "CORRUPTED"
                            }
                        }
                    }
                    else -> {
                        if (download.downloadedBytes > 0) {
                            "MISSING"
                        } else {
                            "PENDING"
                        }
                    }
                }

                // 2. Check Connection Health
                val connectionHealth = checkConnectionHealth(download.url)

                // 3. Update Download Status in DB
                val updatedDownload = download.copy(
                    integrityStatus = integrity,
                    connectionHealth = connectionHealth,
                    lastCheckedTime = System.currentTimeMillis()
                )
                dao.updateDownload(updatedDownload)
                Log.d(TAG, "Download ID ${download.id} check: integrity=$integrity, connection=$connectionHealth")

            } catch (e: Exception) {
                Log.e(TAG, "Failed checking download ${download.id}", e)
            }
        }

        Result.success()
    }

    private fun checkConnectionHealth(urlStr: String): String {
        val startTime = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(urlStr)
                .head() // Use HEAD request to minimize data consumption
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code in 200..399) {
                    when {
                        duration < 400 -> "EXCELLENT"
                        duration < 1200 -> "GOOD"
                        else -> "POOR"
                    }
                } else {
                    "POOR"
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Connection health check failed for URL: $urlStr", e)
            "UNREACHABLE"
        } catch (e: Exception) {
            "UNREACHABLE"
        }
    }
}
