package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractInstagram(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    val shortcode = extractInstagramShortcode(resolved) ?: return null
    Log.d(EXTRACTOR_TAG, "Extracting Instagram shortcode: $shortcode")

    val graphqlResult = extractFromInstagramGraphql(shortcode)
    if (graphqlResult != null) return graphqlResult

    val html = fetchPageHtml(resolved)
    if (html != null) {
        val metaResult = extractInstagramFromMetaTags(html)
        if (metaResult != null) return metaResult
        val jsonLdResult = extractFromInstagramJsonLd(html)
        if (jsonLdResult != null) return jsonLdResult
        val scriptResult = extractFromInstagramScriptData(html)
        if (scriptResult != null) return scriptResult
    }
    return null
}

internal fun extractInstagramShortcode(url: String): String? {
    var processedUrl = url.removeSuffix("/")
    if (processedUrl.contains("?")) processedUrl = processedUrl.substringBefore("?")
    val patterns = listOf(
        Regex("""instagram\.com/(?:p|reel|tv)/([A-Za-z0-9_-]+)(?:/?)"""),
        Regex("""instagr\.am/(?:p|reel|tv)/([A-Za-z0-9_-]+)(?:/?)"""),
        Regex("""instagram\.com/([A-Za-z0-9_-]{11,})(?:/?)""")
    )
    for (pattern in patterns) {
        val match = pattern.find(processedUrl)
        if (match != null) {
            val code = match.groupValues[1]
            if (code.length >= 5 && !code.contains("?")) return code
        }
    }
    return null
}

private fun extractFromInstagramGraphql(shortcode: String): TikTokVideoData? {
    try {
        val graphqlUrl = "https://www.instagram.com/graphql/query/?query_hash=4777bf1659f3c198a0be3bb630125cce&variables={\"shortcode\":\"$shortcode\"}"
        val request = Request.Builder().url(graphqlUrl)
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .get().build()

        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(body) ?: return null
            val data = root["data"] as? Map<*, *> ?: return null
            val shortcodeMedia = data["shortcode_media"] as? Map<*, *> ?: return null
            return parseInstagramMedia(shortcodeMedia)
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "Instagram GraphQL failed", e)
        return null
    }
}

private fun parseInstagramMedia(media: Map<*, *>): TikTokVideoData? {
    val id = media["id"]?.toString() ?: ""
    val shortcode = media["shortcode"]?.toString() ?: ""
    val title = (media["edge_media_to_caption"] as? Map<*, *>)
        ?.let { caption ->
            (caption["edges"] as? List<*>)?.firstOrNull()
                ?.let { (it as? Map<*, *>)?.get("node") as? Map<*, *> }
                ?.get("text")?.toString()
        } ?: (media["accessibility_caption"]?.toString() ?: "")

    val owner = media["owner"] as? Map<*, *>
    val author = owner?.get("full_name")?.toString()
        ?: owner?.get("username")?.toString() ?: ""
    val authorId = owner?.get("username")?.toString() ?: ""
    val thumbnail = (media["display_url"]?.toString()
        ?: (media["display_resources"] as? List<*>)?.firstOrNull()?.toString()) ?: ""

    val duration = (media["video_duration"] as? Number)?.toLong() ?: 0L

    val videoUrl = media["video_url"]?.toString()

    val isVideo = media["is_video"] as? Boolean ?: false
    val isMultiple = (media["__typename"] as? String) == "GraphSidecar"

    if (isVideo && !videoUrl.isNullOrBlank()) {
        return TikTokVideoData(
            id = id, title = title, author = author, authorId = authorId,
            thumbnail = thumbnail, duration = duration,
            videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
        )
    }

    if (isMultiple) {
        val edges = (media["edge_sidecar_to_children"] as? Map<*, *>)
            ?.get("edges") as? List<*>
        if (edges != null) {
            for (edge in edges) {
                val node = (edge as? Map<*, *>)?.get("node") as? Map<*, *> ?: continue
                if (node["is_video"] as? Boolean == true) {
                    val carouselUrl = node["video_url"]?.toString()
                    if (!carouselUrl.isNullOrBlank()) {
                        return TikTokVideoData(
                            id = id, title = title, author = author, authorId = authorId,
                            thumbnail = node["display_url"]?.toString() ?: thumbnail,
                            duration = (node["video_duration"] as? Number)?.toLong() ?: duration,
                            videoUrl = carouselUrl, videoUrlNoWatermark = carouselUrl, audioUrl = null
                        )
                    }
                }
            }
        }
        return null
    }

    return null
}

