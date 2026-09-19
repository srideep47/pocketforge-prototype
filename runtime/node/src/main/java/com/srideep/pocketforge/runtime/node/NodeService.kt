package com.srideep.pocketforge.runtime.node

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import java.io.File

/**
 * Hosts the Node runtime in the `:node` process and answers start/stop requests from the
 * UI process over a [Messenger].
 *
 * Node cannot be torn down and restarted inside one process, so "stop" means killing this
 * process; the next bind gets a fresh one. That is cheap (the UI and the loaded model live
 * elsewhere) and is the reason the runtime is isolated in the first place.
 */
class NodeService : Service() {

    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger

    private var runningProject: String? = null
    private var runningPort: Int = NodeMessages.DEFAULT_PORT

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("node-service").apply { start() }
        messenger = Messenger(Handler(worker.looper, ::handleMessage))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        worker.quitSafely()
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        when (message.what) {
            NodeMessages.MSG_START -> handleStart(message)
            NodeMessages.MSG_STOP -> handleStop(message)
            else -> return false
        }
        return true
    }

    private fun handleStart(message: Message) {
        val data = message.data ?: Bundle()
        val projectPath = data.getString(NodeMessages.KEY_PROJECT_PATH)
        val port = data.getInt(NodeMessages.KEY_PORT, NodeMessages.DEFAULT_PORT)

        if (projectPath.isNullOrBlank()) {
            reply(message, error = "no project path supplied")
            return
        }

        val current = runningProject
        if (current != null) {
            if (current == projectPath) {
                reply(message)
            } else {
                reply(message, error = "dev server already serving $current; stop it first")
            }
            return
        }

        try {
            NodeRuntime.start(applicationContext, File(projectPath), port)
            runningProject = projectPath
            runningPort = port
            reply(message)
        } catch (e: Throwable) {
            Log.e(TAG, "failed to start node", e)
            reply(message, error = e.message ?: e::class.java.simpleName)
        }
    }

    private fun handleStop(message: Message) {
        reply(message, forceStopped = true)
        // Give the reply a moment to cross the binder before the process goes away.
        Handler(worker.looper).postDelayed({
            Log.i(TAG, "stopping :node process")
            stopSelf()
            Process.killProcess(Process.myPid())
        }, STOP_GRACE_MS)
    }

    private fun reply(request: Message, error: String? = null, forceStopped: Boolean = false) {
        val replyTo = request.replyTo ?: return
        val running = !forceStopped && error == null && runningProject != null
        val status = Message.obtain(null, NodeMessages.MSG_STATUS).apply {
            data = Bundle().apply {
                putBoolean(NodeMessages.KEY_RUNNING, running)
                if (running) putString(NodeMessages.KEY_URL, "http://localhost:$runningPort")
                if (error != null) putString(NodeMessages.KEY_ERROR, error)
            }
        }
        try {
            replyTo.send(status)
        } catch (e: Exception) {
            Log.w(TAG, "client went away before status could be delivered", e)
        }
    }

    private companion object {
        const val TAG = "NodeService"
        const val STOP_GRACE_MS = 150L
    }
}
