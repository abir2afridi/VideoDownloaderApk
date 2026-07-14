package com.example.data.download

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object TikTokCookieStore {
    private const val TAG = "TikTokCookieStore"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private var seeded = false

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.addAll(cookies)
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): MutableList<Cookie> {
            val host = url.host
            val result = mutableListOf<Cookie>()
            for ((savedHost, savedCookies) in cookieStore) {
                if (url.host.endsWith(savedHost) || savedHost.endsWith(url.host)) {
                    result.addAll(savedCookies)
                }
            }
            return result
        }
    }

    fun getCookiesForDomain(domain: String): List<Cookie> {
        val result = mutableListOf<Cookie>()
        for ((savedHost, savedCookies) in cookieStore) {
            if (domain.endsWith(savedHost) || savedHost.endsWith(domain)) {
                result.addAll(savedCookies)
            }
        }
        return result
    }

    fun seed() {
        if (seeded) return
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(cookieJar)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val request = Request.Builder().url("https://www.tiktok.com/").build()
            val response = client.newCall(request).execute()
            response.body?.close()
            seeded = true
            Log.d(TAG, "Cookies seeded, have ${getCookiesForDomain("tiktok.com").size} cookies")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to seed cookies", e)
        }
    }
}
