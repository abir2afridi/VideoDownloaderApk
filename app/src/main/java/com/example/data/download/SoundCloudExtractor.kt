package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractSoundCloud(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    Log.d(EXTRACTOR_TAG, "Extracting SoundCloud: $resolved")

    val apiV2Result = extractFromSoundCloudApiV2(resolved)
    if (apiV2Result != null) return apiV2Result

    val apiV1Result = extractFromSoundCloudApiV1(resolved)
    if (apiV1Result != null) return apiV1Result

    val html = fetchPageHtml(resolved)
    if (html != null) {
        val metaResult = extractSoundCloudFromMeta(html)
        if (metaResult != null) return metaResult
        val jsonLdResult = extractFromSoundCloudJsonLd(html)
        if (jsonLdResult != null) return jsonLdResult
        val scriptResult = extractFromSoundCloudScriptData(html)
        if (scriptResult != null) return scriptResult
    }
    return null
}

private fun extractFromSoundCloudApiV2(url: String): TikTokVideoData? {
    try {
        if (!url.contains("soundcloud.com")) return null

        val request = Request.Builder().url("https://api-v2.soundcloud.com/resolve?url=$url")
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "application/json")
            .get().build()
        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val data = adapter.fromJson(body) ?: return null
            val trackId = data["id"] as? Number ?: return null
            val title = data["title"]?.toString() ?: ""
            val author = data["user"]?.let {
                (it as? Map<*, *>)?.get("username")?.toString()
            } ?: ""
            val authorId = (data["user"] as? Map<*, *>)?.get("permalink")?.toString() ?: ""
            val thumbnail = data["artwork_url"]?.toString()
                ?: (data["user"] as? Map<*, *>)?.get("avatar_url")?.toString() ?: ""
            val duration = (data["duration"] as? Number)?.toLong()?.div(1000) ?: 0L

            val streamUrl = data["stream_url"]?.toString()
            val downloadUrl = data["download_url"]?.toString()

            val mediaUrl = streamUrl ?: downloadUrl
            if (mediaUrl != null) {
                return TikTokVideoData(
                    id = trackId.toString(), title = title,
                    author = author, authorId = authorId,
                    thumbnail = thumbnail, duration = duration,
                    videoUrl = mediaUrl, videoUrlNoWatermark = null, audioUrl = mediaUrl
                )
            }
            return null
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "SoundCloud API v2 failed", e)
        return null
    }
}

private fun extractFromSoundCloudApiV1(url: String): TikTokVideoData? {
    try {
        if (!url.contains("soundcloud.com")) return null

        val clientId = extractSoundCloudClientId()
        if (clientId == null) {
            Log.w(EXTRACTOR_TAG, "SoundCloud: Could not extract client_id")
            return null
        }

        val request = Request.Builder()
            .url("https://api.soundcloud.com/resolve?url=$url&client_id=$clientId")
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "application/json")
            .get().build()
        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val data = adapter.fromJson(body) ?: return null

            val trackId = data["id"] as? Number ?: return null
            val title = data["title"]?.toString() ?: ""
            val author = (data["user"] as? Map<*, *>)?.get("username")?.toString() ?: ""
            val authorId = (data["user"] as? Map<*, *>)?.get("permalink")?.toString() ?: ""
            val thumbnail = data["artwork_url"]?.toString()
                ?: (data["user"] as? Map<*, *>)?.get("avatar_url")?.toString() ?: ""
            val duration = (data["duration"] as? Number)?.toLong()?.div(1000) ?: 0L

            val streamUrl = data["stream_url"]?.toString()
            if (streamUrl != null) {
                val finalUrl = "$streamUrl?client_id=$clientId"
                return TikTokVideoData(
                    id = trackId.toString(), title = title,
                    author = author, authorId = authorId,
                    thumbnail = thumbnail, duration = duration,
                    videoUrl = finalUrl, videoUrlNoWatermark = null, audioUrl = finalUrl
                )
            }
            return null
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "SoundCloud API v1 failed", e)
        return null
    }
}

