package com.srideep.pocketforge.runtime.node

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI needs to know about the dev server. */
data class DevServerState(
    val running: Boolean = false,
    val url: String? = null,
    val projectPath: String? = null,
    val error: String? = null,
)

/**
 * UI-process handle on the dev server living in `:node`.
 *
 * Binding is lazy: the `:node` process is only spawned the first time a server is asked
 * for, and it is torn down completely on [stop].
 */
class DevServerClient(private val context: Context) {

    private val _state = MutableStateFlow(DevServerState())
    val state: StateFlow<DevServerState> = _state.asStateFlow()

    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what != NodeMessages.MSG_STATUS) return@Handler false
            val data = message.data ?: Bundle()
            _state.value = _state.value.copy(
                running = data.getBoolean(NodeMessages.KEY_RUNNING),
                url = data.getString(NodeMessages.KEY_URL),
                error = data.getString(NodeMessages.KEY_ERROR),
            )
            true
        },
    )

    private var outgoing: Messenger? = null
    private var connection: ServiceConnection? = null

    /**
     * Starts (or re-points) the dev server at [projectDir] and returns the URL to load.
     * Switching projects restarts `:node`, because one process hosts one Node instance.
     */
    suspend fun start(projectDir: File, port: Int = NodeMessages.DEFAULT_PORT): DevServerState {
        val path = projectDir.absolutePath
        if (_state.value.running && _state.value.projectPath != path) {
            stop()
        }
        projectDir.mkdirs()

        val service = bind() ?: return fail("could not bind the node process")
        val request = Message.obtain(null, NodeMessages.MSG_START).apply {
            data = Bundle().apply {
                putString(NodeMessages.KEY_PROJECT_PATH, path)
                putInt(NodeMessages.KEY_PORT, port)
            }
            replyTo = incoming
        }
        return try {
            service.send(request)
            // MSG_START only asks :node to boot Node; the server listens a few seconds later.
            // Callers load the URL straight away, and a load that races the boot fails with a
            // connection error, so report running only once the port accepts a connection.
            if (!awaitListening(port)) return fail("dev server did not start listening on port $port")
            _state.value = _state.value.copy(
                running = true,
                url = "http://localhost:$port",
                projectPath = path,
                error = null,
            )
            _state.value
        } catch (e: Exception) {
            fail(e.message ?: "node process is not reachable")
        }
    }

    private suspend fun awaitListening(port: Int): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
            while (!acceptsConnections(port)) delay(150)
            true
        } ?: false
    }

    private fun acceptsConnections(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 250) }
    }.isSuccess

    /** Stops the server and lets the `:node` process exit. */
    fun stop() {
        outgoing?.let { service ->
            runCatching {
                service.send(
                    Message.obtain(null, NodeMessages.MSG_STOP).apply { replyTo = incoming },
                )
            }
        }
        unbind()
        _state.value = DevServerState()
    }

    private suspend fun bind(): Messenger? {
        outgoing?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val messenger = binder?.let(::Messenger)
                    outgoing = messenger
                    if (continuation.isActive) continuation.resume(messenger)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // The :node process died (stop(), a crash, or the OS reclaiming it).
                    outgoing = null
                    _state.value = DevServerState()
                }
            }
            connection = serviceConnection
            val bound = context.bindService(
                Intent(context, NodeService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
            if (!bound && continuation.isActive) {
                connection = null
                continuation.resume(null)
            }
            continuation.invokeOnCancellation { unbind() }
        }
    }

    private fun unbind() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        outgoing = null
    }

    private fun fail(reason: String): DevServerState {
        _state.value = DevServerState(error = reason)
        return _state.value
    }

    private companion object {
        /** Cold-starting Node on a phone takes a few seconds; well past that, it is not coming. */
        const val LISTEN_TIMEOUT_MS = 15_000L
    }
}
