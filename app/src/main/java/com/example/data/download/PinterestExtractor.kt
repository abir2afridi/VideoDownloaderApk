package com.example.data.download

import android.util.Log
import java.net.URLDecoder

// =========================================================================
// PINTEREST VIDEO EXTRACTION — DO NOT DELETE OR MODIFY WITHOUT READING
// =========================================================================
// PROBLEM: Pinterest video download fails — extraction returns null for all strategies.
//
// ROOT CAUSES IDENTIFIED AND FIXED:
//
// 1. RELAY JSON REGEX BROKEN (MOST CRITICAL):
//    Pinterest embeds video data in __PWS_RELAY_REGISTER_COMPLETED_REQUEST__ scripts.
//    The second argument is raw JSON with deeply nested objects (storyPinData.pages[].blocks[]).
//    Old regex: \{[\s\S]*?\} — non-greedy, stops at FIRST } → gets partial JSON → parse fails
//    FIX: extractBalancedJson() — brace-counting algorithm that tracks { depth and string
//    escaping to find the matching } → gets full JSON object → parse succeeds
//
// 2. PIN.IT SHORT URL NOT RESOLVED:
//    Old resolveRedirect() only handled TikTok short URLs (vt.tiktok.com, vm.tiktok.com).
//    pin.it/2ima6B8Wm was never resolved to full pinterest.com/pin/{id}/ URL.
//    FIX: Added isShortPinterest check to resolveRedirect() in BaseExtractor.kt
//
// 3. PINTEREST SERVER RETURNS JS SHELL WITHOUT BROWSER HEADERS:
//    Pinterest detects bare OkHttp requests and returns a JavaScript-rendered shell
//    without the relay data or JSON-LD. Needs Sec-Fetch-* and Sec-Ch-Ua headers.
//    FIX: Added Pinterest-specific browser headers in fetchPageHtml() in BaseExtractor.kt
//
// 4. CONTENTURL REGEX FALLBACK MISSING:
//    JSON-LD VideoObject has "contentUrl" but JSON parser can fail on duplicate keys.
//    FIX: Added direct regex extraction for "contentUrl":"https://v1.pinimg.com/...mp4"
//
// HOW PINTEREST VIDEO PAGES WORK:
// 1. pin.it short URLs redirect to full pinterest.com/pin/{id}/ URLs
// 2. Page HTML contains video data in 3 places (tried in order):
//    a. og:video meta tag (rare but simple)
//    b. JSON-LD VideoObject (reliable — has contentUrl)
//    c. Relay script data (__PWS_RELAY_REGISTER_COMPLETED_REQUEST__)
//       → URL-encoded JSON with videoList720P / videoListMobile
//    d. contentUrl regex fallback (bypasses JSON parser)
//    e. Any v1.pinimg.com .mp4 URL regex fallback
// 3. Video CDN is v1.pinimg.com — no auth needed, no 403 issues
//
// MODERN PINTEREST HTML STRUCTURE (2025+):
// - No more og:video meta tags for videos
// - No more data-pin-data or __PWS_INITIAL_DATA__ scripts
// - Uses __PWS_RELAY_REGISTER_COMPLETED_REQUEST__ with URL-encoded JSON
// - Video URLs in: storyPinData.pages[].blocks[].videoDataV2
//   → videoList720P.v720P.url (MP4, preferred)
//   → videoListMobile.vHLSV3MOBILE.url (m3u8, fallback)
//
// RULES:
// - NEVER remove the extractBalancedJson() function — it's required for nested JSON
// - NEVER change relay regex back to [\s\S]*?\} — it will break on nested JSON
// - Always try JSON-LD first (simplest, most reliable)
// - Then try relay script data (most complete)
// - Then fall back to contentUrl regex, then pinimg .mp4 URL regex
// - Pinterest CDN (v1.pinimg.com) has no auth issues — always works
// - Always resolve pin.it short URLs before extraction
// =========================================================================