private fun extractSoundCloudClientId(): String? {
    try {
        val request = Request.Builder().url("https://soundcloud.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            .get().build()

        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null

            val scriptPattern = Pattern.compile(
                """<script[^>]*src=["']([^"']*?/assets/[^"']*?\.js)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = scriptPattern.matcher(html)
            while (matcher.find()) {
                val scriptUrl = matcher.group(1)
                if (scriptUrl != null) {
                    val fullUrl = if (scriptUrl.startsWith("//")) "https:$scriptUrl"
                        else if (scriptUrl.startsWith("/")) "https://soundcloud.com$scriptUrl"
                        else scriptUrl
                    try {
                        val jsRequest = Request.Builder().url(fullUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .get().build()
                        extractorClient.newCall(jsRequest).execute().use { jsResponse ->
                            val jsContent = jsResponse.body?.string() ?: return@use
                            val idMatch = Regex("""client_id["']:\s*["']([a-zA-Z0-9]+)["']""").find(jsContent)
                            if (idMatch != null) return idMatch.groupValues[1]
                        }
                    } catch (_: Throwable) { continue }
                }
            }
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractSoundCloudClientId failed", e)
    }
    return null
}

private fun extractSoundCloudFromMeta(html: String): TikTokVideoData? {
    try {
        val ogAudio = extractMetaContent(html, "og:audio")
            ?: extractMetaContent(html, "og:audio:url")
            ?: extractMetaContent(html, "twitter:player:stream")
        if (!ogAudio.isNullOrBlank()) {
            val title = extractMetaContent(html, "og:title") ?: ""
            val description = extractMetaContent(html, "og:description") ?: ""
            val image = extractMetaContent(html, "og:image") ?: ""
            return TikTokVideoData(
                id = "", title = title.ifBlank { description }, author = "", authorId = "",
                thumbnail = image, duration = 0L,
                videoUrl = ogAudio, videoUrlNoWatermark = null, audioUrl = ogAudio
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractSoundCloudFromMeta failed", e)
    }
    return null
}

private fun extractFromSoundCloudJsonLd(html: String): TikTokVideoData? {
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
                if (type != "MusicRecording" && type != "AudioObject") continue

                val audioUrl = data["audio"]?.toString()
                    ?: (data["associatedAudio"] as? Map<*, *>)?.get("contentUrl")?.toString()
                    ?: data["contentUrl"]?.toString()
                if (audioUrl.isNullOrBlank()) continue

                val title = data["name"]?.toString() ?: ""
                val author = (data["byArtist"] as? Map<*, *>)?.get("name")?.toString()
                    ?: (data["author"] as? Map<*, *>)?.get("name")?.toString() ?: ""
                val thumbnail = (data["image"] as? String) ?: ""

                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = author,
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = audioUrl, videoUrlNoWatermark = null, audioUrl = audioUrl
                )
            } catch (_: Exception) { continue }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromSoundCloudJsonLd failed", e)
    }
    return null
}

private fun extractFromSoundCloudScriptData(html: String): TikTokVideoData? {
    try {
        val scriptPattern = Pattern.compile(
            """<script[^>]*>([\s\S]*?"streamUrl"[\s\S]*?)</script>""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = scriptPattern.matcher(html)
        while (matcher.find()) {
            val scriptContent = matcher.group(1) ?: continue
            val urlMatch = Regex(""""streamUrl"\s*:\s*"([^"]+)""").find(scriptContent)
            if (urlMatch != null) {
                val audioUrl = urlMatch.groupValues[1].replace("\\/", "/")
                val titleMatch = Regex(""""title"\s*:\s*"([^"]+)""").find(scriptContent)
                val title = titleMatch?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
                return TikTokVideoData(
                    id = "", title = title, author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = audioUrl, videoUrlNoWatermark = null, audioUrl = audioUrl
                )
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromSoundCloudScriptData failed", e)
    }
    return null
}
