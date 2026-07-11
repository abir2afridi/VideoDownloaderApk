package com.example.data.download

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

object MediaUtils {

    fun getCategoryFromMimeType(mimeType: String): String {
        return when {
            mimeType.startsWith("video/", ignoreCase = true) -> "Video"
            mimeType.startsWith("audio/", ignoreCase = true) -> "Audio"
            mimeType.startsWith("image/", ignoreCase = true) -> "Images"
            else -> "Other"
        }
    }

    fun getCategoryFromExtension(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "mp4", "mkv", "webm", "avi", "3gp", "flv", "mov" -> "Video"
            "mp3", "m4a", "wav", "flac", "ogg", "aac" -> "Audio"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "Images"
            else -> "Other"
        }
    }

    fun cleanFilename(url: String, contentDisposition: String?, mimeType: String?): String {
        var name = ""
        
        // 1. Try content disposition
        if (!contentDisposition.isNullOrBlank()) {
            val filenameToken = "filename="
            val index = contentDisposition.indexOf(filenameToken)
            if (index != -1) {
                name = contentDisposition.substring(index + filenameToken.length)
                    .trim { it == '"' || it == ' ' || it == '\'' }
            }
        }
        
        // 2. Try URL path segment
        if (name.isBlank()) {
            try {
                val uri = Uri.parse(url)
                name = uri.lastPathSegment ?: ""
                // Strip query parameters if not stripped already
                val qIndex = name.indexOf('?')
                if (qIndex != -1) {
                    name = name.substring(0, qIndex)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // 3. Fallback to default
        if (name.isBlank()) {
            name = "download_" + System.currentTimeMillis()
        }
        
        // Decode URL encoded characters
        try {
            name = Uri.decode(name)
        } catch (e: Exception) {
            // Ignore
        }

        // Separate base name and extension
        var ext = ""
        val lastDot = name.lastIndexOf('.')
        if (lastDot != -1 && lastDot < name.length - 1) {
            ext = name.substring(lastDot + 1)
            name = name.substring(0, lastDot)
        }

        // Clean filename of illegal chars
        name = name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        if (name.length > 100) {
            name = name.substring(0, 100)
        }

        // If extension is missing, guess from MimeType
        if (ext.isBlank() && !mimeType.isNullOrBlank()) {
            val guessExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (!guessExt.isNullOrBlank()) {
                ext = guessExt
            }
        }

        if (ext.isBlank()) {
            ext = "mp4" // default fallback
        }

        return "$name.$ext"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"
        return formatBytes(bytesPerSecond) + "/s"
    }

    fun getEstimatedRemainingTime(totalBytes: Long, downloadedBytes: Long, speed: Long): String {
        if (speed <= 0 || totalBytes <= 0) return "Calculating..."
        val remainingBytes = totalBytes - downloadedBytes
        if (remainingBytes <= 0) return "0s"
        val remainingSeconds = remainingBytes / speed
        return when {
            remainingSeconds < 60 -> "${remainingSeconds}s"
            remainingSeconds < 3600 -> {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                "${mins}m ${secs}s"
            }
            else -> {
                val hours = remainingSeconds / 3600
                val mins = (remainingSeconds % 3600) / 60
                "${hours}h ${mins}m"
            }
        }
    }
}
