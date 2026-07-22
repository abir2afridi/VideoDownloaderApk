package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractVimeo(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    val videoId = extractVimeoId(resolved)
    Log.d(EXTRACTOR_TAG, "Extracting Vimeo video: $videoId")

    val apiResult = extractFromVimeoApi(resolved)
    if (apiResult != null) return apiResult

    val html = fetchPageHtml(resolved)
    if (html != null) {
        val configResult = extractFromVimeoConfig(html)
        if (configResult != null) return configResult
        val metaResult = extractVimeoFromMeta(html)
        if (metaResult != null) return metaResult
        val jsonLdResult = extractFromVimeoJsonLd(html)
        if (jsonLdResult != null) return jsonLdResult
        val videoTagResult = extractFromVimeoVideoTag(html)
        if (videoTagResult != null) return videoTagResult
    }
    return null
}

internal fun extractVimeoId(url: String): String? {
    var u = url.removeSuffix("/")
    if (u.contains("?")) u = u.substringBefore("?")
    if (u.contains("#")) u = u.substringBefore("#")

    val patterns = listOf(
        Regex("""vimeo\.com/(\d+)"""),
        Regex("""vimeo\.com/channels/[^/]+/(\d+)"""),
        Regex("""vimeo\.com/groups/[^/]+/videos/(\d+)"""),
        Regex("""vimeo\.com/ondemand/[^/]+/(\d+)"""),
        Regex("""player\.vimeo\.com/video/(\d+)"""),
        Regex("""vimeo\.com/(\d+)""")
    )
    for (pattern in patterns) {
        val match = pattern.find(u)
        if (match != null) return match.groupValues[1]
    }
    return null
}

private fun extractFromVimeoApi(url: String): TikTokVideoData? {
    try {
        val oembedUrl = "https://vimeo.com/api/oembed.json?url=$url"
        val request = Request.Builder().url(oembedUrl)
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "application/json")
            .get().build()

        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val data = adapter.fromJson(body) ?: return null

            val title = data["title"]?.toString() ?: ""
            val author = data["author_name"]?.toString() ?: ""
            val authorId = data["author_url"]?.toString() ?: ""
            val thumbnail = data["thumbnail_url"]?.toString() ?: ""
            val duration = (data["duration"] as? Number)?.toLong() ?: 0L
            val videoId = data["video_id"]?.toString() ?: data["id"]?.toString() ?: ""

            val playerUrl = data["player_url"]?.toString()
            var videoUrl: String? = data["url"]?.toString()

            if (videoUrl.isNullOrBlank() && playerUrl != null) {
                videoUrl = playerUrl
            }

            if (!videoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = videoId, title = title, author = author, authorId = authorId,
                    thumbnail = thumbnail, duration = duration,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "Vimeo API failed", e)
    }
    return null
}

private fun extractFromVimeoConfig(html: String): TikTokVideoData? {
    try {
        val configMatch = Regex("""window\.vimeo\.config\s*=\s*({[\s\S]*?});""").find(html)
            ?: Regex("""vimeo\.config\s*=\s*({[\s\S]*?});""").find(html)
            ?: Regex("""window\.playerConfig\s*=\s*({[\s\S]*?});""").find(html)

        if (configMatch != null) {
            val jsonStr = configMatch.groupValues[1]
            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val config = adapter.fromJson(jsonStr) ?: return null

            val requestToken = config["request"] as? Map<*, *>
            val files = requestToken?.get("files") as? Map<*, *>
                ?: config["files"] as? Map<*, *>
            val progressive = files?.get("progressive") as? List<*>
            val hls = files?.get("hls") as? Map<*, *>
            val dash = files?.get("dash") as? Map<*, *>

            var videoUrl: String? = null
            var bestQuality = -1

            if (progressive != null) {
                for (track in progressive) {
                    val t = track as? Map<*, *> ?: continue
                    val url = t["url"]?.toString() ?: continue
                    val quality = t["quality"]?.toString() ?: ""
                    val width = (t["width"] as? Number)?.toInt() ?: 0
                    val height = (t["height"] as? Number)?.toInt() ?: 0
                    val dimension = width * height
                    if (dimension > bestQuality) {
                        bestQuality = dimension
                        videoUrl = url
                    }
                }
            }

            if (videoUrl == null && hls != null) {
                videoUrl = hls["url"]?.toString()
                    ?: hls["default_url"]?.toString()
                    ?: hls["cdn_url"]?.toString()
            }

            if (videoUrl == null && dash != null) {
                videoUrl = dash["url"]?.toString()
            }

            val video = config["video"] as? Map<*, *>
            val title = video?.get("title")?.toString()
                ?: config["title"]?.toString() ?: ""
            val author = video?.let {
                (it["owner"] as? Map<*, *>)?.get("name")?.toString()
            } ?: ""
            val duration = (video?.get("duration") as? Number)?.toLong()
                ?: (config["duration"] as? Number)?.toLong() ?: 0L
            val thumbnail = video?.get("thumbs")?.let {
                val thumbs = it as? Map<*, *>
                thumbs?.get("base")?.toString()
            } ?: video?.get("thumbnail")?.toString() ?: ""
            val videoId = video?.get("id")?.toString()
                ?: config["video_id"]?.toString() ?: ""

            if (!videoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = videoId, title = title, author = author, authorId = author,
                    thumbnail = thumbnail, duration = duration,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromVimeoConfig failed", e)
    }
    return null
}

private fun extractVimeoFromMeta(html: String): TikTokVideoData? {
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
        Log.w(EXTRACTOR_TAG, "extractVimeoFromMeta failed", e)
    }
    return null
}

private fun extractFromVimeoJsonLd(html: String): TikTokVideoData? {
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
        Log.w(EXTRACTOR_TAG, "extractFromVimeoJsonLd failed", e)
    }
    return null
}

private fun extractFromVimeoVideoTag(html: String): TikTokVideoData? {
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
        Log.w(EXTRACTOR_TAG, "extractFromVimeoVideoTag failed", e)
    }
    return null
}
