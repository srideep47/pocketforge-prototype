package com.srideep.webstudio.runtime.node

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Owns the embedded Node 18 runtime for the process it is called in.
 *
 * `node::Start` runs the event loop on the calling thread and only returns when Node
 * exits, so [start] hands it a dedicated thread. Exactly one Node instance can exist
 * per process, which is why this only ever runs inside `:node` (see [NodeService]).
 */
object NodeRuntime {

    private const val TAG = "NodeRuntime"
    private const val ASSET_DIR = "nodejs-project"

    @Volatile
    private var started = false

    @JvmStatic
    private external fun nativeStart(args: Array<String>): Int

    @JvmStatic
    private external fun nativeRedirectOutput()

    /**
     * Copies the bundled JS into app storage (Node cannot require() out of the APK) and
     * boots the dev server for [projectDir] on [port]. Returns immediately; the runtime
     * keeps running on its own thread until the process dies.
     */
    fun start(context: Context, projectDir: File, port: Int) {
        if (started) {
            Log.w(TAG, "node already running in this process")
            return
        }
        started = true

        System.loadLibrary("node")
        System.loadLibrary("nodehost")
        nativeRedirectOutput()

        val scriptRoot = syncAssets(context)
        val entry = File(scriptRoot, "server.js")

        Thread({
            val exitCode = nativeStart(
                arrayOf(
                    "node",
                    entry.absolutePath,
                    projectDir.absolutePath,
                    port.toString(),
                ),
            )
            Log.i(TAG, "node exited with $exitCode")
        }, "node-main").apply { isDaemon = false }.start()
    }

    /**
     * Mirrors `assets/nodejs-project` into files/nodejs-project. Assets change only with
     * the APK, so the copy is redone whenever the app version marker differs.
     */
    private fun syncAssets(context: Context): File {
        val target = File(context.filesDir, ASSET_DIR)
        val marker = File(target, ".version")
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
        } catch (e: Exception) {
            Log.w(TAG, "could not read package version", e)
            "dev"
        }
        if (marker.isFile && marker.readText() == version) {
            return target
        }

        target.deleteRecursively()
        target.mkdirs()
        copyAssetDir(context, ASSET_DIR, target)
        marker.writeText(version)
        return target
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            // A leaf: assets.list() returns nothing for files.
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use(input::copyTo)
            }
            return
        }
        target.mkdirs()
        children.forEach { child ->
            copyAssetDir(context, "$assetPath/$child", File(target, child))
        }
    }
}
