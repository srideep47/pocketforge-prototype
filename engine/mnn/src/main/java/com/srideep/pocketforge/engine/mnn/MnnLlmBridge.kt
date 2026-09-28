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

    /**
     * Generates from an explicit role/content conversation. Unlike the single-string overload,
     * this lets MNN's Qwen chat template preserve the system prompt and prior tool rounds.
     */
    external fun nativeGenerateChatStream(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        maxNewTokens: Int,
        callback: TokenCallback,
    ): Boolean

    /** Like [nativeGenerateChatStream], with the reply's first part already written. */
    external fun nativeContinueChatStream(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        assistantPrefix: String,
        maxNewTokens: Int,
        callback: TokenCallback,
    ): Boolean

    /**
     * [promptTokens, generatedTokens, prefillMicros, decodeMicros] for the last turn, then
     * visionMicros accumulated since the last history reset.
     */
    external fun nativeLastStats(handle: Long): LongArray

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
