package com.example

import android.app.Application
import android.os.Environment
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.StringWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NexLoadApp : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            YoutubeDL.getInstance().init(this)
            Log.d("NexLoadApp", "yt-dlp initialized successfully")
        } catch (e: Exception) {
            Log.e("NexLoadApp", "Failed to initialize yt-dlp", e)
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                pw.println("Thread: ${thread.name}")
                pw.println("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                pw.println()
                throwable.printStackTrace(pw)
                pw.close()
                val crashText = sw.toString()

                val internalDir = File(filesDir, "crash_logs")
                internalDir.mkdirs()
                File(internalDir, "crash_$dateStr.txt").writeText(crashText)

                try {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir != null) {
                        File(downloadsDir, "NexLoad_crash_$dateStr.txt").writeText(crashText)
                    }
                } catch (_: Exception) {}

                Log.e("NexLoadCrash", crashText)
            } catch (_: Exception) {}
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(1)
            }
        }
    }
}
