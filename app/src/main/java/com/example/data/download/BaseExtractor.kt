package com.example.data.download

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
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
    val audioUrl: String?,
    val httpHeaders: Map<String, String>? = null,
    val sourceUrl: String? = null
)

object InstagramCookieStore {
    private const val TAG = "InstagramCookieStore"
    private var cachedCookies: String? = null
    private var contextRef: Context? = null

    fun init(context: Context) {
        contextRef = context.applicationContext
        val prefs = context.getSharedPreferences("instagram_cookies", Context.MODE_PRIVATE)
        cachedCookies = prefs.getString("cookies", null)
        Log.d(TAG, "Initialized, hasCookies=${!cachedCookies.isNullOrBlank()}")
    }

    fun setCookies(cookieString: String) {
        cachedCookies = cookieString
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("instagram_cookies", Context.MODE_PRIVATE)
            prefs.edit().putString("cookies", cookieString).apply()
            Log.d(TAG, "Cookies saved: ${cookieString.length} chars")
        } else {
            Log.w(TAG, "Context not available, cookies only cached in memory")
        }
    }

    fun getCookies(): String {
        if (cachedCookies != null && cachedCookies!!.isNotBlank()) return cachedCookies!!
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("instagram_cookies", Context.MODE_PRIVATE)
            cachedCookies = prefs.getString("cookies", null) ?: ""
        }
        return cachedCookies ?: ""
    }

    fun clearCookies() {
        cachedCookies = ""
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("instagram_cookies", Context.MODE_PRIVATE)
            prefs.edit().remove("cookies").apply()
        }
        Log.d(TAG, "Cookies cleared")
    }

    fun hasCookies(): Boolean = getCookies().isNotBlank()

    fun parseNetscapeCookies(netscapeContent: String): String {
        val cookies = mutableListOf<String>()
        netscapeContent.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val parts = trimmed.split("\t")
            if (parts.size >= 7) {
                val name = parts[5]
                val value = parts[6]
                if (name.isNotBlank() && value.isNotBlank()) {
                    cookies.add("$name=$value")
                }
            }
        }
        return cookies.joinToString("; ")
    }

    fun parseRawCookies(rawCookies: String): String {
        return rawCookies.trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace(Regex("\\s+"), " ")
    }
}

object FacebookCookieStore {
    private const val TAG = "FacebookCookieStore"
    private var cachedCookies: String? = null
    private var contextRef: Context? = null

    fun init(context: Context) {
        contextRef = context.applicationContext
        val prefs = context.getSharedPreferences("facebook_cookies", Context.MODE_PRIVATE)
        cachedCookies = prefs.getString("cookies", null)
        Log.d(TAG, "Initialized, hasCookies=${!cachedCookies.isNullOrBlank()}")
    }

    fun setCookies(cookieString: String) {
        cachedCookies = cookieString
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("facebook_cookies", Context.MODE_PRIVATE)
            prefs.edit().putString("cookies", cookieString).apply()
            Log.d(TAG, "Cookies saved: ${cookieString.length} chars")
        }
    }

    fun getCookies(): String {
        if (cachedCookies != null && cachedCookies!!.isNotBlank()) return cachedCookies!!
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("facebook_cookies", Context.MODE_PRIVATE)
            cachedCookies = prefs.getString("cookies", null) ?: ""
        }
        return cachedCookies ?: ""
    }

    fun clearCookies() {
        cachedCookies = ""
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("facebook_cookies", Context.MODE_PRIVATE)
            prefs.edit().remove("cookies").apply()
        }
        Log.d(TAG, "Cookies cleared")
    }

    fun hasCookies(): Boolean = getCookies().isNotBlank()
}

internal const val EXTRACTOR_TAG = "VideoExtractor"
internal const val MOBILE_API_HOST = "api16-normal-c-useast1a.tiktokv.com"
internal val DEVICE_ID = (7250000000000000000L + (Math.random() * 750000000000000L).toLong()).toString()
internal const val APP_VERSION = "35.1.3"
internal const val MOBILE_UA = "com.zhiliaoapp.musically/2023501030 (Linux; U; Android 13; en_US; Pixel 7; Build/TD1A.220804.031; Cronet/58.0.2991.0)"

