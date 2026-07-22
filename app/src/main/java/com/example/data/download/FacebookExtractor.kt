package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractFacebook(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    val videoId = extractFacebookVideoId(resolved)
    Log.d(EXTRACTOR_TAG, "Extracting Facebook video: $videoId")

    val html = fetchPageHtml(resolved) ?: return tryFacebookOembed(resolved)

    val fbMetaResult = extractFacebookFromMeta(html)
    if (fbMetaResult != null) return fbMetaResult

    val jsonLdResult = extractFromFbJsonLd(html)
    if (jsonLdResult != null) return jsonLdResult

    val fbScriptResult = extractFromFbScriptData(html)
    if (fbScriptResult != null) return fbScriptResult

    val sdResult = extractFromFbSd(html)
    if (sdResult != null) return sdResult

    val videoTagResult = extractFromFbVideoTag(html)
    if (videoTagResult != null) return videoTagResult

    return tryFacebookOembed(resolved)
}

internal fun extractFacebookVideoId(url: String): String {
    return url
}

private fun extractFacebookFromMeta(html: String): TikTokVideoData? {
    try {
        val ogVideo = extractMetaContent(html, "og:video")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video:secure_url")
        if (ogVideo.isNullOrBlank()) return null

        val title = extractMetaContent(html, "og:title") ?: ""
        val description = extractMetaContent(html, "og:description") ?: ""
        val thumbnail = extractMetaContent(html, "og:image") ?: ""

        return TikTokVideoData(
            id = "", title = title.ifBlank { description }, author = "", authorId = "",
            thumbnail = thumbnail, duration = 0L,
            videoUrl = ogVideo, videoUrlNoWatermark = ogVideo, audioUrl = null
        )
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFacebookFromMeta failed", e)
    }
    return null
}

private fun extractFromFbJsonLd(html: String): TikTokVideoData? {
    try {
        val jsonLdPattern = Pattern.compile(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = jsonLdPattern.matcher(html)
        while (matcher.find()) {
            val jsonStr = matcher.group(1) ?: continue
            try {
                val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
                val data = adapter.fromJson(jsonStr) ?: continue
                val type = (data["@type"] as? String) ?: ""
                if (type != "VideoObject") continue

                val contentUrl = data["contentUrl"]?.toString()
                val embedUrl = data["embedUrl"]?.toString()
                val videoUrl = contentUrl ?: embedUrl
                if (!videoUrl.isNullOrBlank()) {
                    val title = data["name"]?.toString() ?: data["description"]?.toString() ?: ""
                    val author = (data["author"] as? Map<*, *>)?.get("name")?.toString() ?: ""
                    val thumbnail = (data["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString() ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = author,
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                    )
                }
            } catch (_: Exception) { continue }
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFromFbJsonLd failed", e)
    }
    return null
}

private fun extractFromFbScriptData(html: String): TikTokVideoData? {
    try {
        val scriptTagPattern = Pattern.compile(
            """<script[^>]*>([\s\S]{0,50000}?)</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val scriptMatcher = scriptTagPattern.matcher(html)
        while (scriptMatcher.find()) {
            val scriptContent = scriptMatcher.group(1) ?: continue
            if (!scriptContent.contains("playable_url") && !scriptContent.contains("videoData") && !scriptContent.contains("video_data")) continue
            val urlMatch = Regex("""playable_url["'\s]*:["'\s]*"([^"]+?)""").find(scriptContent)
            if (urlMatch != null) {
                val videoUrl = urlMatch.groupValues[1]
                    .replace("\\/", "/")
                    .replace("\\u002F", "/")
                    .replace("\\", "")
                if (videoUrl.isNotBlank()) {
                    val titleMatch = Regex("""title["'\s]*:["'\s]*"([^"]+?)""").find(scriptContent)
                    val title = titleMatch?.groupValues?.get(1) ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = "", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                    )
                }
            }
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFromFbScriptData failed", e)
    }
    return null
}

private fun extractFromFbSd(html: String): TikTokVideoData? {
    try {
        val scriptTagPattern = Pattern.compile(
            """<script[^>]*>([\s\S]{0,50000}?)</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val scriptMatcher = scriptTagPattern.matcher(html)
        while (scriptMatcher.find()) {
            val sdContent = scriptMatcher.group(1) ?: continue
            if (!sdContent.contains("VideoUrl") && !sdContent.contains("sd_src") && !sdContent.contains("hd_src")) continue
            val sdUrlMatch = Regex("""sd_src["':\s]+"?([^"'\s,]+)""").find(sdContent)
            val hdUrlMatch = Regex("""hd_src["':\s]+"?([^"'\s,]+)""").find(sdContent)
            val videoUrl = hdUrlMatch?.groupValues?.get(1) ?: sdUrlMatch?.groupValues?.get(1)
            if (!videoUrl.isNullOrBlank()) {
                val titleMatch = Regex("""title["':\s]+"?([^"',]+)""").find(sdContent)
                val title = titleMatch?.groupValues?.get(1)?.trim() ?: ""
                return TikTokVideoData(
                    id = "", title = title, author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFromFbSd failed", e)
    }
    return null
}

private fun extractFromFbVideoTag(html: String): TikTokVideoData? {
    try {
        val videoTagPattern = Pattern.compile(
            """<video[^>]+src=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = videoTagPattern.matcher(html)
        if (matcher.find()) {
            val videoUrl = matcher.group(1)
            if (!videoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = "", title = "", author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFromFbVideoTag failed", e)
    }
    return null
}

private fun tryFacebookOembed(url: String): TikTokVideoData? {
    try {
        val oembedUrl = "https://www.facebook.com/plugins/video/oembed/?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        val html = fetchPageHtml(oembedUrl) ?: return null

        val titleMatch = Regex(""""title"\s*:\s*"([^"]+)""").find(html)
        val title = titleMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: ""

        val thumbnailMatch = Regex(""""thumbnail_url"\s*:\s*"([^"]+)""").find(html)
        val thumbnail = thumbnailMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: ""

        val videoSrcMatch = Regex("""https?://[^"'\s]+\.mp4[^"'\s]*""").find(html)
        val videoUrl = videoSrcMatch?.value

        if (!videoUrl.isNullOrBlank()) {
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
            )
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "tryFacebookOembed failed", e)
    }
    return null
}
