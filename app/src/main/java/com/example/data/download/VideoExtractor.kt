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

/**
 * Instagram cookie storage for authenticated extraction.
 * Users can export cookies from browser and import into app.
 */
object InstagramCookieStore {
    private const val TAG = "InstagramCookieStore"
    private var cachedCookies: String? = null
    private var contextRef: android.content.Context? = null
    
    fun init(context: android.content.Context) {
        contextRef = context.applicationContext
        val prefs = context.getSharedPreferences("instagram_cookies", android.content.Context.MODE_PRIVATE)
        cachedCookies = prefs.getString("cookies", null)
        Log.d(TAG, "Initialized, hasCookies=${!cachedCookies.isNullOrBlank()}")
    }
    
    fun setCookies(cookieString: String) {
        cachedCookies = cookieString
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("instagram_cookies", android.content.Context.MODE_PRIVATE)
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
            val prefs = ctx.getSharedPreferences("instagram_cookies", android.content.Context.MODE_PRIVATE)
            cachedCookies = prefs.getString("cookies", null) ?: ""
        }
        return cachedCookies ?: ""
    }
    
    fun clearCookies() {
        cachedCookies = ""
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("instagram_cookies", android.content.Context.MODE_PRIVATE)
            prefs.edit().remove("cookies").apply()
        }
        Log.d(TAG, "Cookies cleared")
    }
    
    fun hasCookies(): Boolean {
        return getCookies().isNotBlank()
    }
    
    /**
     * Parse Netscape cookie format (from browser export) and convert to header format.
     * Example input:
     * # Netscape HTTP Cookie File
     * .instagram.com	TRUE	/	FALSE	0	sessionid	ABC123
     * .instagram.com	TRUE	/	FALSE	0	ds_user_id	123456
     */
    fun parseNetscapeCookies(netscapeContent: String): String {
        val cookies = mutableListOf<String>()
        
        netscapeContent.lines().forEach { line ->
            val trimmed = line.trim()
            // Skip comments and empty lines
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
    
    /**
     * Parse raw cookie header string (from browser dev tools).
     * Example: "sessionid=ABC123; ds_user_id=123456; ig_did=XYZ"
     */
    fun parseRawCookies(rawCookies: String): String {
        return rawCookies.trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace(Regex("\\s+"), " ")
    }
}

/**
 * Facebook cookie storage for authenticated extraction.
 */
object FacebookCookieStore {
    private const val TAG = "FacebookCookieStore"
    private var cachedCookies: String? = null
    private var contextRef: android.content.Context? = null

    fun init(context: android.content.Context) {
        contextRef = context.applicationContext
        val prefs = context.getSharedPreferences("facebook_cookies", android.content.Context.MODE_PRIVATE)
        cachedCookies = prefs.getString("cookies", null)
        Log.d(TAG, "Initialized, hasCookies=${!cachedCookies.isNullOrBlank()}")
    }

    fun setCookies(cookieString: String) {
        cachedCookies = cookieString
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("facebook_cookies", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("cookies", cookieString).apply()
            Log.d(TAG, "Cookies saved: ${cookieString.length} chars")
        }
    }

    fun getCookies(): String {
        if (cachedCookies != null && cachedCookies!!.isNotBlank()) return cachedCookies!!
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("facebook_cookies", android.content.Context.MODE_PRIVATE)
            cachedCookies = prefs.getString("cookies", null) ?: ""
        }
        return cachedCookies ?: ""
    }

    fun clearCookies() {
        cachedCookies = ""
        val ctx = contextRef
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences("facebook_cookies", android.content.Context.MODE_PRIVATE)
            prefs.edit().remove("cookies").apply()
        }
        Log.d(TAG, "Cookies cleared")
    }

    fun hasCookies(): Boolean {
        return getCookies().isNotBlank()
    }
}

object VideoExtractor {
    private const val TAG = "VideoExtractor"
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

            // ── Platform routing ──────────────────────────────────
            val isInstagram = url.contains("instagram.com") || url.contains("instagr.am")
            val isFacebook = url.contains("facebook.com") || url.contains("fb.watch") || url.contains("fb.com")
            val isTwitter = url.contains("twitter.com") || url.contains("x.com") || url.contains("t.co/")
            val isReddit = url.contains("reddit.com") || url.contains("redd.it")
            val isPinterest = url.contains("pinterest.com") || url.contains("pin.it")
            val isSoundcloud = url.contains("soundcloud.com")
            val isVimeo = url.contains("vimeo.com") || url.contains("player.vimeo.com")
            val isTwitch = url.contains("twitch.tv") || url.contains("clips.twitch.tv")
            val isDailymotion = url.contains("dailymotion.com") || url.contains("dai.ly")
            val isTumblr = url.contains("tumblr.com")
            val isTiktok = url.contains("tiktok.com") || url.contains("vt.tiktok.com") || url.contains("vm.tiktok.com")
            val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")
            val isLinkedIn = url.contains("linkedin.com") || url.contains("linke.in")
            val isSpotify = url.contains("spotify.com") || url.contains("open.spotify.com")
            val isTwitchClip = url.contains("clips.twitch.tv")
            val isStreamable = url.contains("streamable.com")
            val isImgur = url.contains("imgur.com")
            val isFlickr = url.contains("flickr.com")
            val isRumble = url.contains("rumble.com")
            val isOdysee = url.contains("odysee.com") || url.contains("lbry.tv")
            val isBitchute = url.contains("bitchute.com")
            val isDailymotionAlt = url.contains("dai.ly")

            // ── Instagram ────────────────────────────────────────
            if (isInstagram) {
                val igResult = extractInstagram(url)
                if (igResult != null) {
                    Log.d(TAG, "Success via Instagram extraction: ${igResult.title}")
                    return Result.success(igResult)
                }
                return Result.failure(Exception("Could not extract Instagram video. The video may be private or the post is restricted."))
            }

            // ── Facebook ─────────────────────────────────────────
            if (isFacebook) {
                val fbResult = extractFacebook(url)
                if (fbResult != null) {
                    Log.d(TAG, "Success via Facebook extraction: ${fbResult.title}")
                    return Result.success(fbResult)
                }
                return Result.failure(Exception("Could not extract Facebook video. The video may be private or the link is invalid."))
            }

            // ── Twitter / X ──────────────────────────────────────
            if (isTwitter) {
                val twResult = extractTwitter(url)
                if (twResult != null) {
                    Log.d(TAG, "Success via Twitter extraction: ${twResult.title}")
                    return Result.success(twResult)
                }
                return Result.failure(Exception("Could not extract Twitter/X video. The tweet may not contain a video."))
            }

            // ── Reddit ───────────────────────────────────────────
            if (isReddit) {
                val rdResult = extractReddit(url)
                if (rdResult != null) {
                    Log.d(TAG, "Success via Reddit extraction: ${rdResult.title}")
                    return Result.success(rdResult)
                }
                return Result.failure(Exception("Could not extract Reddit video. The post may not contain a video."))
            }

