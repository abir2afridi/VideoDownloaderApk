package com.example.data.download

import android.util.Log
import com.squareup.moshi.Types
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

internal fun extractTikTok(url: String): TikTokVideoData? {
    val tikwmResult = extractFromTikwm(url)
    if (tikwmResult != null) {
        Log.d(EXTRACTOR_TAG, "Success via TikWM API: ${tikwmResult.title}")
        return tikwmResult
    }

    val resolvedUrl = resolveRedirect(url)
    val itemId = extractItemId(resolvedUrl ?: url)
        ?: extractItemIdFromHtml(url)
    if (itemId == null) return null

    Log.d(EXTRACTOR_TAG, "Extracting TikTok video ID: $itemId")

    val mobileResult = extractFromMobileApi(itemId)
    if (mobileResult != null) {
        Log.d(EXTRACTOR_TAG, "Success via Mobile API: ${mobileResult.title}")
        return mobileResult
    }

    val html = fetchPageHtml(resolvedUrl ?: url)
    if (html != null) {
        val uniResult = extractFromUniversalData(html)
        if (uniResult != null) return uniResult
        val initResult = extractFromInitProps(html)
        if (initResult != null) return initResult
        val sigiResult = extractFromSigiData(html)
        if (sigiResult != null) return sigiResult
        val cdnResult = extractFromCdnUrlPattern(html)
        if (cdnResult != null) return cdnResult
        val metaResult = extractTikTokFromMetaTags(html)
        if (metaResult != null) return metaResult
        val jsonLdResult = extractFromJsonLd(html)
        if (jsonLdResult != null) return jsonLdResult
        val videoTagResult = extractFromVideoTagTikTok(html)
        if (videoTagResult != null) return videoTagResult
    }

    val oembedResult = extractFromOembed(itemId)
    if (oembedResult != null) {
        Log.d(EXTRACTOR_TAG, "Success via oEmbed: ${oembedResult.title}")
        return oembedResult
    }

    return null
}