internal val extractorClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(TikTokCookieStore.cookieJar)
        .build()
}

internal val extractorMoshi: Moshi by lazy {
    Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
}

internal val rootMapType = Types.newParameterizedType(
    Map::class.java, String::class.java, Any::class.java
)

fun getDefaultHeaders(isMobile: Boolean = false): Map<String, String> {
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

fun fetchPageHtml(url: String, httpClient: OkHttpClient = extractorClient): String? {
    return try {
        val requestBuilder = Request.Builder().url(url)
        val isFacebook = url.contains("facebook.com") || url.contains("fb.watch") || url.contains("fb.com")
        if (isFacebook) {
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
            requestBuilder.header("Sec-Fetch-Dest", "document")
            requestBuilder.header("Sec-Fetch-Mode", "navigate")
            requestBuilder.header("Sec-Fetch-Site", "none")
            requestBuilder.header("Sec-Ch-Ua", "\"Chromium\";v=\"125\", \"Google Chrome\";v=\"125\"")
            requestBuilder.header("Sec-Ch-Ua-Mobile", "?1")
            requestBuilder.header("Sec-Ch-Ua-Platform", "\"Android\"")
            requestBuilder.header("Upgrade-Insecure-Requests", "1")
        } else {
            getDefaultHeaders(false).forEach { (k, v) -> requestBuilder.header(k, v) }
        }
        val request = requestBuilder.build()
        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                Log.w(EXTRACTOR_TAG, "HTTP ${resp.code} fetching page")
                return null
            }
            val html = resp.body?.string()
            if (html.isNullOrBlank()) {
                Log.w(EXTRACTOR_TAG, "Empty response body")
                return null
            }
            html
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "Failed to fetch page HTML", e)
        null
    }
}

fun resolveRedirect(url: String): String? {
    if (!url.contains("vt.tiktok.com") && !url.contains("vm.tiktok.com")) return url
    return try {
        val request = Request.Builder().url(url).build()
        extractorClient.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    } catch (e: Throwable) {
        Log.w(EXTRACTOR_TAG, "Redirect resolution failed", e)
        url
    }
}

fun extractItemId(url: String): String? {
    val videoPattern = Pattern.compile("""(/video/|/item/)(\d+)""", Pattern.CASE_INSENSITIVE)
    val matcher = videoPattern.matcher(url)
    if (matcher.find()) return matcher.group(2)
    return null
}

fun extractItemIdFromHtml(url: String): String? {
    try {
        val html = fetchPageHtml(url) ?: return null
        val idPattern = Pattern.compile(
            """"id"\s*:\s*"(\d+)"|"video_id"\s*:\s*"(\d+)"|itemId=(\d+)|/video/(\d+)|/item/(\d+)""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = idPattern.matcher(html)
        if (matcher.find()) {
            for (i in 1..5) {
                val g = matcher.group(i)
                if (g != null) return g
            }
        }
    } catch (e: Exception) {
        Log.w(EXTRACTOR_TAG, "extractItemIdFromHtml failed", e)
    }
    return null
}

fun extractMetaContent(html: String, property: String): String? {
    val pattern = Pattern.compile(
        """<meta\s+[^>]*property=["']${Pattern.quote(property)}["'][^>]*content=["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    val matcher = pattern.matcher(html)
    return if (matcher.find()) matcher.group(1) else null
}

fun extractJsonString(json: String, key: String): String? {
    val pattern = Pattern.compile(""""${Pattern.quote(key)}"\s*:\s*"((?:[^"\\]|\\.)*)"""", Pattern.CASE_INSENSITIVE)
    val matcher = pattern.matcher(json)
    return if (matcher.find()) matcher.group(1) else null
}

fun extractBalancedJson(text: String, startIndex: Int, open: Char = '{', close: Char = '}'): String? {
    var depth = 0
    var start = -1
    for (i in startIndex until text.length) {
        val c = text[i]
        if (c == open) {
            if (depth == 0) start = i
            depth++
        } else if (c == close) {
            depth--
            if (depth == 0 && start != -1) {
                return text.substring(start, i + 1)
            }
        }
    }
    return null
}
