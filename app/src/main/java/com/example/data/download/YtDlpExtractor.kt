package com.example.data.download

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
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

            val title = videoInfo.title ?: videoInfo.fulltitle ?: ""
            val author = videoInfo.uploader ?: ""
            val authorId = videoInfo.uploaderId ?: ""
            val thumbnail = videoInfo.thumbnail ?: ""
            val duration = videoInfo.duration.toLong()
            val id = videoInfo.id ?: ""

            var videoUrl: String? = videoInfo.url
            var headers: Map<String, String>? = videoInfo.httpHeaders

            if (videoUrl.isNullOrBlank()) {
                Log.d(TAG, "Main URL empty, searching formats (count=${videoInfo.formats?.size}, requested=${videoInfo.requestedFormats?.size})")
                val bestFormat = findBestVideoFormat(videoInfo)
                if (bestFormat != null) {
                    videoUrl = bestFormat.url
                    headers = bestFormat.httpHeaders ?: videoInfo.httpHeaders
                    Log.d(TAG, "Found format: ${bestFormat.formatId}, ext=${bestFormat.ext}, ${bestFormat.width}x${bestFormat.height}")
                }
            }

            if (videoUrl.isNullOrBlank()) {
                Log.w(TAG, "yt-dlp returned no direct URL for: $url")
                return null
            }

            Log.d(TAG, "yt-dlp success: $title by $author, urlLen=${videoUrl.length}, hasHeaders=${headers != null}")

            TikTokVideoData(
                id = id,
                title = title,
                author = author,
                authorId = authorId,
                thumbnail = thumbnail,
                duration = duration,
                videoUrl = videoUrl,
                videoUrlNoWatermark = videoUrl,
                audioUrl = null,
                httpHeaders = headers
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

    private fun findBestVideoFormat(info: VideoInfo): VideoFormat? {
        val allFormats = mutableListOf<VideoFormat>()
        info.requestedFormats?.let { allFormats.addAll(it) }
        info.formats?.let { allFormats.addAll(it) }

        if (allFormats.isEmpty()) return null

        val videoFormats = allFormats.filter { fmt ->
            fmt.url.isNotBlank() && (fmt.vcodec != null && fmt.vcodec != "none")
        }

        if (videoFormats.isEmpty()) return allFormats.firstOrNull { it.url.isNotBlank() }

        return videoFormats.maxByOrNull { fmt ->
            val height = fmt.height
            val tbr = fmt.tbr
            height * 1000 + tbr
        }
    }
}