internal fun extractPinterest(url: String): TikTokVideoData? {
    val resolved = resolveRedirect(url) ?: url
    val pinId = extractPinId(resolved)
    Log.d(EXTRACTOR_TAG, "Extracting Pinterest pin: $pinId (resolved: $resolved)")

    val html = fetchPageHtml(resolved)
    if (html.isNullOrBlank()) {
        Log.w(EXTRACTOR_TAG, "Pinterest: fetchPageHtml returned null/empty for $resolved")
        return null
    }
    Log.d(EXTRACTOR_TAG, "Pinterest: got ${html.length} chars of HTML")

    // Strategy 1: og:video meta tag (simplest)
    val metaResult = extractPinterestFromMeta(html)
    if (metaResult != null) {
        Log.d(EXTRACTOR_TAG, "Pinterest: success via og:video meta")
        return metaResult
    }

    // Strategy 2: JSON-LD VideoObject (reliable)
    val jsonLdResult = extractFromPinterestJsonLd(html)
    if (jsonLdResult != null) {
        Log.d(EXTRACTOR_TAG, "Pinterest: success via JSON-LD VideoObject")
        return jsonLdResult
    }

    // Strategy 3: Relay script data (most complete for story pins)
    val relayResult = extractFromPinterestRelayData(html)
    if (relayResult != null) {
        Log.d(EXTRACTOR_TAG, "Pinterest: success via relay script data")
        return relayResult
    }

    // Strategy 4: Legacy __PWS_INITIAL_DATA__ script
    val legacyResult = extractFromPinterestLegacyData(html)
    if (legacyResult != null) {
        Log.d(EXTRACTOR_TAG, "Pinterest: success via legacy __PWS_INITIAL_DATA__")
        return legacyResult
    }

    // Strategy 5: Regex fallback for any pinimg .mp4 URL
    val regexResult = extractFromPinterestVideoUrlRegex(html)
    if (regexResult != null) {
        Log.d(EXTRACTOR_TAG, "Pinterest: success via .mp4 URL regex")
        return regexResult
    }

    Log.w(EXTRACTOR_TAG, "Pinterest: all extraction strategies failed for $resolved")
    return null
}

internal fun extractPinId(url: String): String? {
    var u = url.removeSuffix("/")
    if (u.contains("?")) u = u.substringBefore("?")
    val patterns = listOf(
        Regex("""pinterest\.com/pin/(\d+)"""),
        Regex("""pin\.it/(\w+)"""),
        Regex("""pinterest\.com/pin/[^/]+-(\d+)"""),
        Regex("""pinterest\.com/pin/[A-Za-z0-9_-]+/(\d+)""")
    )
    for (pattern in patterns) {
        val match = pattern.find(u)
        if (match != null) return match.groupValues[1]
    }
    return null
}

