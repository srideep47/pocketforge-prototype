package com.srideep.pocketforge.apps

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Runs a saved app fullscreen, as if it were installed: opened from its home-screen icon, with
 * no PocketForge UI around it and no dev server or model needed.
 */
class AppPlayerActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_APP_ID)
        val index = id?.let { SavedApps.indexOf(this, it) }
        if (id == null || index == null || !index.isFile) {
            Toast.makeText(this, "This app was removed from PocketForge", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val name = SavedApps.nameOf(this, id)
        setTaskDescription(ActivityManager.TaskDescription(name))

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val origin = "https://$id.pocketforge.app/"
        val webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            // Apps like to-do lists keep their state in localStorage; the per-app origin gives
            // each one its own.
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                // Everything the app needs is in the page; nothing may navigate off-device.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !request.url.toString().startsWith(origin)
            }
        }
        setContentView(webView)
        webView.loadDataWithBaseURL(origin, index.readText(), "text/html", "utf-8", null)
    }

    companion object {
        const val EXTRA_APP_ID = "app_id"
    }
}
