package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractGeneric(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    Log.d(EXTRACTOR_TAG, "Extracting generic URL: $resolved")

    val html = fetchPageHtml(resolved)
    if (html == null) return null

    val metaResult = extractGenericFromMeta(html)
    if (metaResult != null) return metaResult

    val jsonLdResult = extractFromGenericJsonLd(html)
    if (jsonLdResult != null) return jsonLdResult

    val scriptResult = extractFromGenericScriptData(html, resolved)
    if (scriptResult != null) return scriptResult

    val videoTagResult = extractFromGenericVideoTag(html)
    if (videoTagResult != null) return videoTagResult

    val sourceResult = extractFromSourceTag(html)
    if (sourceResult != null) return sourceResult

    val iframeResult = extractFromGenericIframe(html, resolved)
    if (iframeResult != null) return iframeResult

    val directMediaResult = extractDirectMediaUrl(resolved)
    if (directMediaResult != null) return directMediaResult

    return null
}

private fun extractGenericFromMeta(html: String): TikTokVideoData? {
    try {
        val ogVideo = extractMetaContent(html, "og:video")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video:secure_url")
            ?: extractMetaContent(html, "twitter:player:stream")
        if (!ogVideo.isNullOrBlank()) {
            val title = extractMetaContent(html, "og:title")
                ?: extractMetaContent(html, "twitter:title") ?: ""
            val description = extractMetaContent(html, "og:description")
                ?: extractMetaContent(html, "twitter:description") ?: ""
            val thumbnail = extractMetaContent(html, "og:image")
                ?: extractMetaContent(html, "twitter:image") ?: ""

            return TikTokVideoData(
                id = "", title = title.ifBlank { description }, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = ogVideo, videoUrlNoWatermark = ogVideo, audioUrl = null
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractGenericFromMeta failed", e)
    }
    return null
}

private fun extractFromGenericJsonLd(html: String): TikTokVideoData? {
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

                var videoUrl: String? = null
                var title = ""
                var thumbnail = ""

                when (type) {
                    "VideoObject" -> {
                        videoUrl = data["contentUrl"]?.toString()
                            ?: (data["embedUrl"]?.toString())
                            ?: ((data["video"] as? Map<*, *>)?.get("contentUrl")?.toString())
                        title = data["name"]?.toString() ?: data["description"]?.toString() ?: ""
                        thumbnail = (data["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString()
                            ?: data["thumbnail"]?.toString() ?: ""
                    }
                    "Movie", "TVSeries", "Clip" -> {
                        videoUrl = data["contentUrl"]?.toString()
                            ?: data["url"]?.toString()
                        title = data["name"]?.toString() ?: ""
                        thumbnail = (data["image"] as? String)
                            ?: (data["thumbnail"] as? String) ?: ""
                    }
                    "WebPage", "Article" -> {
                        val mainEntity = data["mainEntity"] as? Map<*, *>
                        if (mainEntity != null) {
                            val entityType = mainEntity["@type"] as? String ?: ""
                            if (entityType == "VideoObject" || entityType == "Movie") {
                                videoUrl = mainEntity["contentUrl"]?.toString()
                                    ?: mainEntity["embedUrl"]?.toString()
                                title = mainEntity["name"]?.toString() ?: ""
                                thumbnail = (mainEntity["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString() ?: ""
                            }
                        }
                        val videoList = data["video"] as? List<*>
                        if (videoUrl == null && videoList != null) {
                            for (v in videoList) {
                                val videoObj = v as? Map<*, *> ?: continue
                                videoUrl = videoObj["contentUrl"]?.toString()
                                if (videoUrl != null) {
                                    title = videoObj["name"]?.toString() ?: ""
                                    thumbnail = (videoObj["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString() ?: ""
                                    break
                                }
                            }
                        }
                    }
                }

                if (!videoUrl.isNullOrBlank()) {
                    val author = (data["author"] as? Map<*, *>)?.get("name")?.toString() ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = author,
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                    )
                }
            } catch (_: Exception) { continue }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromGenericJsonLd failed", e)
    }
    return null
}

private fun extractFromGenericScriptData(html: String, pageUrl: String): TikTokVideoData? {
    try {
        val mediaScriptPattern = Pattern.compile(
            """<script[^>]*>([\s\S]*?(?:video|media|player|source|stream|download)(?:[\s\S]*?)(?:https?://[^"'\s<>]*\.(?:mp4|m3u8|webm|avi|mkv|mov)[^"'\s<>]*)(?:[\s\S]*?))</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = mediaScriptPattern.matcher(html)
        while (matcher.find()) {
            val scriptContent = matcher.group(1) ?: continue
            val urlMatch = Regex("""https?://[^"'\s<>]*\.(mp4|m3u8|webm)[^"'\s<>]*""").find(scriptContent)
            if (urlMatch != null) {
                val videoUrl = urlMatch.value
                if (videoUrl.isNotBlank()) {
                    val titleMatch = Regex(""""title"\s*:\s*"([^"]+)""").find(scriptContent)
                    val title = titleMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = "", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                    )
                }
            }
        }

        val scriptSrcPattern = Pattern.compile(
            """<script[^>]+src=["']([^"']+)["'][^>]*>""",
            Pattern.CASE_INSENSITIVE
        )
        val jsMatcher = scriptSrcPattern.matcher(html)
        while (jsMatcher.find()) {
            val jsUrl = jsMatcher.group(1) ?: continue
            if (jsUrl.contains(".js") && !jsUrl.contains("jquery") && !jsUrl.contains("bootstrap")) {
                try {
                    val fullJsUrl = resolveRelativeUrl(jsUrl, pageUrl)
                    if (fullJsUrl == null) continue
                    val jsRequest = Request.Builder().url(fullJsUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .get().build()
                    extractorClient.newCall(jsRequest).execute().use { jsResponse ->
                        if (!jsResponse.isSuccessful) return@use
                        val jsContent = jsResponse.body?.string() ?: return@use

                        val jsUrlMatch = Regex("""https?://[^"'\s<>]*\.(mp4|m3u8|webm)[^"'\s<>]*""").find(jsContent)
                        if (jsUrlMatch != null) {
                            val videoUrl = jsUrlMatch.value
                            return TikTokVideoData(
                                id = "", title = "", author = "", authorId = "",
                                thumbnail = "", duration = 0L,
                                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                            )
                        }
                    }
                } catch (_: Throwable) { continue }
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromGenericScriptData failed", e)
    }
    return null
}

private fun extractFromGenericVideoTag(html: String): TikTokVideoData? {
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
        Log.w(EXTRACTOR_TAG, "extractFromGenericVideoTag failed", e)
    }
    return null
}

private fun extractFromSourceTag(html: String): TikTokVideoData? {
    try {
        val sourcePattern = Pattern.compile(
            """<source[^>]+src=["']([^"']+\.(mp4|webm|m3u8)[^"']*)["']""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = sourcePattern.matcher(html)
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
        Log.w(EXTRACTOR_TAG, "extractFromSourceTag failed", e)
    }
    return null
}

private fun extractFromGenericIframe(html: String, originalUrl: String): TikTokVideoData? {
    try {
        val iframePattern = Pattern.compile(
            """<iframe[^>]+src=["']([^"']+)["'][^>]*>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = iframePattern.matcher(html)
        while (matcher.find()) {
            val src = matcher.group(1) ?: continue
            val resolvedIframeUrl = resolveRelativeUrl(src, originalUrl)
            if (resolvedIframeUrl != null) {
                val iframeHtml = fetchPageHtml(resolvedIframeUrl)
                if (iframeHtml != null) {
                    val iframeResult = extractGeneric(resolvedIframeUrl)
                    if (iframeResult != null) return iframeResult
                }
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromGenericIframe failed", e)
    }
    return null
}

private fun extractDirectMediaUrl(url: String): TikTokVideoData? {
    try {
        if (url.matches(Regex(""".*\.(mp4|webm|m3u8|avi|mkv|mov)(\?.*)?$"""))) {
            return TikTokVideoData(
                id = "", title = "", author = "", authorId = "",
                thumbnail = "", duration = 0L,
                videoUrl = url, videoUrlNoWatermark = url, audioUrl = null
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractDirectMediaUrl failed", e)
    }
    return null
}

private fun resolveRelativeUrl(relativeUrl: String, baseUrl: String): String? {
    if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) return relativeUrl
    if (relativeUrl.startsWith("//")) return "https:$relativeUrl"

    return try {
        val base = java.net.URL(baseUrl)
        java.net.URL(base, relativeUrl).toString()
    } catch (e: Exception) {
        null
    }
}
