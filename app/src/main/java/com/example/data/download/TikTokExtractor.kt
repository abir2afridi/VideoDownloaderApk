package com.example.data.download

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class TikTokVideoData(
    val id: String,
    val title: String,
    val author: String,
    val authorId: String,
    val thumbnail: String,
    val duration: Long,
    val videoUrl: String?,
    val videoUrlNoWatermark: String?,
    val audioUrl: String?
)

object TikTokExtractor {
    private const val TAG = "TikTokExtractor"
    private const val MOBILE_API_HOST = "api16-normal-c-useast1a.tiktokv.com"
    private val DEVICE_ID = (7250000000000000000L + (Math.random() * 750000000000000L).toLong()).toString()
    private const val APP_VERSION = "35.1.3"
    private const val MOBILE_UA = "com.zhiliaoapp.musically/2023501030 (Linux; U; Android 13; en_US; Pixel 7; Build/TD1A.220804.031; Cronet/58.0.2991.0)"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(TikTokCookieStore.cookieJar)
            .build()
    }

    private fun getDefaultHeaders(isMobile: Boolean = false): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept-Encoding" to "gzip, deflate, br"
        )
        if (isMobile) {
            headers["User-Agent"] = MOBILE_UA
        } else {
            headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        }
        return headers
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun extract(url: String): Result<TikTokVideoData> {
        return try {
            TikTokCookieStore.seed()

            // Strategy 0: TikWM API (most reliable — free, no auth, 5000 req/day)
            val tikwmResult = extractFromTikwm(url)
            if (tikwmResult != null) {
                Log.d(TAG, "Success via TikWM API: ${tikwmResult.title}")
                return Result.success(tikwmResult)
            }

            val resolvedUrl = resolveRedirect(url)
            val itemId = extractItemId(resolvedUrl ?: url)
                ?: extractItemIdFromHtml(url)
            if (itemId == null) {
                return Result.failure(Exception("Could not extract video ID from URL"))
            }

            Log.d(TAG, "Extracting TikTok video ID: $itemId")

            // Strategy 1: Mobile App API (requires signing — may fail)
            val mobileResult = extractFromMobileApi(itemId)
            if (mobileResult != null) {
                Log.d(TAG, "Success via Mobile API: ${mobileResult.title}")
                return Result.success(mobileResult)
            }

            // Strategy 2: Fetch HTML and scrape
            val html = fetchPageHtml(resolvedUrl ?: url, client)
            if (html != null) {
                // Strategy 2a: Parse __UNIVERSAL_DATA_FOR_REHYDRATION__
                val uniResult = extractFromUniversalData(html)
                if (uniResult != null) {
                    Log.d(TAG, "Success via __UNIVERSAL_DATA_FOR_REHYDRATION__: ${uniResult.title}")
                    return Result.success(uniResult)
                }

                // Strategy 2b: Parse __INIT_PROPS__
                val initResult = extractFromInitProps(html)
                if (initResult != null) {
                    Log.d(TAG, "Success via __INIT_PROPS__: ${initResult.title}")
                    return Result.success(initResult)
                }

                // Strategy 2c: Parse SIGI_STATE
                val sigiResult = extractFromSigiData(html)
                if (sigiResult != null) {
                    Log.d(TAG, "Success via SIGI_STATE: ${sigiResult.title}")
                    return Result.success(sigiResult)
                }

                // Strategy 2d: Regex for video CDN URLs directly
                val cdnResult = extractFromCdnUrlPattern(html)
                if (cdnResult != null) {
                    Log.d(TAG, "Success via CDN URL pattern: ${cdnResult.title}")
                    return Result.success(cdnResult)
                }

                // Strategy 2e: Parse og:video meta tag
                val metaResult = extractFromMetaTags(html)
                if (metaResult != null) {
                    Log.d(TAG, "Success via meta tags: ${metaResult.title}")
                    return Result.success(metaResult)
                }

                // Strategy 2f: JSON-LD VideoObject (NexLoad-inspired)
                val jsonLdResult = extractFromJsonLd(html)
                if (jsonLdResult != null) {
                    Log.d(TAG, "Success via JSON-LD VideoObject: ${jsonLdResult.title}")
                    return Result.success(jsonLdResult)
                }

                // Strategy 2g: HTML5 <video> tag (NexLoad-inspired)
                val videoTagResult = extractFromVideoTag(html)
                if (videoTagResult != null) {
                    Log.d(TAG, "Success via <video> tag: ${videoTagResult.title}")
                    return Result.success(videoTagResult)
                }
            }

            // Strategy 3: oEmbed API
            val oembedResult = extractFromOembed(itemId)
            if (oembedResult != null) {
                Log.d(TAG, "Success via oEmbed: ${oembedResult.title}")
                return Result.success(oembedResult)
            }

            Log.e(TAG, "All extraction strategies failed for $itemId")
            Result.failure(Exception("Could not find video URL in TikTok page"))
        } catch (e: IOException) {
            Log.e(TAG, "Network error extracting TikTok video", e)
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "Failed to connect to TikTok"}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting TikTok video", e)
            Result.failure(Exception("Extraction error: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    // ── TikWM API (primary strategy — free, no auth) ──────────────

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

            val response = tikwmClient.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return null
            Log.d(TAG, "TikWM response length: ${jsonStr.length}")

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(jsonStr) ?: return null

            val code = (root["code"] as? Number)?.toInt() ?: -1
            if (code != 0) {
                Log.w(TAG, "TikWM API error code: $code, msg: ${root["msg"]}")
                return null
            }

            val data = root["data"] as? Map<*, *> ?: return null

            val videoId = data["id"]?.toString() ?: ""
            val title = data["title"]?.toString() ?: ""
            val duration = (data["duration"] as? Number)?.toLong() ?: 0L
            val thumbnail = data["cover"]?.toString() ?: ""
            val playCount = (data["play_count"] as? Number)?.toLong() ?: 0L

            // Extract author info
            val authorMap = data["author"] as? Map<*, *>
            val author = authorMap?.get("nickname")?.toString() ?: ""
            val authorId = authorMap?.get("unique_id")?.toString() ?: ""

            // Extract video URLs
            // hdplay = HD no watermark, play = SD no watermark, wmplay = watermarked
            val videoUrlHd = data["hdplay"]?.toString()?.ifBlank { null }
            val videoUrlSd = data["play"]?.toString()?.ifBlank { null }
            val videoUrlWatermarked = data["wmplay"]?.toString()?.ifBlank { null }

            // videoUrlNoWatermark = best no-watermark (HD preferred over SD)
            val videoUrlNoWatermark = videoUrlHd ?: videoUrlSd

            // videoUrl = watermarked version (for quality picker "With Watermark" option)
            // Falls back to no-watermark if watermarked not available
            val videoUrl = videoUrlWatermarked ?: videoUrlNoWatermark

            // Audio URL
            val audioUrl = data["music"]?.toString()?.ifBlank { null }

            Log.d(TAG, "TikWM: id=$videoId, title=${title.take(40)}, hasHd=${videoUrlHd != null}, hasSd=${videoUrlSd != null}, hasWm=${videoUrlWatermarked != null}, hasAudio=${audioUrl != null}")

            if (videoUrl.isNullOrBlank()) return null

            return TikTokVideoData(
                id = videoId,
                title = title,
                author = author,
                authorId = authorId,
                thumbnail = thumbnail,
                duration = duration,
                videoUrl = videoUrl,
                videoUrlNoWatermark = videoUrlNoWatermark,
                audioUrl = audioUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "extractFromTikwm failed", e)
            return null
        }
    }

    // ── Mobile App API (requires signing) ─────────────────────────

    private fun extractFromMobileApi(itemId: String): TikTokVideoData? {
        try {
            val url = "https://$MOBILE_API_HOST/aweme/v1/multi/aweme/detail/"
            val formBody = FormBody.Builder()
                .add("aweme_ids", "[$itemId]")
                .add("request_source", "0")
                .build()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(formBody)
            
            getDefaultHeaders(true).forEach { (k, v) -> requestBuilder.header(k, v) }
            
            val request = requestBuilder
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Mobile API returned HTTP ${response.code}")
                return null
            }

            val jsonStr = response.body?.string() ?: return null
            Log.d(TAG, "Mobile API response length: ${jsonStr.length}")

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(jsonStr) ?: return null

            val awemeList = root["aweme_details"] as? List<*> ?: return null
            val awemeDetail = awemeList.firstOrNull() as? Map<*, *> ?: return null

            return parseAwemeDetail(awemeDetail)
        } catch (e: Exception) {
            Log.w(TAG, "extractFromMobileApi failed", e)
            return null
        }
    }

    private fun parseAwemeDetail(detail: Map<*, *>): TikTokVideoData? {
        val videoId = detail["aweme_id"]?.toString() ?: ""
        val desc = detail["desc"]?.toString() ?: ""

        val authorMap = detail["author"] as? Map<*, *>
        val author = authorMap?.get("nickname")?.toString() ?: ""
        val authorId = authorMap?.get("unique_id")?.toString()
            ?: authorMap?.get("uid")?.toString() ?: ""

        val videoInfo = detail["video"] as? Map<*, *> ?: return null

        // Extract cover/thumbnail
        val thumbnail = extractUrlFromAddr(videoInfo, "cover")
            ?: extractUrlFromAddr(videoInfo, "originCover")
            ?: extractUrlFromAddr(videoInfo, "dynamicCover")
            ?: ""

        val duration = (videoInfo["duration"] as? Number)?.toLong() ?: 0L

        // Extract video URLs — try multiple sources (yt-dlp order)
        // 1. playAddr (most reliable for web extraction)
        val videoUrl = extractUrlFromAddr(videoInfo, "playAddr")
            ?: extractUrlFromAddr(videoInfo, "play_addr")

        // 2. downloadAddr (watermarked)
        val videoUrlNoWatermark = extractUrlFromAddr(videoInfo, "downloadAddr")
            ?: extractUrlFromAddr(videoInfo, "download_addr")

        // 3. bitrateInfo fallback
        val bitrateUrl = extractFromBitrateInfo(videoInfo)

        // 4. playAddrH264 fallback
        val h264Url = extractUrlFromAddr(videoInfo, "play_addr_h264")

        // Use first available
        val finalUrl = videoUrl ?: bitrateUrl ?: h264Url

        // Audio from music
        val musicInfo = detail["music"] as? Map<*, *>
        val audioUrl = extractUrlFromAddr(musicInfo ?: emptyMap<String, Any?>(), "playUrl")
            ?: extractUrlFromAddr(musicInfo ?: emptyMap<String, Any?>(), "play_url")

        Log.d(TAG, "parseAwemeDetail: videoUrl=${finalUrl?.take(80)}, watermarkUrl=${videoUrlNoWatermark?.take(80)}, audioUrl=${audioUrl?.take(80)}")

        if (finalUrl == null && videoUrlNoWatermark == null) return null

        return TikTokVideoData(
            id = videoId, title = desc, author = author, authorId = authorId,
            thumbnail = thumbnail, duration = duration,
            videoUrl = finalUrl, videoUrlNoWatermark = videoUrlNoWatermark, audioUrl = audioUrl
        )
    }

    private fun extractUrlFromAddr(map: Map<*, *>, key: String): String? {
        val value = map[key] ?: return null
        return when (value) {
            is String -> value.ifBlank { null }
            is Map<*, *> -> {
                // Try url_list (old format) and use LAST URL (yt-dlp convention)
                val urlList = value["url_list"] as? List<*>
                    ?: value["UrlList"] as? List<*>
                val urlFromList = urlList
                    ?.filterIsInstance<String>()
                    ?.lastOrNull { it.isNotBlank() }
                if (urlFromList != null) return urlFromList

                // Try src key
                val src = value["src"] as? String
                if (!src.isNullOrBlank()) return src

                // Try url key
                val url = value["url"] as? String
                if (!url.isNullOrBlank()) return url

                null
            }
            else -> null
        }
    }

    private fun extractFromBitrateInfo(videoInfo: Map<*, *>): String? {
        try {
            val bitrateInfoList = videoInfo["bitrateInfo"] as? List<*>
                ?: videoInfo["bit_rate"] as? List<*>
                ?: return null

            for (bitrate in bitrateInfoList) {
                if (bitrate !is Map<*, *>) continue
                val playAddr = bitrate["PlayAddr"] as? Map<*, *> ?: continue
                val urlList = playAddr["UrlList"] as? List<*> ?: continue
                // Use LAST URL (yt-dlp convention — highest quality)
                val url = urlList.filterIsInstance<String>().lastOrNull { it.isNotBlank() }
                if (url != null) return url
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    // ── Web page fetching ──────────────────────────────────────────

    private fun fetchPageHtml(url: String, httpClient: OkHttpClient): String? {
        return try {
            val requestBuilder = Request.Builder().url(url)
            getDefaultHeaders(false).forEach { (k, v) -> requestBuilder.header(k, v) }
            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} fetching page")
                return null
            }
            val html = response.body?.string()
            if (html.isNullOrBlank()) {
                Log.w(TAG, "Empty response body")
                return null
            }
            html
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch page HTML", e)
            null
        }
    }

    private fun extractItemId(url: String): String? {
        val videoPattern = Pattern.compile("""(/video/|/item/)(\d+)""", Pattern.CASE_INSENSITIVE)
        val matcher = videoPattern.matcher(url)
        if (matcher.find()) return matcher.group(2)
        return null
    }

    private fun extractItemIdFromHtml(url: String): String? {
        try {
            val html = fetchPageHtml(url, client) ?: return null
            val idPattern = Pattern.compile(
                """"id"\s*:\s*"(\d+)"|"video_id"\s*:\s*"(\d+)"|itemId=(\d+)|/video/(\d+)|/item/(\d+)""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = idPattern.matcher(html)
            if (matcher.find()) {
                for (i in 1..matcher.groupCount()) {
                    val g = matcher.group(i)
                    if (!g.isNullOrBlank()) return g
                }
            }
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractItemIdFromHtml failed", e)
            return null
        }
    }

    // ── HTML scraping strategies ───────────────────────────────────

    private fun extractFromUniversalData(html: String): TikTokVideoData? {
        try {
            val pattern = Pattern.compile(
                """<script\s+id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(html)
            if (!matcher.find()) return null
            val jsonStr = matcher.group(1) ?: return null

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val rootAdapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            val root = rootAdapter.fromJson(jsonStr) ?: return null

            val defaultScope = root["__DEFAULT_SCOPE__"] as? Map<*, *> ?: return null
            val videoDetail = defaultScope["webapp.video-detail"] as? Map<*, *> ?: return null
            val statusCode = (videoDetail["statusCode"] as? Number)?.toInt() ?: 0
            if (statusCode != 0) {
                Log.w(TAG, "universalData status: $statusCode")
            }
            val itemInfo = videoDetail["itemInfo"] as? Map<*, *> ?: return null
            val itemStruct = itemInfo["itemStruct"] as? Map<*, *> ?: return null

            return parseAwemeDetail(itemStruct)
        } catch (e: Exception) {
            Log.w(TAG, "extractFromUniversalData failed", e)
            return null
        }
    }

    private fun extractFromInitProps(html: String): TikTokVideoData? {
        try {
            val pattern = Pattern.compile(
                """<script\s+id="__INIT_PROPS__"[^>]*>(.*?)</script>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(html)
            if (!matcher.find()) return null
            val jsonStr = matcher.group(1) ?: return null

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val rootAdapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            val root = rootAdapter.fromJson(jsonStr) ?: return null

            val page = root["page"] as? Map<*, *> ?: return null
            val videoData = page["videoData"] as? Map<*, *>
                ?: root["videoData"] as? Map<*, *>
                ?: return null

            val videoId = (videoData["id"] ?: videoData["video_id"])?.toString() ?: ""
            val title = (videoData["title"] ?: videoData["desc"])?.toString() ?: ""
            val authorMap = videoData["author"] as? Map<*, *>
            val author = authorMap?.get("nickname")?.toString() ?: videoData["author_name"]?.toString() ?: ""
            val authorId = authorMap?.get("unique_id")?.toString() ?: videoData["author_id"]?.toString() ?: ""
            val thumbnail = extractUrlFromAddr(videoData, "cover") ?: videoData["cover"]?.toString() ?: ""
            val duration = (videoData["duration"] as? Number)?.toLong() ?: 0L

            val videoUrl = extractUrlFromAddr(videoData, "play_addr")
            val videoUrlNoWatermark = extractUrlFromAddr(videoData, "download_addr")
            val audioUrl = extractUrlFromAddr(videoData, "music")

            if (videoUrl == null && videoUrlNoWatermark == null) return null

            return TikTokVideoData(
                id = videoId, title = title, author = author, authorId = authorId,
                thumbnail = thumbnail, duration = duration,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrlNoWatermark, audioUrl = audioUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "extractFromInitProps failed", e)
            return null
        }
    }

    private fun extractFromSigiData(html: String): TikTokVideoData? {
        try {
            val sigiPattern = Pattern.compile(
                """<script[^>]*>\s*window\.(?:__SIGI_INIT__|__SIGI_STATE__)\s*=\s*""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val sigiMatcher = sigiPattern.matcher(html)
            if (!sigiMatcher.find()) return null
            val start = sigiMatcher.end()
            val jsonStr = extractBalancedJson(html, start, '{', '}') ?: return null

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(jsonStr) ?: return null

            val videoData = root["VideoModule"] as? Map<*, *>
                ?: root["video"] as? Map<*, *>
                ?: root["itemInfo"] as? Map<*, *>
                ?: root["shareMeta"] as? Map<*, *>
                ?: return null

            val itemStruct = findNestedMap(videoData, "itemStruct")
            if (itemStruct != null) return parseAwemeDetail(itemStruct)

            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractFromSigiData failed", e)
            return null
        }
    }

    private fun extractBalancedJson(text: String, startIndex: Int, open: Char, close: Char): String? {
        val sb = StringBuilder()
        var depth = 0
        var inString = false
        var escape = false
        for (i in startIndex until text.length) {
            val c = text[i]
            sb.append(c)
            if (escape) { escape = false; continue }
            when (c) {
                '\\' -> escape = true
                '"' -> if (!escape) inString = !inString
                open -> if (!inString) depth++
                close -> { if (!inString) { depth--; if (depth == 0) return sb.toString() } }
            }
        }
        return null
    }

    private fun findNestedMap(map: Map<*, *>, key: String): Map<*, *>? {
        val direct = map[key]
        if (direct is Map<*, *>) return direct
        for ((_, value) in map) {
            if (value is Map<*, *>) {
                val found = findNestedMap(value, key)
                if (found != null) return found
            }
        }
        return null
    }

    private fun extractFromCdnUrlPattern(html: String): TikTokVideoData? {
        try {
            val pattern = Pattern.compile(
                """https?://[a-z0-9.-]+(?:tiktokcdn|tikcdn|bytecdn|bytedance)[^"'\s<>]*""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(html)
            val urls = mutableSetOf<String>()
            while (matcher.find()) {
                val url = matcher.group().replace("\\u002F", "/")
                urls.add(url)
            }
            if (urls.isEmpty()) return null

            val videoUrl = urls.firstOrNull()
            val titlePattern = Pattern.compile("""<title[^>]*>(.*?)</title>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val titleMatcher = titlePattern.matcher(html)
            val title = if (titleMatcher.find()) {
                titleMatcher.group(1)?.trim()?.replace(" | TikTok", "")?.replace(" - TikTok", "")?.trim() ?: ""
            } else ""

            val authorPattern = Pattern.compile("""@(\w+)""", Pattern.CASE_INSENSITIVE)
            val authorMatcher = authorPattern.matcher(html)
            val author = if (authorMatcher.find()) "@${authorMatcher.group(1)}" else ""

            if (videoUrl == null) return null

            return TikTokVideoData(
                id = "", title = title, author = author, authorId = "",
                thumbnail = "", duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "extractFromCdnUrlPattern failed", e)
            return null
        }
    }

    private fun extractFromMetaTags(html: String): TikTokVideoData? {
        try {
            val videoPattern = Pattern.compile(
                """<meta\s+[^>]*property=["']og:video["'][^>]*content=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val videoUrl = if (videoPattern.matcher(html).find()) {
                videoPattern.matcher(html).group(1)
            } else null

            val thumbnailPattern = Pattern.compile(
                """<meta\s+[^>]*property=["']og:image["'][^>]*content=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val thumbnail = if (thumbnailPattern.matcher(html).find()) {
                thumbnailPattern.matcher(html).group(1) ?: ""
            } else ""

            val titlePattern = Pattern.compile(
                """<meta\s+[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val title = if (titlePattern.matcher(html).find()) {
                titlePattern.matcher(html).group(1) ?: ""
            } else ""

            val authorPattern = Pattern.compile("""@(\w+)""", Pattern.CASE_INSENSITIVE)
            val authorMatcher = authorPattern.matcher(html)
            val author = if (authorMatcher.find()) "@${authorMatcher.group(1)}" else ""

            if (videoUrl == null) return null

            return TikTokVideoData(
                id = "", title = title, author = author, authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "extractFromMetaTags failed", e)
            return null
        }
    }

    // ── JSON-LD VideoObject (NexLoad-inspired) ─────────────────────

    private fun extractFromJsonLd(html: String): TikTokVideoData? {
        try {
            val pattern = Pattern.compile(
                """<script[^>]+type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(html)
            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)

            while (matcher.find()) {
                val jsonStr = matcher.group(1) ?: continue
                try {
                    val data = adapter.fromJson(jsonStr) ?: continue
                    val items = if (data["@type"] == "VideoObject") listOf(data)
                    else (data["@graph"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                        ?.filter { it["@type"] == "VideoObject" }
                        ?: emptyList()

                    for (item in items) {
                        val videoUrl = item["contentUrl"]?.toString()?.ifBlank { null }
                            ?: (item["embedUrl"] as? Map<*, *>)?.get("url")?.toString()?.ifBlank { null }
                        if (videoUrl != null) {
                            val title = item["name"]?.toString() ?: ""
                            val thumbnail = item["thumbnailUrl"]?.toString() ?: ""
                            val durationStr = item["duration"]?.toString() ?: ""
                            val duration = parseIsoDuration(durationStr)

                            Log.d(TAG, "extractFromJsonLd: found VideoObject contentUrl")
                            return TikTokVideoData(
                                id = "", title = title, author = "", authorId = "",
                                thumbnail = thumbnail, duration = duration,
                                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                            )
                        }
                    }
                } catch (_: Exception) { }
            }
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractFromJsonLd failed", e)
            return null
        }
    }

    private fun parseIsoDuration(iso: String): Long {
        // Parse ISO 8601 duration like PT1M30S → 90 seconds
        val m = Pattern.compile("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matcher(iso)
        if (!m.find()) return 0L
        val hours = m.group(1)?.toLongOrNull() ?: 0L
        val minutes = m.group(2)?.toLongOrNull() ?: 0L
        val seconds = m.group(3)?.toLongOrNull() ?: 0L
        return hours * 3600 + minutes * 60 + seconds
    }

    // ── HTML5 <video> tag extraction (NexLoad-inspired) ─────────────

    private fun extractFromVideoTag(html: String): TikTokVideoData? {
        try {
            val pattern = Pattern.compile(
                """<video[^>]+src=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(html)
            if (!matcher.find()) return null
            val videoUrl = matcher.group(1) ?: return null
            if (videoUrl.isBlank()) return null

            // Try to get title from og:title
            val titlePattern = Pattern.compile(
                """<meta\s+[^>]*property=["']og:title["'][^>]*content=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val title = if (titlePattern.matcher(html).find()) {
                titlePattern.matcher(html).group(1) ?: ""
            } else ""

            Log.d(TAG, "extractFromVideoTag: found <video src>")
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = "", duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "extractFromVideoTag failed", e)
            return null
        }
    }

    // ── oEmbed API ─────────────────────────────────────────────────

    private fun extractFromOembed(itemId: String): TikTokVideoData? {
        try {
            val oembedUrl = "https://www.tiktok.com/oembed?url=https://www.tiktok.com/@x/video/$itemId"
            val request = Request.Builder().url(oembedUrl)
                .header("Accept", "application/json")
                .build()

            val apiClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(TikTokCookieStore.cookieJar)
                .build()

            val response = apiClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val jsonStr = response.body?.string() ?: return null

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(jsonStr) ?: return null

            val title = root["title"]?.toString() ?: ""
            val author = root["author_name"]?.toString() ?: ""
            val authorId = root["author_unique_id"]?.toString()
                ?: root["author_url"]?.toString()?.substringAfter("@")?.substringBefore("/") ?: ""
            val thumbnail = root["thumbnail_url"]?.toString() ?: ""
            val embedHtml = root["html"]?.toString() ?: ""

            // Try to find video URL from embed HTML
            val videoUrl = extractVideoUrlFromEmbed(embedHtml)
            if (videoUrl == null) {
                Log.w(TAG, "oEmbed succeeded but no video URL found in HTML")
                return null
            }

            return TikTokVideoData(
                id = itemId, title = title, author = author, authorId = authorId,
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "extractFromOembed failed", e)
            return null
        }
    }

    private fun extractVideoUrlFromEmbed(html: String): String? {
        val cdnPattern = Pattern.compile(
            """https?://[a-z0-9.-]+(?:tiktokcdn|tikcdn|bytecdn)[^"'\s<>]+""", Pattern.CASE_INSENSITIVE
        )
        val cdnMatcher = cdnPattern.matcher(html)
        if (cdnMatcher.find()) return cdnMatcher.group()
        return null
    }

    // ── Redirect resolution ────────────────────────────────────────

    private fun resolveRedirect(url: String): String? {
        if (!url.contains("vt.tiktok.com") && !url.contains("vm.tiktok.com")) return url
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.request.url.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Redirect resolution failed", e)
            url
        }
    }
}