private fun extractInstagramFromMetaTags(html: String): TikTokVideoData? {
    try {
        val ogVideo = extractMetaContent(html, "og:video")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video:secure_url")
        if (ogVideo.isNullOrBlank()) return null

        val title = extractMetaContent(html, "og:title") ?: ""
        val description = extractMetaContent(html, "og:description") ?: ""
        val thumbnail = extractMetaContent(html, "og:image") ?: ""

        val author = extractMetaContent(html, "profile:username") ?: ""

        val finalTitle = title.ifBlank { description }

        return TikTokVideoData(
            id = "", title = finalTitle, author = author, authorId = author,
            thumbnail = thumbnail, duration = 0L,
            videoUrl = ogVideo, videoUrlNoWatermark = ogVideo, audioUrl = null
        )
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractInstagramFromMetaTags failed", e)
    }
    return null
}

private fun extractFromInstagramJsonLd(html: String): TikTokVideoData? {
    try {
        val jsonLdPattern = Pattern.compile(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val jsonLdMatcher = jsonLdPattern.matcher(html)
        while (jsonLdMatcher.find()) {
            val jsonStr = jsonLdMatcher.group(1) ?: continue
            try {
                val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
                val data = adapter.fromJson(jsonStr) ?: continue
                val type = (data["@type"] as? String) ?: ""
                if (type != "VideoObject") continue

                val name = data["name"]?.toString() ?: ""
                val description = data["description"]?.toString() ?: ""
                val title = name.ifBlank { description }
                val contentUrl = data["contentUrl"]?.toString()
                val embedUrl = data["embedUrl"]?.toString()
                val videoUrl = contentUrl ?: embedUrl
                val thumbnail = (data["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString()
                    ?: data["thumbnail"]?.toString() ?: ""
                val durationRaw = data["duration"]?.toString() ?: ""
                val duration = parseInstagramDuration(durationRaw)

                if (!videoUrl.isNullOrBlank()) {
                    val author = (data["author"] as? Map<*, *>)?.get("name")?.toString() ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = author,
                        thumbnail = thumbnail, duration = duration,
                        videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                    )
                }
            } catch (_: Exception) { continue }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromInstagramJsonLd failed", e)
    }
    return null
}

private fun extractFromInstagramScriptData(html: String): TikTokVideoData? {
    try {
        val sharePattern = Pattern.compile(
            """window\.__shareConfig\s*=\s*({[\s\S]*?});""",
            Pattern.CASE_INSENSITIVE
        )
        val shareMatcher = sharePattern.matcher(html)
        if (shareMatcher.find()) {
            val jsonStr = shareMatcher.group(1)
            if (jsonStr != null) {
                val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
                val config = adapter.fromJson(jsonStr) ?: return null
                val videoUrl = config["video_url"]?.toString() ?: config["src"]?.toString()
                if (!videoUrl.isNullOrBlank()) {
                    return TikTokVideoData(
                        id = "", title = "", author = "", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                    )
                }
            }
        }

        val additionalDataPattern = Pattern.compile(
            """<script[^>]*type=["']text\/javascript["'][^>]*>([\s\S]*?window\.__additionalData[\s\S]*?)</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val additionalMatcher = additionalDataPattern.matcher(html)
        if (additionalMatcher.find()) {
            val scriptContent = additionalMatcher.group(1)
            val jsonMatch = Regex("""window\.__additionalData\s*\(\s*(\{[\s\S]*?\})\s*\)""").find(scriptContent ?: "")
            if (jsonMatch != null) {
                val jsonStr = jsonMatch.groupValues[1]
                val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
                val data = adapter.fromJson(jsonStr) ?: return null
                val shortcodeMedia = (data["graphql"] as? Map<*, *>)?.get("shortcode_media") as? Map<*, *>
                if (shortcodeMedia != null) return parseInstagramMedia(shortcodeMedia)
            }
        }

        val videoUrlPattern = Pattern.compile(
            """https?://[^"'\s<>]*?instagram[^"'\s<>]*?\.mp4[^"'\s<>]*""",
            Pattern.CASE_INSENSITIVE
        )
        val videoMatcher = videoUrlPattern.matcher(html)
        if (videoMatcher.find()) {
            val videoUrl = videoMatcher.group()
            if (!videoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = "", title = "", author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromInstagramScriptData failed", e)
    }
    return null
}

private fun parseInstagramDuration(iso8601: String): Long {
    try {
        var total = 0L
        val hMatch = Regex("""(\d+)H""").find(iso8601)
        val mMatch = Regex("""(\d+)M""").find(iso8601)
        val sMatch = Regex("""(\d+)S""").find(iso8601)
        val dMatch = Regex("""(\d+)D""").find(iso8601)
        if (dMatch != null) total += dMatch.groupValues[1].toLong() * 86400
        if (hMatch != null) total += hMatch.groupValues[1].toLong() * 3600
        if (mMatch != null) total += mMatch.groupValues[1].toLong() * 60
        if (sMatch != null) total += sMatch.groupValues[1].toLong()
        return total
    } catch (e: Exception) { return 0L }
}
