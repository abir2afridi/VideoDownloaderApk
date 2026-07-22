package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractReddit(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    Log.d(EXTRACTOR_TAG, "Extracting Reddit post: $resolved")

    val redditApiResult = extractFromRedditJson(resolved)
    if (redditApiResult != null) return redditApiResult

    val oembedResult = extractFromRedditOembed(resolved)
    if (oembedResult != null) return oembedResult

    val html = fetchPageHtml(resolved)
    if (html != null) {
        val metaResult = extractRedditFromMeta(html)
        if (metaResult != null) return metaResult
        val jsonLdResult = extractFromRedditJsonLd(html)
        if (jsonLdResult != null) return jsonLdResult
        val scriptResult = extractFromRedditScriptData(html)
        if (scriptResult != null) return scriptResult
    }
    return null
}

private fun extractFromRedditJson(url: String): TikTokVideoData? {
    try {
        val jsonUrl = if (url.endsWith("/")) "${url}.json" else "$url.json"
        val request = Request.Builder().url(jsonUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .get().build()

        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val listAdapter = extractorMoshi.adapter<List<Any?>>(Types.newParameterizedType(List::class.java, Any::class.java))
            val rootList = listAdapter.fromJson(body) ?: return null
            val first = rootList.firstOrNull() as? Map<*, *> ?: return null
            val data = first["data"] as? Map<*, *> ?: return null
            val children = data["children"] as? List<*> ?: return null
            val firstChild = children.firstOrNull() as? Map<*, *> ?: return null
            val childData = firstChild["data"] as? Map<*, *> ?: return null
            return parseRedditPost(childData)
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "Reddit JSON API failed", e)
        return null
    }
}

private fun parseRedditPost(data: Map<*, *>): TikTokVideoData? {
    val title = data["title"]?.toString() ?: ""
    val author = data["author"]?.toString() ?: ""
    val thumbnail = data["thumbnail"]?.toString() ?: ""
    val domain = data["domain"]?.toString() ?: ""

    val isVideo = data["is_video"] as? Boolean ?: false
    val isGallery = data["is_gallery"] as? Boolean ?: false
    val media = data["media"] as? Map<*, *>
    val secureMedia = data["secure_media"] as? Map<*, *>
    val mediaEmbed = data["media_embed"] as? Map<*, *>
    val secureMediaEmbed = data["secure_media_embed"] as? Map<*, *>

    val redditVideo = (media ?: secureMedia)?.let {
        (it["reddit_video"] as? Map<*, *>)
    }

    if (isVideo && redditVideo != null) {
        val fallbackUrl = redditVideo["fallback_url"]?.toString()
            ?: redditVideo["hls_url"]?.toString()
        val duration = (redditVideo["duration"] as? Number)?.toLong() ?: 0L
        if (!fallbackUrl.isNullOrBlank()) {
            val audioUrl = redditVideo["audio_url"]?.toString()
                ?: fallbackUrl.replace("/DASH_", "/DASH_AUDIO_")
            return TikTokVideoData(
                id = data["id"]?.toString() ?: "", title = title,
                author = author, authorId = author,
                thumbnail = thumbnail, duration = duration,
                videoUrl = fallbackUrl, videoUrlNoWatermark = fallbackUrl,
                audioUrl = if (audioUrl != fallbackUrl) audioUrl else null
            )
        }
    }

    val embedContent = secureMediaEmbed?.get("content")?.toString()
        ?: mediaEmbed?.get("content")?.toString()
    if (embedContent != null) {
        val srcMatch = Regex("""src=["']([^"']+)["']""").find(embedContent)
        if (srcMatch != null) {
            val embedUrl = srcMatch.groupValues[1]
            val embedResult = extractReddit(embedUrl)
            if (embedResult != null) return embedResult
        }
    }

    if (isGallery) {
        val galleryData = data["media_metadata"] as? Map<*, *>
        if (galleryData != null) {
            for ((_, value) in galleryData) {
                val item = value as? Map<*, *> ?: continue
                if (item["e"]?.toString() == "Image") {
                    val source = item["s"] as? Map<*, *> ?: continue
                    val imgUrl = source["u"]?.toString()?.replace("&amp;", "&")
                    if (imgUrl != null) {
                        return TikTokVideoData(
                            id = "", title = title, author = author, authorId = author,
                            thumbnail = thumbnail, duration = 0L,
                            videoUrl = imgUrl, videoUrlNoWatermark = imgUrl, audioUrl = null
                        )
                    }
                }
            }
        }
        return null
    }

    val domainLower = domain.lowercase()
    if (domainLower.contains("v.redd.it") && redditVideo != null) {
        val fallbackUrl = redditVideo["fallback_url"]?.toString()
        if (!fallbackUrl.isNullOrBlank()) {
            return TikTokVideoData(
                id = "", title = title, author = author, authorId = author,
                thumbnail = thumbnail, duration = (redditVideo["duration"] as? Number)?.toLong() ?: 0L,
                videoUrl = fallbackUrl, videoUrlNoWatermark = fallbackUrl, audioUrl = null
            )
        }
    }

    val urlStr = data["url"]?.toString()
    if (urlStr != null && !urlStr.contains("reddit.com")) {
        val externalResult = extractGeneric(urlStr)
        if (externalResult != null) return externalResult
    }

    return null
}

private fun extractFromRedditOembed(url: String): TikTokVideoData? {
    try {
        val oembedUrl = "https://www.reddit.com/oembed?url=$url&format=json"
        val request = Request.Builder().url(oembedUrl)
            .header("User-Agent", "Mozilla/5.0")
            .get().build()

        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(body) ?: return null
            val htmlEmbed = root["html"]?.toString() ?: ""

            val srcMatch = Regex("""src=["']([^"']+)["']""").find(htmlEmbed)
            if (srcMatch != null) {
                val embedUrl = srcMatch.groupValues[1]
                val embedResult = extractReddit(embedUrl)
                if (embedResult != null) return embedResult
            }
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "Reddit oEmbed failed", e)
    }
    return null
}

private fun extractRedditFromMeta(html: String): TikTokVideoData? {
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
        Log.w(EXTRACTOR_TAG, "extractRedditFromMeta failed", e)
    }
    return null
}

private fun extractFromRedditJsonLd(html: String): TikTokVideoData? {
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

                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = author,
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = contentUrl, videoUrlNoWatermark = contentUrl, audioUrl = null
                )
            } catch (_: Exception) { continue }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromRedditJsonLd failed", e)
    }
    return null
}

private fun extractFromRedditScriptData(html: String): TikTokVideoData? {
    try {
        val scriptPattern = Pattern.compile(
            """<script[^>]*>([\s\S]*?"playableMedia"[^:]*:[^}]*?url[\s\S]*?)</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = scriptPattern.matcher(html)
        while (matcher.find()) {
            val scriptContent = matcher.group(1) ?: continue
            val urlMatch = Regex(""""url"\s*:\s*"([^"]+)""").find(scriptContent)
            if (urlMatch != null) {
                val videoUrl = urlMatch.groupValues[1].replace("\\/", "/")
                val titleMatch = Regex(""""title"\s*:\s*"([^"]+)""").find(scriptContent)
                val title = titleMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
                return TikTokVideoData(
                    id = "", title = title, author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromRedditScriptData failed", e)
    }
    return null
}