            // ── Pinterest ────────────────────────────────────────
            if (isPinterest) {
                val pinResult = extractPinterest(url)
                if (pinResult != null) {
                    Log.d(TAG, "Success via Pinterest extraction: ${pinResult.title}")
                    return Result.success(pinResult)
                }
                return Result.failure(Exception("Could not extract Pinterest video."))
            }

            // ── SoundCloud ───────────────────────────────────────
            if (isSoundcloud) {
                val scResult = extractSoundCloud(url)
                if (scResult != null) {
                    Log.d(TAG, "Success via SoundCloud extraction: ${scResult.title}")
                    return Result.success(scResult)
                }
                return Result.failure(Exception("Could not extract SoundCloud audio. The track may be private."))
            }

            // ── Vimeo ────────────────────────────────────────────
            if (isVimeo) {
                val vmResult = extractVimeo(url)
                if (vmResult != null) {
                    Log.d(TAG, "Success via Vimeo extraction: ${vmResult.title}")
                    return Result.success(vmResult)
                }
                return Result.failure(Exception("Could not extract Vimeo video."))
            }

            // ── Twitch ───────────────────────────────────────────
            if (isTwitch) {
                val twResult = extractTwitch(url)
                if (twResult != null) {
                    Log.d(TAG, "Success via Twitch extraction: ${twResult.title}")
                    return Result.success(twResult)
                }
                return Result.failure(Exception("Could not extract Twitch clip."))
            }

            // ── Dailymotion ──────────────────────────────────────
            if (isDailymotion) {
                val dmResult = extractDailymotion(url)
                if (dmResult != null) {
                    Log.d(TAG, "Success via Dailymotion extraction: ${dmResult.title}")
                    return Result.success(dmResult)
                }
                return Result.failure(Exception("Could not extract Dailymotion video."))
            }

            // ── Tumblr ───────────────────────────────────────────
            if (isTumblr) {
                val tbResult = extractTumblr(url)
                if (tbResult != null) {
                    Log.d(TAG, "Success via Tumblr extraction: ${tbResult.title}")
                    return Result.success(tbResult)
                }
                return Result.failure(Exception("Could not extract Tumblr video."))
            }

            // ── Generic / Other websites ──────────────────────────
            // Try generic extraction for any unknown website
            val genericResult = extractGeneric(url)
            if (genericResult != null) {
                Log.d(TAG, "Success via generic extraction: ${genericResult.title}")
                return Result.success(genericResult)
            }

            // ── TikTok extraction ────────────────────────────────

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

