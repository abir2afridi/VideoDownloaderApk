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
import com.example.data.download.InstagramCookieStore

class InstagramLoginActivity : Activity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, InstagramLoginActivity::class.java))
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
            text = "Login to Instagram — your session will be saved automatically"
            setTextColor(AndroidColor.WHITE)
            setBackgroundColor(AndroidColor.parseColor("#E1306C"))
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

                if (url != null && url.contains("instagram.com") && !url.contains("/accounts/login")) {
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

        webView.loadUrl("https://www.instagram.com/accounts/login/")
    }

    private fun extractCookiesAndFinish() {
        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie("instagram.com") ?: ""

        if (cookieString.isNotBlank()) {
            val sessionParts = mutableListOf<String>()
            val rawCookies = cookieString.split(";").map { it.trim() }

            for (cookie in rawCookies) {
                val name = cookie.substringBefore("=").trim()
                if (name in listOf("sessionid", "ds_user_id", "csrftoken", "mid", "ig_did", "rur", "shbid", "shbts", "ig_nrcb", "ps_n", "ps_l", "web_session_id", "iiv", "fbsr")) {
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
                InstagramCookieStore.setCookies(finalCookie)
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Instagram login saved!", android.widget.Toast.LENGTH_LONG).show()
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
