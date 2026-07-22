package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractDailymotion(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    val videoId = extractDailymotionId(resolved)
    Log.d(EXTRACTOR_TAG, "Extracting Dailymotion video: $videoId")

    val apiResult = extractFromDailymotionApi(videoId)
    if (apiResult != null) return apiResult

    val html = fetchPageHtml(resolved)
    if (html != null) {
        val metaResult = extractDailymotionFromMeta(html)
        if (metaResult != null) return metaResult
        val jsonLdResult = extractFromDailymotionJsonLd(html)
        if (jsonLdResult != null) return jsonLdResult
        val scriptResult = extractFromDailymotionScriptData(html)
        if (scriptResult != null) return scriptResult
        val videoTagResult = extractFromDailymotionVideoTag(html)
        if (videoTagResult != null) return videoTagResult
    }
    return null
}

internal fun extractDailymotionId(url: String): String? {
    var u = url.removeSuffix("/")
    if (u.contains("?")) u = u.substringBefore("?")
    if (u.contains("#")) u = u.substringBefore("#")

    val patterns = listOf(
        Regex("""dailymotion\.com/video/([a-zA-Z0-9]+)"""),
        Regex("""dai\.ly/([a-zA-Z0-9]+)"""),
        Regex("""dailymotion\.com/embed/video/([a-zA-Z0-9]+)"""),
        Regex("""dailymotion\.com/[^/]+/video/([a-zA-Z0-9]+)""")
    )
    for (pattern in patterns) {
        val match = pattern.find(u)
        if (match != null) return match.groupValues[1]
    }
    return null
}

private fun extractFromDailymotionApi(videoId: String?): TikTokVideoData? {
    if (videoId == null) return null
    try {
        val apiUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
        val request = Request.Builder().url(apiUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .get().build()

        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val data = adapter.fromJson(body) ?: return null

            val title = data["title"]?.toString() ?: ""
            val owner = data["owner"] as? Map<*, *>
            val author = owner?.get("screenname")?.toString()
                ?: owner?.get("username")?.toString() ?: ""
            val thumbnail = data["thumbnail_url"]?.toString()
                ?: data["thumbnail_medium_url"]?.toString()
                ?: data["poster_url"]?.toString() ?: ""
            val duration = (data["duration"] as? Number)?.toLong() ?: 0L

            val qualities = data["qualities"] as? Map<*, *>
            var videoUrl: String? = null
            val qualityPriority = listOf("2160", "1080", "720", "480", "360", "240", "auto")

            if (qualities != null) {
                for (quality in qualityPriority) {
                    val streams = qualities[quality] as? List<*>
                    if (streams != null) {
                        for (stream in streams) {
                            val streamMap = stream as? Map<*, *>
                            val url = streamMap?.get("url")?.toString()
                            val type = streamMap?.get("type")?.toString() ?: ""
                            if (!url.isNullOrBlank() && (type.contains("video") || type.isEmpty())) {
                                videoUrl = url
                                break
                            }
                        }
                    }
                    if (videoUrl != null) break
                }
            }

            if (videoUrl == null) return null
            return TikTokVideoData(
                id = videoId, title = title, author = author, authorId = "",
                thumbnail = thumbnail, duration = duration,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
            )
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "Dailymotion API failed", e)
    }
    return null
}

private fun extractDailymotionFromMeta(html: String): TikTokVideoData? {
    try {
        val ogVideo = extractMetaContent(html, "og:video")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video:secure_url")
        if (!ogVideo.isNullOrBlank()) {
            val title = extractMetaContent(html, "og:title") ?: ""
            val thumbnail = extractMetaContent(html, "og:image") ?: ""
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = ogVideo, videoUrlNoWatermark = ogVideo, audioUrl = null
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractDailymotionFromMeta failed", e)
    }
    return null
}

private fun extractFromDailymotionJsonLd(html: String): TikTokVideoData? {
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
                if (contentUrl.isNullOrBlank()) continue

                val title = data["name"]?.toString() ?: ""
                val author = (data["author"] as? Map<*, *>)?.get("name")?.toString() ?: ""
                val thumbnail = (data["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString() ?: ""
                val durationRaw = data["duration"]?.toString() ?: ""
                val duration = parseDuration(durationRaw)

                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = author,
                    thumbnail = thumbnail, duration = duration,
                    videoUrl = contentUrl, videoUrlNoWatermark = contentUrl, audioUrl = null
                )
            } catch (_: Exception) { continue }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromDailymotionJsonLd failed", e)
    }
    return null
}

private fun extractFromDailymotionScriptData(html: String): TikTokVideoData? {
    try {
        val scriptPattern = Pattern.compile(
            """<script[^>]*>([\s\S]*?)(?:player_data|config|metadata)[\s\S]*?</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = scriptPattern.matcher(html)
        while (matcher.find()) {
            val scriptContent = matcher.group(1) ?: continue
            val urlMatch = Regex("""https?://[^"'\s<>]*\.mp4[^"'\s<>]*""").find(scriptContent)
                ?: Regex("""https?://[^"'\s<>]*\.m3u8[^"'\s<>]*""").find(scriptContent)
            if (urlMatch != null) {
                val videoUrl = urlMatch.value
                return TikTokVideoData(
                    id = "", title = "", author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromDailymotionScriptData failed", e)
    }
    return null
}

private fun extractFromDailymotionVideoTag(html: String): TikTokVideoData? {
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
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromDailymotionVideoTag failed", e)
    }
    return null
}