private fun extractFromTikwm(url: String): TikTokVideoData? {
    try {
        val tikwmClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val formBody = FormBody.Builder()
            .add("url", url)
            .add("hd", "1")
            .build()

        val request = Request.Builder()
            .url("https://www.tikwm.com/api/")
            .post(formBody)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        tikwmClient.newCall(request).execute().use { response ->
            val jsonStr = response.body?.string() ?: return null
            Log.d(EXTRACTOR_TAG, "TikWM response length: ${jsonStr.length}")

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(jsonStr) ?: return null

            val code = (root["code"] as? Number)?.toInt() ?: -1
            if (code != 0) {
                Log.w(EXTRACTOR_TAG, "TikWM API error code: $code, msg: ${root["msg"]}")
                return null
            }

            val data = root["data"] as? Map<*, *> ?: return null

            val videoId = data["id"]?.toString() ?: ""
            val title = data["title"]?.toString() ?: ""
            val duration = (data["duration"] as? Number)?.toLong() ?: 0L
            val thumbnail = data["cover"]?.toString() ?: ""

            val authorMap = data["author"] as? Map<*, *>
            val author = authorMap?.get("nickname")?.toString() ?: ""
            val authorId = authorMap?.get("unique_id")?.toString() ?: ""

            val videoUrlHd = data["hdplay"]?.toString()?.ifBlank { null }
            val videoUrlSd = data["play"]?.toString()?.ifBlank { null }
            val videoUrlWatermarked = data["wmplay"]?.toString()?.ifBlank { null }
            val videoUrlNoWatermark = videoUrlHd ?: videoUrlSd
            val videoUrl = videoUrlWatermarked ?: videoUrlNoWatermark
            val audioUrl = data["music"]?.toString()?.ifBlank { null }

            Log.d(EXTRACTOR_TAG, "TikWM: id=$videoId, title=${title.take(40)}, hasHd=${videoUrlHd != null}, hasSd=${videoUrlSd != null}, hasWm=${videoUrlWatermarked != null}, hasAudio=${audioUrl != null}")

            if (videoUrl.isNullOrBlank()) return null

            return TikTokVideoData(
                id = videoId, title = title, author = author, authorId = authorId,
                thumbnail = thumbnail, duration = duration,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrlNoWatermark, audioUrl = audioUrl
            )
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFromTikwm failed", e)
        return null
    }
}

private fun extractFromMobileApi(itemId: String): TikTokVideoData? {
    try {
        val url = "https://$MOBILE_API_HOST/aweme/v1/multi/aweme/detail/"
        val formBody = FormBody.Builder()
            .add("aweme_ids", "[$itemId]")
            .add("request_source", "0")
            .build()

        val requestBuilder = Request.Builder().url(url)
        requestBuilder.header("User-Agent", MOBILE_UA)
        requestBuilder.header("Accept", "application/json")
        requestBuilder.header("X-Requested-With", "XMLHttpRequest")
        requestBuilder.post(formBody)

        extractorClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null

            Log.d(EXTRACTOR_TAG, "Mobile API response length: ${responseBody.length}")

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(responseBody) ?: return null

            val awemeDetails = root["aweme_details"] as? List<*> ?: return null
            val firstAweme = awemeDetails.firstOrNull() as? Map<*, *> ?: return null

            return parseAweme(firstAweme)
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFromMobileApi failed", e)
        return null
    }
}

internal fun parseAweme(aweme: Map<*, *>): TikTokVideoData? {
    try {
        val awemeId = aweme["aweme_id"]?.toString() ?: ""
        val desc = aweme["desc"]?.toString() ?: ""

        val authorMap = aweme["author"] as? Map<*, *>
        val author = authorMap?.get("nickname")?.toString() ?: ""
        val authorUniqueId = authorMap?.get("unique_id")?.toString() ?: authorMap?.get("short_id")?.toString() ?: ""

        val videoInfo = aweme["video"] as? Map<*, *> ?: return null

        val duration = (aweme["duration"] as? Number)?.toLong()
            ?: (videoInfo["duration"] as? Number)?.toLong() ?: 0L

        val dynamicCover = videoInfo["dynamic_cover"] as? Map<*, *>
        val originCover = videoInfo["origin_cover"] as? Map<*, *>
        val coverUrls = (dynamicCover?.get("url_list") as? List<*>)?.filterIsInstance<String>()
            ?: (originCover?.get("url_list") as? List<*>)?.filterIsInstance<String>()
        val thumbnail = coverUrls?.firstOrNull() ?: ""

        val videoUrl = extractFromBitrateInfo(videoInfo)
        val videoUrlNoWatermark = extractUrlFromAddr(videoInfo, "download_addr")
            ?: extractUrlFromAddr(videoInfo, "play_addr")
        val audioInfo = aweme["music"] as? Map<*, *>
        val audioUrl = extractUrlFromAddr(audioInfo ?: emptyMap<Any, Any>(), "play_url")

        if (videoUrl == null && videoUrlNoWatermark == null) return null

        return TikTokVideoData(
            id = awemeId, title = desc, author = author, authorId = authorUniqueId,
            thumbnail = thumbnail, duration = duration,
            videoUrl = videoUrl ?: videoUrlNoWatermark,
            videoUrlNoWatermark = videoUrlNoWatermark ?: videoUrl,
            audioUrl = audioUrl
        )
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "parseAweme failed", e)
        return null
    }
}

