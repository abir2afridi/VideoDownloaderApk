package com.example.data.download

// =========================================================================
// FACEBOOK VIDEO EXTRACTION — DO NOT DELETE OR MODIFY WITHOUT READING
// =========================================================================
// PROBLEM: Facebook video download fails with HTTP 403 Forbidden.
//
// WHY IT HAPPENS:
// 1. Facebook CDN (fbcdn.net) requires specific request headers — a browser
//    User-Agent triggers rate-limiting → 403 error
// 2. Facebook share URLs (e.g. /share/r/xxx) need to be resolved to actual
//    video page URLs before extraction
// 3. Modern Facebook uses DASH (separate audio+video) — the CDN URL in the
//    page HTML may be a manifest, not a direct MP4
// 4. CDN tokens (oh=, oe=, _nc_sid=) expire in ~30-60 minutes — must
//    download immediately after extraction
//
// HOW OTHER APPS SOLVE THIS (researched from SnapSave, FDown, yt-dlp):
// - SnapSave: POST to server-side API, decode obfuscated JS, get CDN links
// - FDown: Puppeteer-based headless browser scraping
// - yt-dlp: Uses facebookexternalhit/1.1 UA + 250MB chunked downloads
// - mbasic/m.facebook.com: Simpler HTML with direct <video> tags
//
// OUR SOLUTION (3-strategy extraction):
// 1. m.facebook.com — mobile page has simplest HTML, direct <video> tags,
//    and hd_src/sd_src in script data (MOST RELIABLE)
// 2. www.facebook.com — desktop page with JSON-LD, script data patterns
// 3. mbasic.facebook.com — oldest/simplest HTML format
//
// Each strategy tries 6 regex patterns to find the direct CDN URL:
// - hd_src/sd_src (classic Facebook embed)
// - playable_url (script data)
// - DASH BaseURL (modern Facebook)
// - <video> tag src (mbasic pages)
// - Any fbcdn mp4 URL (catch-all)
// - og:video meta tag (embed URL fallback)
//
// RULES:
// - NEVER remove the m.facebook.com strategy — it's the most reliable
// - NEVER use yt-dlp for Facebook extraction — it's slow, hangs, and gives 403
// - NEVER change the User-Agent in fetchPageHtml for Facebook pages
// - Always extract from CDN URL (contains "fbcdn") not embed URL
// - If extraction fails, check if the video is private/region-locked
// =========================================================================

import android.util.Log
import okhttp3.Request
import java.util.regex.Pattern

internal fun extractFacebook(url: String): TikTokVideoData? {
    val resolved = resolveFbShareUrl(url) ?: url
    Log.d(EXTRACTOR_TAG, "Extracting Facebook video from: $resolved")

    // Strategy 1: Try m.facebook.com (simplest HTML, direct video tags)
    val mobileUrl = resolved.replace("www.facebook.com", "m.facebook.com")
        .replace("web.facebook.com", "m.facebook.com")
    val mobileResult = extractFromFbPage(mobileUrl)
    if (mobileResult?.hasDirectVideoUrl() == true) {
        Log.d(EXTRACTOR_TAG, "Facebook: got direct URL from mobile page")
        return mobileResult
    }

    // Strategy 2: Try original URL
    val desktopResult = extractFromFbPage(resolved)
    if (desktopResult?.hasDirectVideoUrl() == true) {
        Log.d(EXTRACTOR_TAG, "Facebook: got direct URL from desktop page")
        return desktopResult
    }

    // Strategy 3: Try mbasic.facebook.com (older but simpler HTML)
    val mbasicUrl = resolved.replace("www.facebook.com", "mbasic.facebook.com")
        .replace("web.facebook.com", "mbasic.facebook.com")
    val mbasicResult = extractFromFbPage(mbasicUrl)
    if (mbasicResult?.hasDirectVideoUrl() == true) {
        Log.d(EXTRACTOR_TAG, "Facebook: got direct URL from mbasic page")
        return mbasicResult
    }

    // Strategy 4: Oembed fallback (returns thumbnail + sometimes video URL)
    val oembedResult = tryFacebookOembed(resolved)
    if (oembedResult?.hasDirectVideoUrl() == true) return oembedResult

    // Return whatever we got (even non-direct URLs as last resort)
    return mobileResult ?: desktopResult ?: mbasicResult ?: oembedResult
}