            Log.e(TAG, "All extraction strategies failed for URL: $url")
            Result.failure(Exception("Could not extract video from this URL. The video may be private, password-protected, or hosted on an unsupported platform."))
        } catch (e: IOException) {
            Log.e(TAG, "Network error extracting TikTok video", e)
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "Failed to connect to TikTok"}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting TikTok video", e)
            Result.failure(Exception("Extraction error: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    // ── Instagram extraction (API-based, no cookies needed!) ───────

    private fun extractInstagram(url: String): TikTokVideoData? {
        val shortcode = extractInstagramShortcode(url) ?: return null
        Log.d(TAG, "Instagram shortcode: $shortcode")

        val igClient = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // Strategy 1: Third-party API (works without auth!)
        try {
            val apiResult = tryInstagramAPI(url, igClient)
            if (apiResult != null) {
                Log.d(TAG, "Instagram: success via third-party API")
                return apiResult
            }
        } catch (e: Exception) {
            Log.d(TAG, "Instagram API failed: ${e.message}")
        }

        // Strategy 2: Embed page with /captioned/ suffix (mobile UA)
        try {
            val embedResult = tryInstagramEmbedCaptioned(shortcode, igClient)
            if (embedResult != null) {
                Log.d(TAG, "Instagram: success via embed/captioned")
                return embedResult
            }
        } catch (e: Exception) {
            Log.d(TAG, "Instagram embed/captioned failed: ${e.message}")
        }

        // Strategy 3: GraphQL with session warmup (no login needed!)
        try {
            val graphqlResult = tryInstagramGraphQL(shortcode, igClient)
            if (graphqlResult != null) {
                Log.d(TAG, "Instagram: success via GraphQL")
                return graphqlResult
            }
        } catch (e: Exception) {
            Log.d(TAG, "Instagram GraphQL failed: ${e.message}")
        }

        // Strategy 4: Regular embed page (fallback)
        try {
            val embedResult = tryInstagramEmbed(shortcode, igClient)
            if (embedResult != null) {
                Log.d(TAG, "Instagram: success via regular embed")
                return embedResult
            }
        } catch (e: Exception) {
            Log.d(TAG, "Instagram embed failed: ${e.message}")
        }

        return null
    }

    private fun tryInstagramAPI(url: String, client: OkHttpClient): TikTokVideoData? {
        val apiUrl = "https://r-gengpt-api.vercel.app/api/video/download?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        val request = Request.Builder().url(apiUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36")
            .get().build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        if (!response.isSuccessful) return null

        Log.d(TAG, "Instagram API response length: ${body.length}")

        val json = try { org.json.JSONObject(body) } catch (e: Exception) { return null }
        val data = json.optJSONObject("data") ?: return null
        val medias = data.optJSONArray("medias") ?: return null

        // Find video media
        for (i in 0 until medias.length()) {
            val media = medias.optJSONObject(i) ?: continue
            val type = media.optString("type", "")
            if (type == "video") {
                val videoUrl = media.optString("url", "")
                if (videoUrl.isNotBlank()) {
                    return TikTokVideoData(
                        id = "",
                        title = data.optString("title", ""),
                        author = "",
                        authorId = "",
                        thumbnail = data.optString("thumbnail", ""),
                        duration = data.optLong("duration", 0L),
                        videoUrl = videoUrl,
                        videoUrlNoWatermark = null,
                        audioUrl = null
                    )
                }
            }
        }

        // If no video found, try first media as image
        if (medias.length() > 0) {
            val media = medias.optJSONObject(0)
            val imageUrl = media?.optString("url", "") ?: ""
            if (imageUrl.isNotBlank()) {
                return TikTokVideoData(
                    id = "",
                    title = data.optString("title", ""),
                    author = "",
                    authorId = "",
                    thumbnail = data.optString("thumbnail", ""),
                    duration = 0L,
                    videoUrl = imageUrl,
                    videoUrlNoWatermark = null,
                    audioUrl = null
                )
            }
        }

        return null
    }

    private fun tryInstagramEmbedCaptioned(shortcode: String, client: OkHttpClient): TikTokVideoData? {
        val mobileUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

        val request = Request.Builder()
            .url("https://www.instagram.com/p/$shortcode/embed/captioned/")
            .header("User-Agent", mobileUA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Referer", "https://www.instagram.com/")
            .get().build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return null
        if (!response.isSuccessful) return null

        Log.d(TAG, "Instagram embed/captioned length: ${html.length}")

        // video_url JSON
        val videoUrlMatch = Regex(""""video_url":"(https://[^"]+)"""").find(html)
        if (videoUrlMatch != null) {
            val videoUrl = videoUrlMatch.groupValues[1].replace("\\/", "/").replace("\\u0026", "&")
            return TikTokVideoData(
                id = shortcode, title = extractInstagramCaption(html),
                author = extractInstagramAuthor(html), authorId = "",
                thumbnail = extractMetaContent(html, "og:image") ?: "", duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        }

        // display_url JSON
        val displayUrlMatch = Regex(""""display_url":"(https://[^"]+)"""").find(html)
        if (displayUrlMatch != null) {
            val url = displayUrlMatch.groupValues[1].replace("\\/", "/").replace("\\u0026", "&")
            return TikTokVideoData(
                id = shortcode, title = extractInstagramCaption(html),
                author = extractInstagramAuthor(html), authorId = "",
                thumbnail = "", duration = 0L,
                videoUrl = url, videoUrlNoWatermark = null, audioUrl = null
            )
        }

        // og:image fallback
        val ogImage = extractMetaContent(html, "og:image")
        if (!ogImage.isNullOrBlank()) {
            return TikTokVideoData(
                id = shortcode, title = extractInstagramCaption(html),
                author = extractInstagramAuthor(html), authorId = "",
                thumbnail = ogImage, duration = 0L,
                videoUrl = ogImage, videoUrlNoWatermark = null, audioUrl = null
            )
        }

        return null
    }

    private fun tryInstagramGraphQL(shortcode: String, client: OkHttpClient): TikTokVideoData? {
        val desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"

        // Step 1: Warm up session cookies
        val warmup = client.newCall(
            Request.Builder().url("https://www.instagram.com/")
                .header("User-Agent", desktopUA).get().build()
        ).execute()
        warmup.close()

        // Step 2: Visit reel page to get fb_dtsg token
        val reelPage = client.newCall(
            Request.Builder().url("https://www.instagram.com/reel/$shortcode/")
                .header("User-Agent", desktopUA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://www.instagram.com/")
                .get().build()
        ).execute()
        val reelHtml = reelPage.body?.string() ?: ""
        reelPage.close()

        // Step 3: Extract fb_dtsg token
        val fbDtsg = Regex(""""token":"(AdQ[^"]+)"""").find(reelHtml)?.groupValues?.get(1)
            ?: Regex(""""fb_dtsg","[^"]*","[^"]*","([^"]+)"""").find(reelHtml)?.groupValues?.get(1)
            ?: Regex(""""DTSGInitData"[^}]+"token":"([^"]+)"""").find(reelHtml)?.groupValues?.get(1)

        val csrfToken = Regex(""""csrf_token":"([^"]+)"""").find(reelHtml)?.groupValues?.get(1) ?: ""

        // Step 4: POST to GraphQL
        val body = okhttp3.FormBody.Builder()
            .addEncoded("variables", """{"shortcode":"$shortcode"}""")
            .add("doc_id", "8845758582119845")
            .add("server_timestamps", "true")
            .apply { if (fbDtsg != null) add("fb_dtsg", fbDtsg) }
            .build()

        val request = Request.Builder()
            .url("https://www.instagram.com/graphql/query")
            .post(body)
            .header("User-Agent", desktopUA)
            .header("Accept", "*/*")
            .header("Referer", "https://www.instagram.com/reel/$shortcode/")
            .header("X-IG-App-ID", "936619743392459")
            .header("X-CSRFToken", csrfToken)
            .header("X-FB-Friendly-Name", "PolarisPostRootQuery")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        val resp = client.newCall(request).execute()
        val respBody = resp.body?.string() ?: return null
        if (!resp.isSuccessful) return null

        Log.d(TAG, "Instagram GraphQL response length: ${respBody.length}")

        val media = try {
            org.json.JSONObject(respBody).getJSONObject("data")
                .optJSONObject("xdt_shortcode_media")
        } catch (e: Exception) {
            null
        } ?: return null

        return if (media.optBoolean("is_video", false)) {
            val videoUrl = media.optString("video_url").takeIf { it.isNotEmpty() } ?: return null
            TikTokVideoData(
                id = shortcode,
                title = media.optString("caption", "").take(200),
                author = media.optJSONObject("owner")?.optString("username") ?: "",
                authorId = "",
                thumbnail = media.optString("display_url", ""),
                duration = media.optLong("video_duration", 0L),
                videoUrl = videoUrl,
                videoUrlNoWatermark = null,
                audioUrl = null
            )
        } else {
            val displayUrl = media.optString("display_url").takeIf { it.isNotEmpty() } ?: return null
            TikTokVideoData(
                id = shortcode,
                title = media.optString("caption", "").take(200),
                author = media.optJSONObject("owner")?.optString("username") ?: "",
                authorId = "",
                thumbnail = displayUrl,
                duration = 0L,
                videoUrl = displayUrl,
                videoUrlNoWatermark = null,
                audioUrl = null
            )
        }
    }

    private fun tryInstagramEmbed(shortcode: String, client: OkHttpClient): TikTokVideoData? {
        val mobileUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        val request = Request.Builder().url("https://www.instagram.com/p/$shortcode/embed/")
            .header("User-Agent", mobileUA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", "https://www.instagram.com/")
            .get().build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return null
        if (!response.isSuccessful || html.length < 500) return null

        val videoUrlMatch = Regex(""""video_url"\s*:\s*"([^"]+)"""").find(html)
            ?: Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
        if (videoUrlMatch != null) {
            val videoUrl = videoUrlMatch.groupValues[1].replace("\\u002F", "/").replace("\\/", "/")
            return TikTokVideoData(
                id = shortcode, title = "", author = "", authorId = "",
                thumbnail = "", duration = 0L,
                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        }

        return null
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern = Pattern.compile(
            """<meta\s+[^>]*property=["']${Regex.escape(property)}["'][^>]*content=["']([^"']+)["']""",
            Pattern.CASE_INSENSITIVE
        )
        return if (pattern.matcher(html).find()) pattern.matcher(html).group(1) else null
    }

    private fun extractInstagramAuthor(html: String): String {
        // Try og:title first (e.g. "Video by @username on Instagram")
        val title = extractMetaContent(html, "og:title") ?: ""
        val atMatch = Regex("""@(\w+)""").find(title)
        if (atMatch != null) return "@${atMatch.groupValues[1]}"

        // Try meta description
        val desc = extractMetaContent(html, "og:description") ?: ""
        val descMatch = Regex("""@(\w+)""").find(desc)
        if (descMatch != null) return "@${descMatch.groupValues[1]}"

        return ""
    }

    private fun extractInstagramShortcode(url: String): String? {
        val pattern = Pattern.compile("""/(?:p|reel|tv)/([a-zA-Z0-9_-]+)""")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractInstagramCaption(html: String): String {
        val desc = extractMetaContent(html, "og:description") ?: ""
        if (desc.isNotBlank()) return desc.take(200)

        // Try extracting from JSON data
        val captionMatch = Regex(""""caption"\s*:\s*"([^"]{1,200})"""").find(html)
        if (captionMatch != null) return captionMatch.groupValues[1].replace("\\n", "\n")

        return ""
    }

    // ── Facebook extraction ─────────────────────────────────────────

    private fun extractFacebook(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            // Strategy 0: Third-party API (yt-dlp based, same as Instagram)
            val apiResult = tryFacebookAPI(url, igClient)
            if (apiResult != null) {
                Log.d(TAG, "Facebook API extraction success")
                return apiResult
            }

            val canonicalUrl = resolveFacebookUrl(igClient, url)
            Log.d(TAG, "Facebook canonical URL: $canonicalUrl")

            val embedResult = extractFacebookEmbed(igClient, canonicalUrl)
            if (embedResult != null) {
                Log.d(TAG, "Facebook embed extraction success")
                return embedResult
            }

            val touchResult = extractFacebookTouch(igClient, canonicalUrl)
            if (touchResult != null) {
                Log.d(TAG, "Facebook touch extraction success")
                return touchResult
            }

            val mbasicResult = extractFacebookViaMbasic(igClient, canonicalUrl)
            if (mbasicResult != null) {
                Log.d(TAG, "Facebook mbasic extraction success")
                return mbasicResult
            }

            val standardResult = extractFacebookStandard(igClient, canonicalUrl)
            if (standardResult != null) {
                Log.d(TAG, "Facebook standard extraction success")
                return standardResult
            }

            Log.w(TAG, "Facebook: all strategies failed for $canonicalUrl")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractFacebook failed", e)
            return null
        }
    }

    private fun tryFacebookAPI(url: String, client: OkHttpClient): TikTokVideoData? {
        try {
            val apiUrl = "https://r-gengpt-api.vercel.app/api/video/download?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val request = Request.Builder().url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36")
                .get().build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (!response.isSuccessful) return null

            Log.d(TAG, "Facebook API response length: ${body.length}")

            val json = try { org.json.JSONObject(body) } catch (e: Exception) { return null }
            val data = json.optJSONObject("data") ?: return null
            val medias = data.optJSONArray("medias") ?: return null

            for (i in 0 until medias.length()) {
                val media = medias.optJSONObject(i) ?: continue
                val type = media.optString("type", "")
                if (type == "video") {
                    val videoUrl = media.optString("url", "")
                    if (videoUrl.isNotBlank()) {
                        return TikTokVideoData(
                            id = "",
                            title = data.optString("title", ""),
                            author = data.optString("author", "Facebook"),
                            authorId = "",
                            thumbnail = data.optString("thumbnail", ""),
                            duration = data.optLong("duration", 0L),
                            videoUrl = videoUrl,
                            videoUrlNoWatermark = null,
                            audioUrl = null
                        )
                    }
                }
            }
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Facebook API failed", e)
            return null
        }
    }

    private fun resolveFacebookUrl(client: OkHttpClient, url: String): String {
        try {
            val builder = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            val response = client.newCall(builder.build()).execute()
            return response.request.url.toString()
        } catch (e: Exception) {
            return url
        }
    }

    private fun extractFacebookEmbed(client: OkHttpClient, url: String): TikTokVideoData? {
        try {
            val embedUrl = "https://www.facebook.com/plugins/video.php?href=${java.net.URLEncoder.encode(url, "UTF-8")}&show_text=false&width=560"
            val builder = Request.Builder().url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")

            val response = client.newCall(builder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Facebook embed page length: ${html.length}")

            return extractFacebookVideoFromHtml(html)
        } catch (e: Exception) {
            Log.w(TAG, "Facebook embed failed", e)
            return null
        }
    }

    private fun extractFacebookTouch(client: OkHttpClient, url: String): TikTokVideoData? {
        try {
            val touchUrl = url
                .replace("https://www.facebook.com", "https://touch.facebook.com")
                .replace("https://web.facebook.com", "https://touch.facebook.com")
                .replace("https://m.facebook.com", "https://touch.facebook.com")

            val builder = Request.Builder().url(touchUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")

            val response = client.newCall(builder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Facebook touch page length: ${html.length}")

            return extractFacebookVideoFromHtml(html)
        } catch (e: Exception) {
            Log.w(TAG, "Facebook touch failed", e)
            return null
        }
    }

    private fun extractFacebookViaMbasic(client: OkHttpClient, url: String): TikTokVideoData? {
        try {
            val mbasicUrl = url
                .replace("https://www.facebook.com", "https://mbasic.facebook.com")
                .replace("https://m.facebook.com", "https://mbasic.facebook.com")
                .replace("https://web.facebook.com", "https://mbasic.facebook.com")
                .replace("https://touch.facebook.com", "https://mbasic.facebook.com")

            val builder = Request.Builder().url(mbasicUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")

            val response = client.newCall(builder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Facebook mbasic page length: ${html.length}")

            return extractFacebookVideoFromHtml(html)
        } catch (e: Exception) {
            Log.w(TAG, "Facebook mbasic failed", e)
            return null
        }
    }

    private fun extractFacebookStandard(client: OkHttpClient, url: String): TikTokVideoData? {
        try {
            val builder = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")

            val response = client.newCall(builder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Facebook standard page length: ${html.length}")

            return extractFacebookVideoFromHtml(html)
        } catch (e: Exception) {
            Log.w(TAG, "Facebook standard extraction failed", e)
            return null
        }
    }

    private fun extractFacebookVideoFromHtml(html: String): TikTokVideoData? {
        if (html.length < 100) return null

        val srcUrl = extractFbSrcUrl(html)
        if (srcUrl != null) {
            return TikTokVideoData(
                id = "", title = extractMetaContent(html, "og:title") ?: "Facebook Video", author = "Facebook", authorId = "",
                thumbnail = extractMetaContent(html, "og:image") ?: "", duration = 0L,
                videoUrl = srcUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        }

        val ogVideoUrl = extractMetaContent(html, "og:video")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video:secure_url")
        if (!ogVideoUrl.isNullOrBlank()) {
            return TikTokVideoData(
                id = "", title = extractMetaContent(html, "og:title") ?: "Facebook Video", author = "Facebook", authorId = "",
                thumbnail = extractMetaContent(html, "og:image") ?: "", duration = 0L,
                videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
            )
        }

        val videoTagPatterns = listOf(
            Pattern.compile("""<video[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<source[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<video[^>]+data-src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
        )
        for (pattern in videoTagPatterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val videoUrl = matcher.group(1)
                if (!videoUrl.isNullOrBlank() && videoUrl.contains("http")) {
                    return TikTokVideoData(
                        id = "", title = extractMetaContent(html, "og:title") ?: "Facebook Video", author = "Facebook", authorId = "",
                        thumbnail = extractMetaContent(html, "og:image") ?: "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }
        }

        val jsonPatterns = listOf(
            Pattern.compile(""""play_url"\s*:\s*\{\s*"uri"\s*:\s*"([^"]+)""""),
            Pattern.compile(""""play_url"\s*:\s*"([^"]+)""""),
            Pattern.compile(""""video_url"\s*:\s*"([^"]+)""""),
        )
        for (pattern in jsonPatterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val videoUrl = matcher.group(1)?.replace("\\u002F", "/")?.replace("\\/", "/")
                if (!videoUrl.isNullOrBlank() && videoUrl.contains("http")) {
                    return TikTokVideoData(
                        id = "", title = extractMetaContent(html, "og:title") ?: "Facebook Video", author = "Facebook", authorId = "",
                        thumbnail = extractMetaContent(html, "og:image") ?: "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }
        }

        val cdnPattern = Pattern.compile("""(https?://[^"'\s]+(?:fbcdn|scontent)[^"'\s]+\.mp4[^"'\s]*)""")
        val cdnMatcher = cdnPattern.matcher(html)
        if (cdnMatcher.find()) {
            val videoUrl = cdnMatcher.group(1)
            if (!videoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = "", title = extractMetaContent(html, "og:title") ?: "Facebook Video", author = "Facebook", authorId = "",
                    thumbnail = extractMetaContent(html, "og:image") ?: "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }
        }

        val mp4Pattern = Pattern.compile("""(https?://[^"'\s]+\.mp4[^"'\s]*)""")
        val mp4Matcher = mp4Pattern.matcher(html)
        if (mp4Matcher.find()) {
            val videoUrl = mp4Matcher.group(1)
            if (!videoUrl.isNullOrBlank() && !videoUrl.contains("static") && !videoUrl.contains("bundle")) {
                return TikTokVideoData(
                    id = "", title = extractMetaContent(html, "og:title") ?: "Facebook Video", author = "Facebook", authorId = "",
                    thumbnail = extractMetaContent(html, "og:image") ?: "", duration = 0L,
                    videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }
        }

        Log.w(TAG, "Facebook: no video found in HTML (length=${html.length})")
        return null
    }

    private fun extractFbSrcUrl(html: String): String? {
        val patterns = listOf(
            Pattern.compile(""""hd_src"\s*:\s*"([^"]+)""""),
            Pattern.compile(""""sd_src"\s*:\s*"([^"]+)""""),
            Pattern.compile(""""hd_src_no_ratelimit"\s*:\s*"([^"]+)""""),
            Pattern.compile(""""sd_src_no_ratelimit"\s*:\s*"([^"]+)""""),
            Pattern.compile("""hd_src\s*=\s*"([^"]+)"""),
            Pattern.compile("""sd_src\s*=\s*"([^"]+)"""),
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val videoUrl = matcher.group(1)?.replace("\\u002F", "/")?.replace("\\/", "/")
                if (!videoUrl.isNullOrBlank() && videoUrl.contains("http")) {
                    return videoUrl
                }
            }
        }
        return null
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Pattern.compile(""""$key"\s*:\s*"([^"]+)"""")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1)?.replace("\\u002F", "/") else null
    }

    // ── Twitter / X extraction ──────────────────────────────────────

    private fun extractTwitter(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(url)
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")

            val response = igClient.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Twitter page length: ${html.length}")

            // Strategy 1: og:video meta tag
            val ogVideoUrl = extractMetaContent(html, "og:video")
            if (!ogVideoUrl.isNullOrBlank()) {
                val title = extractMetaContent(html, "og:title") ?: ""
                val thumbnail = extractMetaContent(html, "og:image") ?: ""
                val author = extractTwitterAuthor(html)
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 2: twitter:player:stream
            val playerStreamPattern = Pattern.compile(
                """<meta\s+[^>]*name=["']twitter:player:stream["'][^>]*content=["']([^"']+)["']""",
                Pattern.CASE_INSENSITIVE
            )
            val playerStreamMatcher = playerStreamPattern.matcher(html)
            if (playerStreamMatcher.find()) {
                val videoUrl = playerStreamMatcher.group(1)
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    val thumbnail = extractMetaContent(html, "og:image") ?: ""
                    val author = extractTwitterAuthor(html)
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 3: video_url in JSON data
            val videoUrlJsonPattern = Pattern.compile(""""video_url"\s*:\s*"([^"]+)"""")
            val videoUrlJsonMatcher = videoUrlJsonPattern.matcher(html)
            if (videoUrlJsonMatcher.find()) {
                val videoUrl = videoUrlJsonMatcher.group(1)?.replace("\\u002F", "/")?.replace("\\/", "/")
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    val thumbnail = extractMetaContent(html, "og:image") ?: ""
                    val author = extractTwitterAuthor(html)
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 4: CDN URL pattern
            val cdnPattern = Pattern.compile(
                """https?://(?:video\.twimg\.com|pbs\.twimg\.com/media/)[^"'\s<>]+\.mp4[^"'\s<>]*""",
                Pattern.CASE_INSENSITIVE
            )
            val cdnMatcher = cdnPattern.matcher(html)
            if (cdnMatcher.find()) {
                val videoUrl = cdnMatcher.group()?.replace("\\u002F", "/")
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    val author = extractTwitterAuthor(html)
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 5: <video> tag
            val videoTagPattern = Pattern.compile("""<video[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val videoTagMatcher = videoTagPattern.matcher(html)
            if (videoTagMatcher.find()) {
                val videoUrl = videoTagMatcher.group(1)
                if (!videoUrl.isNullOrBlank()) {
                    return TikTokVideoData(
                        id = "", title = extractMetaContent(html, "og:title") ?: "", author = "", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            Log.w(TAG, "Twitter: no video found in page")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractTwitter failed", e)
            return null
        }
    }

    private fun extractTwitterAuthor(html: String): String {
        val title = extractMetaContent(html, "og:title") ?: ""
        // Twitter titles are usually "Author Name on X: ..."
        val atMatch = Regex("""@(\w+)""").find(title)
        if (atMatch != null) return "@${atMatch.groupValues[1]}"
        val desc = extractMetaContent(html, "og:description") ?: ""
        val descMatch = Regex("""@(\w+)""").find(desc)
        if (descMatch != null) return "@${descMatch.groupValues[1]}"
        return ""
    }

    // ── Reddit extraction ───────────────────────────────────────────

    private fun extractReddit(url: String): TikTokVideoData? {
        try {
            // Convert URL to JSON API
            val jsonUrl = if (url.endsWith(".json")) url
            else if (url.contains("?")) "$url&raw_json=1"
            else "$url?raw_json=1"

            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(jsonUrl)
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            requestBuilder.header("Accept", "application/json")

            val response = igClient.newCall(requestBuilder.build()).execute()
            val jsonStr = response.body?.string() ?: return null
            Log.d(TAG, "Reddit JSON length: ${jsonStr.length}")

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)

            // Reddit JSON is an array [post, comments]
            val rootList = adapter.fromJson(jsonStr) as? List<*> ?: return null
            val postData = rootList.firstOrNull() as? Map<*, *> ?: return null
            val data = postData["data"] as? Map<*, *> ?: return null
            val children = data["children"] as? List<*> ?: return null
            val firstChild = children.firstOrNull() as? Map<*, *> ?: return null
            val childData = firstChild["data"] as? Map<*, *> ?: return null

            val title = childData["title"]?.toString() ?: ""
            val author = childData["author"]?.toString() ?: ""
            val thumbnail = childData["thumbnail"]?.toString() ?: ""
            val isVideo = childData["is_video"] as? Boolean ?: false

            if (!isVideo) {
                Log.w(TAG, "Reddit post is not a video")
                return null
            }

            // Get video URL from secure_media or media
            val secureMedia = childData["secure_media"] as? Map<*, *>
            val media = childData["media"] as? Map<*, *>
            val redditVideo = (secureMedia ?: media)?.get("reddit_video") as? Map<*, *>

            if (redditVideo != null) {
                val videoUrl = redditVideo["fallback_url"]?.toString()
                    ?: redditVideo["dash_url"]?.toString()
                    ?: redditVideo["hls_url"]?.toString()

                if (!videoUrl.isNullOrBlank()) {
                    return TikTokVideoData(
                        id = childData["id"]?.toString() ?: "",
                        title = title,
                        author = "r/${childData["subreddit"]}",
                        authorId = author,
                        thumbnail = thumbnail,
                        duration = 0L,
                        videoUrl = videoUrl,
                        videoUrlNoWatermark = null,
                        audioUrl = null
                    )
                }
            }

            Log.w(TAG, "Reddit: no video URL found")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractReddit failed", e)
            return null
        }
    }

    // ── Pinterest extraction ────────────────────────────────────────

    private fun extractPinterest(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(url)
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")

            val response = igClient.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Pinterest page length: ${html.length}")

            // Strategy 1: og:video meta tag
            val ogVideoUrl = extractMetaContent(html, "og:video")
            if (!ogVideoUrl.isNullOrBlank()) {
                val title = extractMetaContent(html, "og:title") ?: ""
                val thumbnail = extractMetaContent(html, "og:image") ?: ""
                return TikTokVideoData(
                    id = "", title = title, author = "Pinterest", authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 2: video_src
            val videoSrcPattern = Pattern.compile("""video_list.*?"url"\s*:\s*"([^"]+)"""")
            val videoSrcMatcher = videoSrcPattern.matcher(html)
            if (videoSrcMatcher.find()) {
                val videoUrl = videoSrcMatcher.group(1)?.replace("\\u002F", "/")
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    val thumbnail = extractMetaContent(html, "og:image") ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = "Pinterest", authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 3: CDN URL pattern
            val cdnPattern = Pattern.compile(
                """https?://(?:v1\.pinimg\.com|i\.pinimg\.com|s-media)[^"'\s<>]*\.mp4[^"'\s<>]*""",
                Pattern.CASE_INSENSITIVE
            )
            val cdnMatcher = cdnPattern.matcher(html)
            if (cdnMatcher.find()) {
                val videoUrl = cdnMatcher.group()?.replace("\\u002F", "/")
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = "Pinterest", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            Log.w(TAG, "Pinterest: no video found")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractPinterest failed", e)
            return null
        }
    }

    // ── SoundCloud extraction ───────────────────────────────────────

    private fun extractSoundCloud(url: String): TikTokVideoData? {
        try {
            // Step 1: Get client_id from the page
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(url)
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val response = igClient.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "SoundCloud page length: ${html.length}")

            // Strategy 1: og:audio meta tag
            val ogAudioUrl = extractMetaContent(html, "og:audio")
            if (!ogAudioUrl.isNullOrBlank()) {
                val title = extractMetaContent(html, "og:title") ?: ""
                val thumbnail = extractMetaContent(html, "og:image") ?: ""
                val author = extractSoundCloudAuthor(html)
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = null, videoUrlNoWatermark = null, audioUrl = ogAudioUrl
                )
            }

            // Strategy 2: Try oEmbed
            val oembedUrl = "https://soundcloud.com/oembed?format=json&url=$url"
            val oembedRequest = Request.Builder().url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build()

            val oembedResponse = igClient.newCall(oembedRequest).execute()
            val oembedJson = oembedResponse.body?.string()
            if (oembedJson != null) {
                val rootMapType = Types.newParameterizedType(
                    Map::class.java, String::class.java, Any::class.java
                )
                val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
                val root = adapter.fromJson(oembedJson)
                if (root != null) {
                    val title = root["title"]?.toString() ?: ""
                    val author = root["author_name"]?.toString() ?: ""
                    val thumbnail = root["thumbnail_url"]?.toString() ?: ""
                    val htmlContent = root["html"]?.toString() ?: ""

                    // Extract audio URL from embed HTML
                    val srcPattern = Pattern.compile("""src=["']([^"']+)["']""")
                    val srcMatcher = srcPattern.matcher(htmlContent)
                    if (srcMatcher.find()) {
                        val audioUrl = srcMatcher.group(1)
                        if (!audioUrl.isNullOrBlank()) {
                            return TikTokVideoData(
                                id = "", title = title, author = author, authorId = "",
                                thumbnail = thumbnail, duration = 0L,
                                videoUrl = null, videoUrlNoWatermark = null, audioUrl = audioUrl
                            )
                        }
                    }
                }
            }

            Log.w(TAG, "SoundCloud: no audio found")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractSoundCloud failed", e)
            return null
        }
    }

    private fun extractSoundCloudAuthor(html: String): String {
        val title = extractMetaContent(html, "og:title") ?: ""
        // SoundCloud titles usually format as "Song Name by Artist"
        val byMatch = Regex("""by\s+(.+?)(?:\s+on\s+SoundCloud)?$""", RegexOption.MULTILINE).find(title)
        if (byMatch != null) return byMatch.groupValues[1].trim()
        return ""
    }

    // ── Vimeo extraction ────────────────────────────────────────────

    private fun extractVimeo(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            // Try oEmbed first
            val oembedUrl = "https://vimeo.com/api/oembed.json?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            val request = Request.Builder().url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build()

            val response = igClient.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return null
            Log.d(TAG, "Vimeo oEmbed response: ${jsonStr.length}")

            val rootMapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            val root = adapter.fromJson(jsonStr) ?: return null

            val title = root["title"]?.toString() ?: ""
            val author = root["author_name"]?.toString() ?: ""
            val thumbnail = root["thumbnail_url"]?.toString() ?: ""
            val htmlContent = root["html"]?.toString() ?: ""

            // Extract video URL from embed HTML
            val videoUrlPattern = Pattern.compile("""https?://(?:player\.vimeo\.com|vimeo\.com)/video/\d+/[^"'\s<>]*""", Pattern.CASE_INSENSITIVE)
            val videoUrlMatcher = videoUrlPattern.matcher(htmlContent)
            if (videoUrlMatcher.find()) {
                val videoUrl = videoUrlMatcher.group()
                if (!videoUrl.isNullOrBlank()) {
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Fallback: Try og:video
            val htmlRequest = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build()
            val htmlResponse = igClient.newCall(htmlRequest).execute()
            val html = htmlResponse.body?.string() ?: return null

            val ogVideoUrl = extractMetaContent(html, "og:video")
            if (!ogVideoUrl.isNullOrBlank()) {
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            Log.w(TAG, "Vimeo: no video found")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractVimeo failed", e)
            return null
        }
    }

    // ── Twitch extraction ───────────────────────────────────────────

    private fun extractTwitch(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(url)
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val response = igClient.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Twitch page length: ${html.length}")

            // Strategy 1: og:video meta tag
            val ogVideoUrl = extractMetaContent(html, "og:video")
            if (!ogVideoUrl.isNullOrBlank()) {
                val title = extractMetaContent(html, "og:title") ?: ""
                val thumbnail = extractMetaContent(html, "og:image") ?: ""
                return TikTokVideoData(
                    id = "", title = title, author = "Twitch", authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 2: video_url in JSON
            val videoUrlPattern = Pattern.compile(""""video_url"\s*:\s*"([^"]+)"""")
            val videoUrlMatcher = videoUrlPattern.matcher(html)
            if (videoUrlMatcher.find()) {
                val videoUrl = videoUrlMatcher.group(1)?.replace("\\u002F", "/")?.replace("\\/", "/")
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    val thumbnail = extractMetaContent(html, "og:image") ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = "Twitch", authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 3: CDN URL pattern for Twitch clips
            val cdnPattern = Pattern.compile(
                """https?://(?:clips\.twitch\.tv|vod-secure\.twitch\.tv|static-cdn\.jtvnw\.net)[^"'\s<>]*\.mp4[^"'\s<>]*""",
                Pattern.CASE_INSENSITIVE
            )
            val cdnMatcher = cdnPattern.matcher(html)
            if (cdnMatcher.find()) {
                val videoUrl = cdnMatcher.group()
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = "Twitch", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 4: <video> tag
            val videoTagPattern = Pattern.compile("""<video[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val videoTagMatcher = videoTagPattern.matcher(html)
            if (videoTagMatcher.find()) {
                val videoUrl = videoTagMatcher.group(1)
                if (!videoUrl.isNullOrBlank()) {
                    return TikTokVideoData(
                        id = "", title = extractMetaContent(html, "og:title") ?: "", author = "Twitch", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            Log.w(TAG, "Twitch: no video found")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractTwitch failed", e)
            return null
        }
    }

    // ── Dailymotion extraction ──────────────────────────────────────

    private fun extractDailymotion(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            // Try oEmbed first
            val oembedUrl = "https://www.dailymotion.com/services/oembed?url=${java.net.URLEncoder.encode(url, "UTF-8")}&format=json"
            val request = Request.Builder().url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build()

            val response = igClient.newCall(request).execute()
            val jsonStr = response.body?.string()
            if (jsonStr != null) {
                val rootMapType = Types.newParameterizedType(
                    Map::class.java, String::class.java, Any::class.java
                )
                val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
                val root = adapter.fromJson(jsonStr)
                if (root != null) {
                    val title = root["title"]?.toString() ?: ""
                    val author = root["author_name"]?.toString() ?: ""
                    val thumbnail = root["thumbnail_url"]?.toString() ?: ""
                    val htmlContent = root["html"]?.toString() ?: ""

                    // Extract video URL from embed HTML
                    val srcPattern = Pattern.compile("""src=["']([^"']+)["']""")
                    val srcMatcher = srcPattern.matcher(htmlContent)
                    if (srcMatcher.find()) {
                        val videoUrl = srcMatcher.group(1)
                        if (!videoUrl.isNullOrBlank()) {
                            return TikTokVideoData(
                                id = "", title = title, author = author, authorId = "",
                                thumbnail = thumbnail, duration = 0L,
                                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                            )
                        }
                    }
                }
            }

            // Fallback: og:video
            val htmlRequest = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build()
            val htmlResponse = igClient.newCall(htmlRequest).execute()
            val html = htmlResponse.body?.string() ?: return null

            val ogVideoUrl = extractMetaContent(html, "og:video")
            if (!ogVideoUrl.isNullOrBlank()) {
                val title = extractMetaContent(html, "og:title") ?: ""
                val thumbnail = extractMetaContent(html, "og:image") ?: ""
                return TikTokVideoData(
                    id = "", title = title, author = "Dailymotion", authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            Log.w(TAG, "Dailymotion: no video found")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractDailymotion failed", e)
            return null
        }
    }

    // ── Tumblr extraction ───────────────────────────────────────────

    private fun extractTumblr(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(url)
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val response = igClient.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Tumblr page length: ${html.length}")

            // Strategy 1: og:video meta tag
            val ogVideoUrl = extractMetaContent(html, "og:video")
            if (!ogVideoUrl.isNullOrBlank()) {
                val title = extractMetaContent(html, "og:title") ?: ""
                val thumbnail = extractMetaContent(html, "og:image") ?: ""
                val author = extractMetaContent(html, "twitter:data1") ?: ""
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 2: <video> tag
            val videoTagPattern = Pattern.compile("""<video[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val videoTagMatcher = videoTagPattern.matcher(html)
            if (videoTagMatcher.find()) {
                val videoUrl = videoTagMatcher.group(1)
                if (!videoUrl.isNullOrBlank()) {
                    return TikTokVideoData(
                        id = "", title = extractMetaContent(html, "og:title") ?: "", author = "", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 3: CDN URL pattern
            val cdnPattern = Pattern.compile(
                """https?://(?:(?:v\d+\.)? tumblr\.com|media\.tumblr\.com)[^"'\s<>]*\.mp4[^"'\s<>]*""",
                Pattern.CASE_INSENSITIVE
            )
            val cdnMatcher = cdnPattern.matcher(html)
            if (cdnMatcher.find()) {
                val videoUrl = cdnMatcher.group()?.replace("\\u002F", "/")
                if (!videoUrl.isNullOrBlank()) {
                    val title = extractMetaContent(html, "og:title") ?: ""
                    return TikTokVideoData(
                        id = "", title = title, author = "", authorId = "",
                        thumbnail = "", duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            Log.w(TAG, "Tumblr: no video found")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractTumblr failed", e)
            return null
        }
    }

    // ── Generic extraction for any website ──────────────────────────

    private fun extractGeneric(url: String): TikTokVideoData? {
        try {
            val igClient = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val requestBuilder = Request.Builder().url(url)
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")

            val response = igClient.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: return null
            Log.d(TAG, "Generic extraction - page length: ${html.length}")

            val title = extractMetaContent(html, "og:title")
                ?: extractMetaContent(html, "title")
                ?: ""
            val thumbnail = extractMetaContent(html, "og:image") ?: ""
            val author = extractMetaContent(html, "og:site_name") ?: ""

            // Strategy 1: og:video meta tag
            val ogVideoUrl = extractMetaContent(html, "og:video")
            if (!ogVideoUrl.isNullOrBlank()) {
                Log.d(TAG, "Generic: found og:video")
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 2: og:video:url
            val ogVideoUrl2 = extractMetaContent(html, "og:video:url")
            if (!ogVideoUrl2.isNullOrBlank()) {
                Log.d(TAG, "Generic: found og:video:url")
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = ogVideoUrl2, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 3: twitter:player:stream
            val twitterStream = extractMetaContent(html, "twitter:player:stream")
            if (!twitterStream.isNullOrBlank()) {
                Log.d(TAG, "Generic: found twitter:player:stream")
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = twitterStream, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 4: JSON-LD VideoObject
            val jsonLdPattern = Pattern.compile(
                """<script[^>]+type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
                Pattern.CASE_INSENSITIVE
            )
            val jsonLdMatcher = jsonLdPattern.matcher(html)
            val rootMapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = moshi.adapter<Map<String, Any?>>(rootMapType)
            while (jsonLdMatcher.find()) {
                try {
                    val data = adapter.fromJson(jsonLdMatcher.group(1) ?: continue) ?: continue
                    val items = if (data["@type"] == "VideoObject") listOf(data)
                    else (data["@graph"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                        ?.filter { it["@type"] == "VideoObject" } ?: emptyList()
                    for (item in items) {
                        val videoUrl = item["contentUrl"]?.toString()?.ifBlank { null }
                            ?: (item["embedUrl"] as? Map<*, *>)?.get("url")?.toString()?.ifBlank { null }
                        if (videoUrl != null) {
                            Log.d(TAG, "Generic: found JSON-LD VideoObject")
                            return TikTokVideoData(
                                id = "", title = item["name"]?.toString() ?: title, author = author, authorId = "",
                                thumbnail = item["thumbnailUrl"]?.toString() ?: thumbnail, duration = 0L,
                                videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                            )
                        }
                    }
                } catch (_: Exception) { }
            }

            // Strategy 5: <video> tag
            val videoTagPattern = Pattern.compile("""<video[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val videoTagMatcher = videoTagPattern.matcher(html)
            if (videoTagMatcher.find()) {
                val videoUrl = videoTagMatcher.group(1)
                if (!videoUrl.isNullOrBlank()) {
                    Log.d(TAG, "Generic: found <video> tag")
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 6: <source> tag inside <video>
            val sourceTagPattern = Pattern.compile("""<source[^>]+src=["']([^"']+)["'][^>]*type=["']video/[^"']*["']""", Pattern.CASE_INSENSITIVE)
            val sourceTagMatcher = sourceTagPattern.matcher(html)
            if (sourceTagMatcher.find()) {
                val videoUrl = sourceTagMatcher.group(1)
                if (!videoUrl.isNullOrBlank()) {
                    Log.d(TAG, "Generic: found <source> tag")
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 7: video/mp4 URL patterns in HTML
            val videoUrlPattern = Pattern.compile(
                """https?://[^"'\s<>]+\.mp4(?:\?[^"'\s<>]*)?""",
                Pattern.CASE_INSENSITIVE
            )
            val videoUrlMatcher = videoUrlPattern.matcher(html)
            val videoUrls = mutableSetOf<String>()
            while (videoUrlMatcher.find()) {
                val videoUrl = videoUrlMatcher.group()?.replace("\\u002F", "/")
                if (!videoUrl.isNullOrBlank()) {
                    videoUrls.add(videoUrl)
                }
            }
            if (videoUrls.isNotEmpty()) {
                val bestUrl = videoUrls.firstOrNull { it.contains("mp4") }
                    ?: videoUrls.first()
                Log.d(TAG, "Generic: found ${videoUrls.size} MP4 URLs")
                return TikTokVideoData(
                    id = "", title = title, author = author, authorId = "",
                    thumbnail = thumbnail, duration = 0L,
                    videoUrl = bestUrl, videoUrlNoWatermark = null, audioUrl = null
                )
            }

            // Strategy 8: video_url in JSON/script data
            val videoUrlJsonPattern = Pattern.compile(""""(?:video_url|videoUrl|download_url|downloadUrl|media_url|mediaUrl)"\s*:\s*"([^"]+)"""")
            val videoUrlJsonMatcher = videoUrlJsonPattern.matcher(html)
            if (videoUrlJsonMatcher.find()) {
                val videoUrl = videoUrlJsonMatcher.group(1)?.replace("\\u002F", "/")?.replace("\\/", "/")
                if (!videoUrl.isNullOrBlank() && videoUrl.startsWith("http")) {
                    Log.d(TAG, "Generic: found video URL in JSON")
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 9: data-src or data-video-src attributes
            val dataSrcPattern = Pattern.compile("""data-(?:src|video-src|video-url)=["']([^"']+\.mp4[^"']*?)["']""", Pattern.CASE_INSENSITIVE)
            val dataSrcMatcher = dataSrcPattern.matcher(html)
            if (dataSrcMatcher.find()) {
                val videoUrl = dataSrcMatcher.group(1)
                if (!videoUrl.isNullOrBlank()) {
                    Log.d(TAG, "Generic: found data-src MP4")
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = videoUrl, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            // Strategy 10: iframe embed with video
            val iframePattern = Pattern.compile("""<iframe[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
            val iframeMatcher = iframePattern.matcher(html)
            while (iframeMatcher.find()) {
                val iframeSrc = iframeMatcher.group(1)
                if (iframeSrc.isNullOrBlank()) continue
                // Only try common video embed domains
                if (iframeSrc.contains("youtube.com/embed") ||
                    iframeSrc.contains("player.vimeo.com") ||
                    iframeSrc.contains("dailymotion.com/embed") ||
                    iframeSrc.contains("facebook.com/plugins/video") ||
                    iframeSrc.contains("twitch.tv/embed")) {
                    Log.d(TAG, "Generic: found video iframe embed")
                    return TikTokVideoData(
                        id = "", title = title, author = author, authorId = "",
                        thumbnail = thumbnail, duration = 0L,
                        videoUrl = iframeSrc, videoUrlNoWatermark = null, audioUrl = null
                    )
                }
            }

            Log.w(TAG, "Generic extraction: no video found on this website")
            return null
        } catch (e: Exception) {
            Log.w(TAG, "extractGeneric failed", e)
            return null
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
