package com.example.data.download

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo

internal object YtDlpExtractor {
    private const val TAG = "YtDlpExtractor"

    fun extract(url: String): TikTokVideoData? {
        return try {
            Log.d(TAG, "Extracting via yt-dlp: $url")
            val request = YoutubeDLRequest(url)
            request.addOption("--no-warnings")
            request.addOption("--no-check-certificates")
            request.addOption("--flat-playlist")

            val videoInfo = YoutubeDL.getInstance().getInfo(request)

            val videoUrl = videoInfo.url
            if (videoInfo.url.isNullOrBlank()) {
                Log.w(TAG, "yt-dlp returned no direct URL for: $url")
                return null
            }

            val title = videoInfo.title ?: videoInfo.fulltitle ?: ""
            val author = videoInfo.uploader ?: ""
            val authorId = videoInfo.uploaderId ?: ""
            val thumbnail = videoInfo.thumbnail ?: ""
            val duration = videoInfo.duration.toLong()
            val id = videoInfo.id ?: ""

            Log.d(TAG, "yt-dlp success: $title by $author")

            TikTokVideoData(
                id = id,
                title = title,
                author = author,
                authorId = authorId,
                thumbnail = thumbnail,
                duration = duration,
                videoUrl = videoUrl,
                videoUrlNoWatermark = videoUrl,
                audioUrl = null
            )
        } catch (e: InterruptedException) {
            Log.w(TAG, "yt-dlp extraction interrupted", e)
            Thread.currentThread().interrupt()
            null
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp extraction failed for: $url", e)
            null
        }
    }
}
