package com.srideep.pocketforge.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.srideep.pocketforge.ui.theme.CodeColors

/**
 * The preview tab: a WebView pointed at the on-device dev server.
 *
 * The page comes from 127.0.0.1 and was written by the agent on this phone, so JavaScript
 * and DOM storage are on and nothing else is. Load failures surface in the address bar
 * rather than as the WebView's own error page, which is unreadable on a phone.
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
        AddressBar(
            url = url,
            loadError = loadError,
            serverRunning = serverRunning,
            onReload = {
                loadError = null
                webView?.reload()
            },
            onToggleServer = if (serverRunning) onStopServer else onStartServer,
        )

        if (url == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Nothing to preview yet",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Start the server to serve this project over localhost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(onClick = onStartServer, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("  Start dev server")
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
                                loadError = error?.description?.toString() ?: "could not load"
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

@Composable
private fun AddressBar(
    url: String?,
    loadError: String?,
    serverRunning: Boolean,
    onReload: () -> Unit,
    onToggleServer: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                loadError != null -> MaterialTheme.colorScheme.error
                                serverRunning -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                )
                Text(
                    text = "  " + (loadError ?: url ?: "dev server not running"),
                    fontFamily = CodeColors.mono,
                    fontSize = 12.sp,
                    color = if (loadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onReload, enabled = url != null) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reload",
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(onClick = onToggleServer) {
                Icon(
                    imageVector = if (serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (serverRunning) "Stop server" else "Start server",
                    modifier = Modifier.size(19.dp),
                    tint = if (serverRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
