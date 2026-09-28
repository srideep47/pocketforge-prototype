package com.srideep.pocketforge.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddToHomeScreen
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.srideep.pocketforge.ui.theme.CodeColors

/**
 * The preview tab: full-bleed WebView pointed at the on-device dev server, topped by a
 * slim hardware-styled toolbar (dev server state, reload, open in browser, start/stop).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewPane(
    url: String?,
    serverRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToHomeScreen: () -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current

    Column(modifier = modifier.fillMaxSize()) {
        AddressBar(
            url = url,
            loadError = loadError,
            serverRunning = serverRunning,
            onReload = {
                loadError = null
                webView?.reload()
            },
            onOpenInBrowser = {
                if (url != null) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            },
            onToggleServer = if (serverRunning) onStopServer else onStartServer,
            onAddToHomeScreen = onAddToHomeScreen,
        )

        if (url == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Nothing to preview yet",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Start the on-device Node server to serve index.html over localhost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(onClick = onStartServer, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start dev server")
                }
            }
            return@Column
        }

        if (isInspection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Live WebView Preview · $url",
                    fontFamily = CodeColors.mono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            return@Column
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
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
    onOpenInBrowser: () -> Unit,
    onToggleServer: () -> Unit,
    onAddToHomeScreen: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val statusColor = when {
                    loadError != null -> MaterialTheme.colorScheme.error
                    serverRunning -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = when {
                        loadError != null -> "ERR"
                        serverRunning -> "LIVE"
                        else -> "STOPPED"
                    },
                    fontFamily = CodeColors.mono,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
                Text(
                    text = loadError ?: url ?: "127.0.0.1 (server stopped)",
                    fontFamily = CodeColors.mono,
                    fontSize = 11.5.sp,
                    color = if (loadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            IconButton(
                onClick = onReload,
                enabled = url != null,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reload",
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(
                onClick = onAddToHomeScreen,
                enabled = url != null,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.AddToHomeScreen,
                    contentDescription = "Add to home screen",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(
                onClick = onOpenInBrowser,
                enabled = url != null,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open in browser",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onToggleServer,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (serverRunning) "Stop server" else "Start server",
                    modifier = Modifier.size(20.dp),
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
