package com.srideep.pocketforge.engine.mnn

/**
 * Thin 1:1 mapping of the native entry points in `llm_jni.cpp`.
 *
 * Every method here blocks the calling thread; scheduling is the caller's job
 * (see [MnnLlmEngine], which confines all of it to a single-threaded dispatcher).
 */
internal class MnnLlmBridge {

    /** Receives decoded text, one call per token flushed by MNN. */
    fun interface TokenCallback {
        fun onToken(token: String)
    }

    external fun nativeInitModel(configPath: String): Long

    external fun nativeGenerateStream(
        handle: Long,
        prompt: String,
        maxNewTokens: Int,
        callback: TokenCallback,
    ): Boolean

    external fun nativeStopGeneration(handle: Long)

    external fun nativeResetHistory(handle: Long)

    external fun nativeReleaseModel(handle: Long)

    companion object {
        @Volatile
        private var loaded = false

        fun ensureNativeLibraryLoaded() {
            if (loaded) return
            synchronized(this) {
                if (loaded) return
                System.loadLibrary("MNN")
                System.loadLibrary("pocketforgellm")
                loaded = true
            }
        }
    }
}
