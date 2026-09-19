package com.srideep.pocketforge.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The preview tab: a WebView pointed at the on-device dev server.
 *
 * The page comes from 127.0.0.1 and is written by the agent on this phone, so JavaScript
 * and DOM storage are on; nothing else is. Load failures surface in the bar rather than
 * as the WebView's own error page, which is unreadable on a phone.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewPane(
    url: String?,
    serverRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = loadError ?: url ?: "dev server not running",
                style = MaterialTheme.typography.labelMedium,
                color = if (loadError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = {
                    loadError = null
                    webView?.reload()
                },
                enabled = url != null,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }

            IconButton(onClick = if (serverRunning) onStopServer else onStartServer) {
                Icon(
                    imageVector = if (serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (serverRunning) "Stop server" else "Start server",
                )
            }
        }

        if (url == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing to preview yet", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onStartServer, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Start dev server")
                }
            }
            return@Column
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                loadError = "could not load: " + error?.description
                            }
                        }
                    }
                    webView = this
                    loadUrl(url)
                }
            },
            update = { view ->
                if (view.url != url) {
                    loadError = null
                    view.loadUrl(url)
                }
            },
        )
    }
}
