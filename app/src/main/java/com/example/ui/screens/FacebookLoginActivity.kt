package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import com.example.data.download.FacebookCookieStore

class FacebookLoginActivity : Activity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, FacebookLoginActivity::class.java))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.WHITE)
        }

        val statusBar = TextView(this).apply {
            text = "Login to Facebook — your session will be saved automatically"
            setTextColor(AndroidColor.WHITE)
            setBackgroundColor(AndroidColor.parseColor("#1877F2"))
            setPadding(32, 48, 32, 48)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        layout.addView(statusBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        layout.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                if (url != null && url.contains("facebook.com") && !url.contains("/login") && !url.contains("/checkpoint")) {
                    extractCookiesAndFinish()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        layout.addView(webView)
        setContentView(layout)

        webView.loadUrl("https://www.facebook.com/login/")
    }

    private fun extractCookiesAndFinish() {
        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie("facebook.com") ?: ""

        if (cookieString.isNotBlank()) {
            val sessionParts = mutableListOf<String>()
            val rawCookies = cookieString.split(";").map { it.trim() }

            for (cookie in rawCookies) {
                val name = cookie.substringBefore("=").trim()
                if (name in listOf("c_user", "xs", "sb", "datr", "fr", "spin", "locale", "pl", " Presence", "wd", "dpr", "ct", "fr")) {
                    sessionParts.add(cookie.trim())
                }
            }

            if (sessionParts.isEmpty()) {
                for (cookie in rawCookies) {
                    sessionParts.add(cookie.trim())
                }
            }

            val finalCookie = sessionParts.joinToString("; ")
            if (finalCookie.isNotBlank()) {
                FacebookCookieStore.setCookies(finalCookie)
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Facebook login saved!", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        finish()
    }

    @Deprecated("Use OnBackPressedCallback instead")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed()
        finish()
    }
}