private fun resolveFbShareUrl(url: String): String? {
    if (!url.contains("/share/")) return url
    return try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,*/*")
            .build()
        extractorClient.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            Log.d(EXTRACTOR_TAG, "Resolved FB share: $url -> $finalUrl")
            finalUrl
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "Failed to resolve FB share URL", e)
        url
    }
}

private fun extractFromFbPage(url: String): TikTokVideoData? {
    val html = fetchPageHtml(url) ?: return null

    // Pattern 1: hd_src / sd_src (classic Facebook embed)
    val hdMatch = Regex("""hd_src\s*[:=]\s*["']([^"']+)["']""").find(html)
    val sdMatch = Regex("""sd_src\s*[:=]\s*["']([^"']+)["']""").find(html)
    val directUrl = hdMatch?.groupValues?.get(1)?.unescapeUrl()
        ?: sdMatch?.groupValues?.get(1)?.unescapeUrl()

    if (!directUrl.isNullOrBlank() && directUrl.contains("fbcdn")) {
        val title = extractFbTitle(html)
        val thumbnail = extractFbThumbnail(html)
        return TikTokVideoData(
            id = "", title = title, author = "", authorId = "",
            thumbnail = thumbnail, duration = 0L,
            videoUrl = directUrl, videoUrlNoWatermark = directUrl, audioUrl = null
        )
    }

    // Pattern 2: playable_url in script data
    val playableMatch = Regex("""playable_url\s*[:=]\s*["']([^"']+)["']""").find(html)
    if (playableMatch != null) {
        val videoUrl = playableMatch.groupValues[1].unescapeUrl()
        if (videoUrl.isNotBlank() && videoUrl.contains("fbcdn")) {
            val title = extractFbTitle(html)
            val thumbnail = extractFbThumbnail(html)
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
            )
        }
    }

    // Pattern 3: BaseURL in DASH manifest (modern Facebook)
    val dashMatch = Regex("""FBQualityLabel.*?<BaseURL>(.*?)</BaseURL>""", RegexOption.DOT_MATCHES_ALL).find(html)
    if (dashMatch != null) {
        val videoUrl = dashMatch.groupValues[1].trim().unescapeUrl()
        if (videoUrl.isNotBlank() && videoUrl.contains("fbcdn")) {
            val title = extractFbTitle(html)
            val thumbnail = extractFbThumbnail(html)
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
            )
        }
    }

    // Pattern 4: <video> tag src (mbasic pages)
    val videoTagMatch = Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
    if (videoTagMatch != null) {
        val videoUrl = videoTagMatch.groupValues[1].unescapeUrl()
        if (videoUrl.isNotBlank()) {
            val title = extractFbTitle(html)
            val thumbnail = extractFbThumbnail(html)
            return TikTokVideoData(
                id = "", title = title, author = "", authorId = "",
                thumbnail = thumbnail, duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
            )
        }
    }

    // Pattern 5: Any fbcdn mp4 URL in the page
    val anyFbcdnMatch = Regex("""https?://[^"'\s]*fbcdn[^"'\s]*\.mp4[^"'\s]*""").find(html)
    if (anyFbcdnMatch != null) {
        val videoUrl = anyFbcdnMatch.value.unescapeUrl()
        val title = extractFbTitle(html)
        val thumbnail = extractFbThumbnail(html)
        return TikTokVideoData(
            id = "", title = title, author = "", authorId = "",
            thumbnail = thumbnail, duration = 0L,
            videoUrl = videoUrl, videoUrlNoWatermark = videoUrl, audioUrl = null
        )
    }

    // Pattern 6: og:video meta tag (embed URL, not ideal but works)
    val ogVideo = extractMetaContent(html, "og:video")
        ?: extractMetaContent(html, "og:video:url")
        ?: extractMetaContent(html, "og:video:secure_url")
    if (!ogVideo.isNullOrBlank()) {
        val title = extractMetaContent(html, "og:title") ?: ""
        val thumbnail = extractMetaContent(html, "og:image") ?: ""
        return TikTokVideoData(
            id = "", title = title.ifBlank { extractFbTitle(html) }, author = "", authorId = "",
            thumbnail = thumbnail, duration = 0L,
            videoUrl = ogVideo, videoUrlNoWatermark = ogVideo, audioUrl = null
        )
    }

    return null
}

private fun extractFbTitle(html: String): String {
    val title = extractMetaContent(html, "og:title")
    if (!title.isNullOrBlank()) return title
    val desc = extractMetaContent(html, "og:description")
    if (!desc.isNullOrBlank()) return desc
    val titleTag = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
    return titleTag?.groupValues?.get(1)?.trim() ?: "Facebook Video"
}

private fun extractFbThumbnail(html: String): String {
    return extractMetaContent(html, "og:image") ?: ""
}

private fun String.unescapeUrl(): String {
    return this
        .replace("\\/", "/")
        .replace("\\u002F", "/")
        .replace("\\u0026", "&")
        .replace("\\", "")
        .trim()
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