private fun extractPinterestFromMeta(html: String): TikTokVideoData? {
    try {
        val ogVideo = extractMetaContent(html, "og:video")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video:secure_url")
        if (!ogVideo.isNullOrBlank() && ogVideo.contains("pinimg.com")) {
            val title = extractMetaContent(html, "og:title")
                ?: extractMetaContent(html, "pinterest:title") ?: ""
            val description = extractMetaContent(html, "description")
                ?: extractMetaContent(html, "og:description") ?: ""
            val thumbnail = extractMetaContent(html, "og:image") ?: ""

            return TikTokVideoData(
                id = "", title = title.ifBlank { description }, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = ogVideo, videoUrlNoWatermark = ogVideo, audioUrl = null
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractPinterestFromMeta failed", e)
    }
    return null
}

private fun extractFromPinterestJsonLd(html: String): TikTokVideoData? {
    try {
        val jsonLdPattern = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE
        )
        jsonLdPattern.findAll(html).forEach { match ->
            val jsonStr = match.groupValues[1]
            try {
                val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
                val data = adapter.fromJson(jsonStr) ?: return@forEach
                val type = (data["@type"] as? String) ?: ""
                if (type != "VideoObject") return@forEach

                val contentUrl = data["contentUrl"]?.toString()
                if (contentUrl.isNullOrBlank() || !contentUrl.contains("pinimg.com")) return@forEach

                val title = data["name"]?.toString() ?: data["description"]?.toString() ?: ""
                val thumbnail = (data["thumbnailUrl"] as? List<*>)?.firstOrNull()?.toString()
                    ?: data["thumbnail"]?.toString() ?: ""
                val author = (data["author"] as? Map<*, *>)?.get("name")?.toString() ?: ""
                val durationStr = data["duration"]?.toString() ?: ""
                val durationMs = parseDurationMs(durationStr)

                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = author,
                    thumbnail = thumbnail, duration = durationMs,
                    videoUrl = contentUrl, videoUrlNoWatermark = contentUrl, audioUrl = null
                )
            } catch (_: Exception) { }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromPinterestJsonLd failed", e)
    }
    return null
}

private fun extractFromPinterestRelayData(html: String): TikTokVideoData? {
    // =========================================================================
    // RELAY DATA EXTRACTION — DO NOT DELETE
    // =========================================================================
    // PROBLEM: Old regex \{[\s\S]*?\} is non-greedy and stops at the first },
    // but the relay JSON is deeply nested (storyPinData → pages → blocks → videoDataV2).
    // This returned partial JSON → Moshi parse returned null → extraction failed.
    //
    // SOLUTION: Use extractBalancedJson() which counts { depth and handles string
    // escaping to find the MATCHING closing } — returns the full JSON object.
    //
    // The relay function call format is:
    //   __PWS_RELAY_REGISTER_COMPLETED_REQUEST__("URL_ENCODED_QUERY", {FULL_JSON});
    // We find the function call, then extract the JSON starting from the first {.
    // =========================================================================
    try {
        val relayFnPattern = Regex(
            """__PWS_RELAY_REGISTER_COMPLETED_REQUEST__\("([^"]+)",\s*""",
            RegexOption.IGNORE_CASE
        )
        relayFnPattern.findAll(html).forEach { match ->
            val queryStr = match.groupValues[1]
            val startIdx = match.range.last + 1

            // Try to extract the raw JSON by counting braces from startIdx
            val jsonStr = extractBalancedJson(html, startIdx) ?: return@forEach

            // The first arg is URL-encoded query info, the second arg is the actual data
            // The raw JSON is the second argument — parse it directly
            val videoData = extractVideoFromRelayJson(jsonStr)
            if (videoData != null) return videoData
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromPinterestRelayData failed", e)
    }
    return null
}

private fun extractBalancedJson(text: String, startIndex: Int): String? {
    // =========================================================================
    // BALANCED JSON EXTRACTION — DO NOT DELETE OR MODIFY
    // =========================================================================
    // WHY THIS EXISTS: Pinterest relay JSON is deeply nested:
    //   {"data":{"v3GetPinQueryv2":{"data":{"storyPinData":{"pages":[{"blocks":[...
    // A regex like \{[\s\S]*?\} stops at the first } (non-greedy) — gets partial JSON.
    // This function counts { depth to find the MATCHING closing }.
    //
    // HOW IT WORKS:
    // 1. Find the first { after startIndex
    // 2. Iterate character by character, tracking:
    //    - { increases depth (opening a nested object)
    //    - } decreases depth (closing a nested object)
    //    - " toggles inString flag (don't count { } inside strings)
    //    - \ escapes next char (handle \" inside strings)
    // 3. When depth returns to 0, we found the matching }
    //
    // EXAMPLE: {"a":{"b":1}} → depth: 0→1→2→1→0 at position 15 → returns full JSON
    // =========================================================================
    // Find the opening { and count braces to find the matching closing }
    var depth = 0
    var inString = false
    var escape = false
    val start = text.indexOf('{', startIndex)
    if (start == -1 || start - startIndex > 5) return null

    for (i in start until text.length) {
        val c = text[i]
        if (escape) {
            escape = false
            continue
        }
        if (c == '\\' && inString) {
            escape = true
            continue
        }
        if (c == '"') {
            inString = !inString
            continue
        }
        if (inString) continue
        if (c == '{') depth++
        if (c == '}') {
            depth--
            if (depth == 0) {
                return text.substring(start, i + 1)
            }
        }
    }
    return null
}

private fun extractVideoFromRelayJson(jsonStr: String): TikTokVideoData? {
    try {
        val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
        val root = adapter.fromJson(jsonStr) ?: return null

        // Navigate: data.v3GetPinQueryv2.data.storyPinData.pages[].blocks[]
        val pinData = root["data"]?.let { it as? Map<*, *> }
            ?.get("v3GetPinQueryv2")?.let { it as? Map<*, *> }
            ?.get("data")?.let { it as? Map<*, *> }

        val title = pinData?.get("title")?.toString()
            ?: pinData?.get("description")?.toString() ?: ""
        val author = (pinData?.get("pinner") as? Map<*, *>)?.get("fullName")?.toString() ?: ""
        val thumbnail = pinData?.get("images_orig")?.let { it as? Map<*, *> }?.get("url")?.toString()
            ?: pinData?.get("imageLargeUrl")?.toString() ?: ""

        // Extract video from storyPinData
        val storyPinData = pinData?.get("storyPinData") as? Map<*, *>
        val pages = storyPinData?.get("pages") as? List<*>
        if (pages != null) {
            for (page in pages) {
                val pageMap = page as? Map<*, *> ?: continue
                val blocks = pageMap["blocks"] as? List<*> ?: continue
                for (block in blocks) {
                    val blockMap = block as? Map<*, *> ?: continue
                    val videoDataV2 = blockMap["videoDataV2"] as? Map<*, *> ?: continue

                    // Try videoList720P first (MP4, preferred)
                    val video720p = (videoDataV2["videoList720P"] as? Map<*, *>)
                        ?.get("v720P") as? Map<*, *>
                    val mp4Url = video720p?.get("url")?.toString()
                    if (!mp4Url.isNullOrBlank() && mp4Url.contains(".mp4")) {
                        val durationMs = (video720p?.get("duration") as? Number)?.toLong() ?: 0L
                        return TikTokVideoData(
                            id = "", title = title, author = author, authorId = author,
                            thumbnail = thumbnail, duration = durationMs,
                            videoUrl = mp4Url, videoUrlNoWatermark = mp4Url, audioUrl = null
                        )
                    }

                    // Fallback: videoListMobile (m3u8)
                    val mobileVideo = (videoDataV2["videoListMobile"] as? Map<*, *>)
                        ?.get("vHLSV3MOBILE") as? Map<*, *>
                    val m3u8Url = mobileVideo?.get("url")?.toString()
                    if (!m3u8Url.isNullOrBlank() && m3u8Url.contains(".m3u8")) {
                        val durationMs = (mobileVideo?.get("duration") as? Number)?.toLong() ?: 0L
                        return TikTokVideoData(
                            id = "", title = title, author = author, authorId = author,
                            thumbnail = thumbnail, duration = durationMs,
                            videoUrl = m3u8Url, videoUrlNoWatermark = m3u8Url, audioUrl = null
                        )
                    }

                    // Fallback: videoList (any format)
                    val videoList = videoDataV2["videoList"] as? Map<*, *>
                    videoList?.values?.firstOrNull()?.let { first ->
                        val entry = first as? Map<*, *>
                        val url = entry?.get("url")?.toString()
                        if (!url.isNullOrBlank()) {
                            return TikTokVideoData(
                                id = "", title = title, author = author, authorId = author,
                                thumbnail = thumbnail, duration = 0L,
                                videoUrl = url, videoUrlNoWatermark = url, audioUrl = null
                            )
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractVideoFromRelayJson failed", e)
    }
    return null
}

private fun extractFromPinterestLegacyData(html: String): TikTokVideoData? {
    // Legacy Pinterest pages with __PWS_INITIAL_DATA__ or data-pin-data scripts
    try {
        val pinDataMatch = Regex("""<script[^>]*data-pin-data[^>]*>([\s\S]*?)</script>""").find(html)
            ?: Regex("""<script[^>]*id=["']__PWS_INITIAL_DATA__["'][^>]*>([\s\S]*?)</script>""").find(html)

        if (pinDataMatch != null) {
            val jsonStr = pinDataMatch.groupValues[1]
            val adapter = extractorMoshi.adapter<Map<String, Any?>>(rootMapType)
            val data = adapter.fromJson(jsonStr) ?: return null

            val pinJson = data["pins"]?.let {
                val pinList = it as? List<*>
                pinList?.firstOrNull() as? Map<*, *>
            } ?: data["pin"] as? Map<*, *>

            if (pinJson != null) {
                val title = pinJson["title"]?.toString()
                    ?: pinJson["description"]?.toString() ?: pinJson["grid_description"]?.toString() ?: ""
                val author = (pinJson["pinner"] as? Map<*, *>)?.get("full_name")?.toString()
                    ?: (pinJson["user"] as? Map<*, *>)?.get("username")?.toString() ?: ""
                val thumbnail = pinJson["image_cover_url"]?.toString()
                    ?: (pinJson["images"] as? Map<*, *>)?.let { images ->
                        val orig = images["orig"] as? Map<*, *>
                        orig?.get("url")?.toString()
                    } ?: ""

                val videoUrl = pinJson["video_url"]?.toString()
                    ?: (pinJson["videos"] as? Map<*, *>)?.let { videos ->
                        val videoList = videos["video_list"] as? Map<*, *>
                        videoList?.values?.firstOrNull()?.let {
                            val entry = it as? Map<*, *>
                            entry?.get("url")?.toString()
                        }
                    }

                if (!videoUrl.isNullOrBlank()) {
                    return TikTokVideoData(
                        id = pinJson["id"]?.toString() ?: "", title = title,
                        author = author, authorId = author,
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromPinterestLegacyData failed", e)
    }
    return null
}

private fun extractFromPinterestVideoUrlRegex(html: String): TikTokVideoData? {
    // Last resort: find any v1.pinimg.com .mp4 URL in the HTML
    // Also try to extract contentUrl from JSON-LD directly via regex (bypasses JSON parser)
    try {
        // Try contentUrl regex first (from JSON-LD VideoObject)
        val contentUrlPattern = Regex(""""contentUrl"\s*:\s*"(https?://v\d*\.pinimg\.com/videos/[^"]+\.mp4)"""")
        val contentUrlMatch = contentUrlPattern.find(html)
        if (contentUrlMatch != null) {
            val videoUrl = contentUrlMatch.groupValues[1]
            val title = extractMetaContent(html, "og:title")
                ?: extractMetaContent(html, "pinterest:title") ?: ""
            val thumbnail = extractMetaContent(html, "og:image") ?: ""
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
            )
        }

        // Fallback: any pinimg .mp4 URL
        val mp4Pattern = Regex("""https?://v\d*\.pinimg\.com/videos/[^"'\s]+\.mp4""")
        val match = mp4Pattern.find(html)
        if (match != null) {
            val videoUrl = match.value
            val title = extractMetaContent(html, "og:title")
                ?: extractMetaContent(html, "pinterest:title") ?: ""
            val thumbnail = extractMetaContent(html, "og:image") ?: ""

            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
            )
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractFromPinterestVideoUrlRegex failed", e)
    }
    return null
}

private fun parseDurationMs(durationStr: String): Long {
    // Parse ISO 8601 duration: PT20S → 20000ms, PT1M30S → 90000ms
    try {
        val match = Regex("""PT(?:(\d+)M)?(?:(\d+)S)?""").find(durationStr) ?: return 0L
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        return (minutes * 60 + seconds) * 1000
    } catch (_: Exception) { return 0L }
}
