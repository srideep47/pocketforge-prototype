package com.srideep.pocketforge.preview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.srideep.pocketforge.vision.PreparedImage
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes a phone-sized screenshot of a page so the vision model can check what the coder wrote.
 *
 * It renders in a WebView of its own that is never attached to a window, rather than grabbing
 * the preview tab's: when a run ends the user is usually on the chat tab, and a WebView that
 * has not been on screen has drawn nothing. A detached WebView still loads and lays out a page
 * once it has been measured, and draws in software into a Bitmap's canvas.
 */
object PageSnapshot {

    /**
     * A rendered page: its screenshot (null if nothing drew) and the script errors it logged.
     * A page can look perfect and still throw on load; the screenshot alone would pass it.
     */
    class Render(val image: PreparedImage?, val scriptErrors: List<String>)

    /** CSS viewport of a common phone, so the page takes the layout users will actually see. */
    private const val VIEWPORT_WIDTH_DP = 412
    private const val VIEWPORT_HEIGHT_DP = 915

    /**
     * Long side of the saved JPEG. Photos go to the encoder at 672, but a whole phone screen
     * has smaller text than a sketch; 896 (a multiple of 28 and 32) keeps body text legible
     * for about the same ~350 image tokens, since a screen is narrower than a photo.
     */
    private const val LONG_SIDE = 896

    private const val LOAD_TIMEOUT_MS = 10_000L

    /** onPageFinished fires before first-frame scripts, transitions and layout settle. */
    private const val SETTLE_MS = 700L

    private const val FILE_PREFIX = "render_"
    private const val KEEP_RENDERS = 10
    private const val MAX_ERRORS = 3
    private const val TAG = "PageSnapshot"

    /** Renders [url]; the screenshot is saved as a JPEG in [outDir]. */
    suspend fun capture(context: Context, url: String, outDir: File): Render {
        val errors = mutableListOf<String>()
        val bitmap = withContext(Dispatchers.Main) { render(context, url, errors) }
        val image = bitmap?.let { withContext(Dispatchers.IO) { save(it, outDir) } }
        return Render(image, errors.distinct().take(MAX_ERRORS))
    }

    /** WebViews may only be touched on the main thread. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun render(context: Context, url: String, errors: MutableList<String>): Bitmap? {
        val density = context.resources.displayMetrics.density
        val width = (VIEWPORT_WIDTH_DP * density).roundToInt()
        val height = (VIEWPORT_HEIGHT_DP * density).roundToInt()
        val webView = WebView(context.applicationContext)
        try {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            // The preview tab's WebView shares the HTTP cache; the page was just rewritten.
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            // Rasterise the viewport even though the view is not on screen.
            webView.settings.offscreenPreRaster = true
            // Laid out before loading, so the page sees a phone viewport from its first script.
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, width, height)
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    // Uncaught exceptions arrive here too, as "Uncaught TypeError: ...".
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        errors += message.message().take(160) + " (line " + message.lineNumber() + ")"
                    }
                    return true
                }
            }

            val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true && continuation.isActive) {
                                continuation.resume(false)
                            }
                        }
                    }
                    webView.loadUrl(url)
                }
            } ?: false
            if (!loaded) {
                Log.w(TAG, "page did not load: " + url)
                return null
            }
            delay(SETTLE_MS)

            val scale = LONG_SIDE.toFloat() / height
            val bitmap = Bitmap.createBitmap(
                (width * scale).roundToInt(),
                LONG_SIDE,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            if (isUniform(bitmap)) {
                // What a WebView that failed to draw produces. A page that is really one flat
                // colour is rare, and a false "the page is empty" would send the coder off to
                // rewrite a page that is fine, so neither is sent to be checked.
                Log.w(TAG, "snapshot came out blank")
                bitmap.recycle()
                return null
            }
            return bitmap
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun isUniform(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val first = pixels[0]
        return pixels.all { it == first }
    }

    private fun save(bitmap: Bitmap, outDir: File): PreparedImage {
        outDir.mkdirs()
        // Each check's screenshot is shown on its step in the chat, so recent ones are kept;
        // older ones go. Photos in the same folder are never touched.
        outDir.listFiles { file -> file.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_RENDERS - 1)
            ?.forEach { it.delete() }
        val file = File(outDir, FILE_PREFIX + System.currentTimeMillis() + ".jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val image = PreparedImage(file, bitmap.width, bitmap.height)
        bitmap.recycle()
        return image
    }
}
