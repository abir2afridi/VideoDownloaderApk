package com.example.data.download

import android.util.Log
import okhttp3.Request

object VideoExtractor {
    fun extract(url: String): Result<TikTokVideoData> {
        return try {
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

            if (isInstagram) {
                val ytDlpResult = YtDlpExtractor.extract(url)
                if (ytDlpResult != null) return Result.success(ytDlpResult)
                Log.w(EXTRACTOR_TAG, "yt-dlp failed for Instagram, trying custom extractor")
                val result = extractInstagram(url)
                return if (result != null) Result.success(result)
                else Result.failure(Exception("Could not extract Instagram video."))
            }

            if (isFacebook) {
                val ytDlpResult = YtDlpExtractor.extract(url)
                if (ytDlpResult != null) return Result.success(ytDlpResult)
                Log.w(EXTRACTOR_TAG, "yt-dlp failed for Facebook, trying custom extractor")
                val result = extractFacebook(url)
                if (result != null) return Result.success(result)
                val genericFallback = extractGeneric(url)
                if (genericFallback != null) {
                    Log.d(EXTRACTOR_TAG, "Success via generic fallback for Facebook: ${genericFallback.title}")
                    return Result.success(genericFallback)
                }
                return Result.failure(Exception("Could not extract Facebook video. The video may be private or the link is invalid."))
            }

            if (isTwitter) {
                val result = extractTwitter(url)
                if (result != null) return Result.success(result)
                return Result.failure(Exception("Could not extract Twitter/X video. The tweet may not contain a video."))
            }

            if (isReddit) {
                val result = extractReddit(url)
                if (result != null) return Result.success(result)
                return Result.failure(Exception("Could not extract Reddit video. The post may not contain a video."))
            }

            if (isPinterest) {
                val result = extractPinterest(url)
                if (result != null) return Result.success(result)
                Log.w(EXTRACTOR_TAG, "Pinterest dedicated extraction failed, falling through to generic")
            }

            if (isSoundcloud) {
                val result = extractSoundCloud(url)
                if (result != null) return Result.success(result)
                return Result.failure(Exception("Could not extract SoundCloud audio. The track may be private."))
            }

            if (isVimeo) {
                val result = extractVimeo(url)
                if (result != null) return Result.success(result)
                return Result.failure(Exception("Could not extract Vimeo video."))
            }

            if (isTwitch) {
                val result = extractTwitch(url)
                if (result != null) return Result.success(result)
                return Result.failure(Exception("Could not extract Twitch clip."))
            }

            if (isDailymotion) {
                val result = extractDailymotion(url)
                if (result != null) return Result.success(result)
                return Result.failure(Exception("Could not extract Dailymotion video."))
            }

            if (isTumblr) {
                val result = extractTumblr(url)
                if (result != null) return Result.success(result)
                return Result.failure(Exception("Could not extract Tumblr video."))
            }

            val genericResult = extractGeneric(url)
            if (genericResult != null) {
                Log.d(EXTRACTOR_TAG, "Success via generic extraction: ${genericResult.title}")
                return Result.success(genericResult)
            }

            if (isPinterest) {
                return Result.failure(Exception("Could not extract Pinterest video. Pinterest may require a logged-in session."))
            }

            if (isTiktok) {
                TikTokCookieStore.seed()
                val tikResult = extractTikTok(url)
                if (tikResult != null) {
                    Log.d(EXTRACTOR_TAG, "Success via TikTok extraction: ${tikResult.title}")
                    return Result.success(tikResult)
                }
                return Result.failure(Exception("Could not extract TikTok video. All extraction strategies failed."))
            }

            return Result.failure(Exception("Could not extract video from the provided URL."))
        } catch (e: Throwable) {
            Log.e(EXTRACTOR_TAG, "Video extraction failed", e)
            return Result.failure(e)
        }
    }
}