private fun extractUrlFromAddr(map: Map<*, *>, key: String): String? {
    try {
        val addr = map[key] as? Map<*, *> ?: return null
        val urlList = addr["url_list"] as? List<*> ?: return null
        for (urlObj in urlList) {
            val url = urlObj?.toString() ?: continue
            if (url.isNotBlank() && !url.contains("log.byteoversea.com")) {
                return url.replace("https://", "http://")
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractUrlFromAddr($key) failed", e)
    }
    return null
}

private fun extractFromBitrateInfo(videoInfo: Map<*, *>): String? {
    try {
        val bitrateInfo = videoInfo["bitrate_info"] as? Map<*, *>
        if (bitrateInfo != null) {
            for ((_, value) in bitrateInfo) {
                val info = value as? Map<*, *> ?: continue
                val playUrl = info["play_url"] as? Map<*, *> ?: continue
                val urlList = playUrl["url_list"] as? List<*> ?: continue
                for (urlObj in urlList) {
                    val url = urlObj?.toString() ?: continue
                    if (url.isNotBlank() && url.contains("http")) {
                        return url.replace("https://", "http://")
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromBitrateInfo failed", e)
    }
    return null
}

private fun extractFromUniversalData(html: String): TikTokVideoData? {
    try {
        val pattern = Pattern.compile("""<script[^>]*id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>([\s\S]*?)</script>""")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val jsonStr = matcher.group(1) ?: return null
            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(jsonStr) ?: return null

            val defaultScope = root["__DEFAULT_SCOPE__"] as? Map<*, *>
                ?: root["defaultScope"] as? Map<*, *> ?: return null
            val seo = defaultScope["seo.abtest"] as? Map<*, *>
                ?: defaultScope["seo"] as? Map<*, *> ?: return null
            val canonical = seo["canonical"] as? String ?: ""
            val itemId = extractItemId(canonical) ?: return null
            val videoData = defaultScope["videoData"] as? Map<*, *>
                ?: defaultScope["video.data"] as? Map<*, *> ?: return null

            val author = (videoData["author"] as? String)?.replace("@", "") ?: ""
            val title = videoData["title"]?.toString() ?: videoData["desc"]?.toString() ?: ""
            val duration = (videoData["duration"] as? Number)?.toLong() ?: 0L
            val thumbnail = videoData["thumbnail"]?.toString() ?: videoData["cover"]?.toString() ?: ""

            var videoUrl: String? = null
            var videoUrlNoWatermark: String? = null
            var audioUrl: String? = null

            val videoUrls = videoData["videoUrls"] as? List<*>
            if (videoUrls != null) {
                for (vu in videoUrls) {
                    val entry = vu as? Map<*, *> ?: continue
                    val url = entry["url"]?.toString() ?: continue
                    val dataSize = entry["dataSize"]?.toString() ?: ""
                    val label = entry["label"]?.toString() ?: ""

                    if (label.contains("watermark", true)) {
                        videoUrl = url
                    } else {
                        videoUrlNoWatermark = url
                    }
                }
            }

            val musicInfo = videoData["musicInfo"] as? Map<*, *>
            if (musicInfo != null) {
                val musicUrl = musicInfo["playUrl"]?.toString()
                    ?: extractUrlFromAddr(musicInfo, "play_url")
                if (!musicUrl.isNullOrBlank()) audioUrl = musicUrl
            }

            if (videoUrl == null && videoUrlNoWatermark == null) return null

            return TikTokVideoData(
                id = itemId, title = title, author = author, authorId = author,
                thumbnail = thumbnail, duration = duration,
                videoUrl = videoUrl ?: videoUrlNoWatermark,
                videoUrlNoWatermark = videoUrlNoWatermark ?: videoUrl,
                audioUrl = audioUrl
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromUniversalData failed", e)
    }
    return null
}

private fun extractFromInitProps(html: String): TikTokVideoData? {
    try {
        val pattern = Pattern.compile("""<script[^>]*id="__INIT_PROPS__"[^>]*>([\s\S]*?)</script>""")
        val matcher = pattern.matcher(html)
        if (!matcher.find()) return null

        val jsonStr = matcher.group(1) ?: return null
        val rootAdapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
        val root = rootAdapter.fromJson(jsonStr) ?: return null

        val page = root["page"] as? Map<*, *> ?: return null
        val videoData = page["videoData"] as? Map<*, *>
            ?: page["video"] as? Map<*, *> ?: return null

        val id = videoData["id"]?.toString() ?: ""
        val title = videoData["title"]?.toString() ?: videoData["desc"]?.toString() ?: ""
        val author = videoData["author"]?.toString() ?: ""
        val duration = (videoData["duration"] as? Number)?.toLong() ?: 0L
        val thumbnail = videoData["thumbnail"]?.toString() ?: videoData["cover"]?.toString() ?: ""

        var videoUrl: String? = null
        var videoUrlNoWatermark: String? = null
        var audioUrl: String? = null

        val subVideoList = videoData["subVideoList"] as? List<*>
        if (subVideoList != null) {
            for (sv in subVideoList) {
                val entry = sv as? Map<*, *> ?: continue
                val url = entry["url"]?.toString() ?: continue
                val label = entry["label"]?.toString() ?: ""
                if (label.contains("watermark", true)) videoUrl = url
                else videoUrlNoWatermark = url
            }
        }

        if (videoUrl == null && videoUrlNoWatermark == null) return null

        return TikTokVideoData(
            id = id, title = title, author = author, authorId = author,
            thumbnail = thumbnail, duration = duration,
            videoUrl = videoUrl ?: videoUrlNoWatermark,
            videoUrlNoWatermark = videoUrlNoWatermark ?: videoUrl,
            audioUrl = audioUrl
        )
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromInitProps failed", e)
        return null
    }
}

private fun extractFromSigiData(html: String): TikTokVideoData? {
    try {
        val pattern = Pattern.compile("""<script[^>]*id="__SIGI_STATE__"[^>]*>([\s\S]*?)</script>""")
        val matcher = pattern.matcher(html)
        if (!matcher.find()) return null

        val jsonStr = matcher.group(1) ?: return null
        val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
        val root = adapter.fromJson(jsonStr) ?: return null

        val itemModule = root["ItemModule"] as? Map<*, *> ?: return null
        val firstEntry = itemModule.entries.firstOrNull() ?: return null
        val itemData = firstEntry.value as? Map<*, *> ?: return null

        val id = itemData["id"]?.toString() ?: ""
        val desc = itemData["desc"]?.toString() ?: ""
        val author = itemData["author"]?.toString() ?: itemData["nickname"]?.toString() ?: ""
        val duration = (itemData["duration"] as? Number)?.toLong() ?: 0L

        val video = itemData["video"] as? Map<*, *> ?: return null
        val dynamicCover = video["dynamicCover"]?.toString() ?: video["cover"]?.toString() ?: ""

        var videoUrl: String? = null
        var videoUrlNoWatermark: String? = null

        val downloadAddr = video["downloadAddr"]?.toString()
        val playAddr = video["playAddr"]?.toString()
        if (downloadAddr != null && downloadAddr.contains("http")) {
            videoUrlNoWatermark = downloadAddr
        }
        if (playAddr != null && playAddr.contains("http")) {
            videoUrl = playAddr
        }

        val bitrate = video["bitrateInfo"] as? List<*>
        if (bitrate != null && videoUrl == null && videoUrlNoWatermark == null) {
            for (bi in bitrate) {
                val info = bi as? Map<*, *> ?: continue
                val dataSize = info["DataSize"] as? Number ?: continue
                val playUrlMap = info["PlayUrl"] as? Map<*, *> ?: continue
                val urlList = playUrlMap["UrlList"] as? List<*> ?: continue
                val url = urlList.firstOrNull()?.toString() ?: continue
                if (url.contains("http")) {
                    videoUrl = url
                    break
                }
            }
        }

        if (videoUrl == null && videoUrlNoWatermark == null) return null

        return TikTokVideoData(
            id = id, title = desc, author = author, authorId = author,
            thumbnail = dynamicCover, duration = duration,
            videoUrl = videoUrl ?: videoUrlNoWatermark,
            videoUrlNoWatermark = videoUrlNoWatermark ?: videoUrl,
            audioUrl = null
        )
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromSigiData failed", e)
        return null
    }
}

private fun extractFromCdnUrlPattern(html: String): TikTokVideoData? {
    try {
        val cdnPattern = Pattern.compile(
            """(https?://[^"'\s<>]+?tiktokcdn[^"'\s<>]*?/video/[^"'\s<>]+?\.mp4[^"'\s<>]*)""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = cdnPattern.matcher(html)
        if (matcher.find()) {
            val videoUrl = matcher.group(1)?.replace("\\u002F", "/")?.replace("\\/", "/")
                ?.replace("https://", "http://")
            if (!videoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = "", title = "", author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                )
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromCdnUrlPattern failed", e)
    }
    return null
}

private fun extractTikTokFromMetaTags(html: String): TikTokVideoData? {
    try {
        val ogVideoUrl = extractMetaContent(html, "og:video")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video:secure_url")
        if (!ogVideoUrl.isNullOrBlank()) {
            val title = extractMetaContent(html, "og:title") ?: ""
            val thumbnail = extractMetaContent(html, "og:image") ?: ""
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractTikTokFromMetaTags failed", e)
    }
    return null
}

private fun extractFromJsonLd(html: String): TikTokVideoData? {
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
                val thumbnail = (data["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString()
                    ?: data["thumbnail"]?.toString() ?: ""
                val durationRaw = data["duration"]?.toString() ?: ""
                val duration = parseDuration(durationRaw)

                val contentUrl = data["contentUrl"]?.toString()
                val embedUrl = data["embedUrl"]?.toString()
                val videoUrl = contentUrl ?: embedUrl

                if (videoUrl.isNullOrBlank()) continue

                return TikTokVideoData(
                    id = "", title = title, author = "", authorId = "",
                    thumbnail = thumbnail, duration = duration,
                    videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            } catch (_: Exception) { continue }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromJsonLd failed", e)
    }
    return null
}

private fun extractFromVideoTagTikTok(html: String): TikTokVideoData? {
    try {
        val pattern = Pattern.compile("""<video[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val videoUrl = matcher.group(1)?.replace("https://", "http://")
            if (!videoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = "", title = "", author = "", authorId = "",
                    thumbnail = "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromVideoTagTikTok failed", e)
    }
    return null
}

private fun extractFromOembed(itemId: String): TikTokVideoData? {
    try {
        val embedUrl = "https://www.tiktok.com/oembed?url=https://www.tiktok.com/video/$itemId"
        val request = Request.Builder().url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get().build()

        extractorClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(body) ?: return null

            val title = root["title"]?.toString() ?: ""
            val author = root["author_name"]?.toString() ?: ""
            val authorId = root["author_unique_id"]?.toString() ?: ""
            val thumbnail = root["thumbnail_url"]?.toString() ?: ""
            val htmlEmbed = root["html"]?.toString() ?: ""

            val videoUrlMatch = Regex("""src=["']([^"']+)["']""").find(htmlEmbed)
            val videoUrl = videoUrlMatch?.groupValues?.get(1)

            if (videoUrl.isNullOrBlank()) return null

            return TikTokVideoData(
                id = itemId, title = title, author = author, authorId = authorId,
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "extractFromOembed failed", e)
        return null
    }
}

internal fun parseDuration(iso8601: String): Long {
    try {
        val hMatch = Regex("""(\d+)H""").find(iso8601)
        val mMatch = Regex("""(\d+)M""").find(iso8601)
        val sMatch = Regex("""(\d+)S""").find(iso8601)
        var seconds = 0L
        if (hMatch != null) seconds += hMatch.groupValues[1].toLong() * 3600
        if (mMatch != null) seconds += mMatch.groupValues[1].toLong() * 60
        if (sMatch != null) seconds += sMatch.groupValues[1].toLong()
        return seconds
    } catch (e: Exception) {
        return 0L
    }
}
