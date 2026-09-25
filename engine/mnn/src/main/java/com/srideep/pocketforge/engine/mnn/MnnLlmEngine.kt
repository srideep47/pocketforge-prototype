package com.srideep.pocketforge.engine.mnn

import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Throughput for the turn that just finished, straight out of MNN's own counters. */
data class GenerationStats(
    val promptTokens: Int,
    val generatedTokens: Int,
    val prefillMicros: Long,
    val decodeMicros: Long,
    /** Vision-encoder time since the last [MnnLlmEngine.resetHistory]; cumulative, not per turn. */
    val visionMicros: Long = 0L,
) {
    val decodeTokensPerSecond: Double
        get() = if (decodeMicros <= 0L) 0.0 else generatedTokens * 1_000_000.0 / decodeMicros

    val prefillTokensPerSecond: Double
        get() = if (prefillMicros <= 0L) 0.0 else promptTokens * 1_000_000.0 / prefillMicros
}

/** One message passed through MNN's native chat template. */
data class ChatTurn(val role: String, val content: String)

/**
 * On-device LLM inference over MNN.
 *
 * The native runtime is not thread safe, so every native call is confined to a single
 * worker thread. [generate] returns a cold [Flow] that runs one generation per collection
 * and cancels the native decode loop when the collector goes away.
 */
class MnnLlmEngine {

    private val bridge = MnnLlmBridge()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mnn-llm").apply { priority = Thread.MAX_PRIORITY }
    }
    private val dispatcher: CoroutineDispatcher = worker.asCoroutineDispatcher()

    @Volatile
    private var handle: Long = 0L

    val isLoaded: Boolean get() = handle != 0L

    /** Loads a model directory. Returns false if MNN could not create or load the model. */
    suspend fun load(config: ModelConfig): Boolean = withContext(dispatcher) {
        require(config.modelDir.isDirectory) { "model dir not found: ${config.modelDir}" }
        releaseBlocking()
        MnnLlmBridge.ensureNativeLibraryLoaded()
        val configFile: File = config.writeTo()
        handle = bridge.nativeInitModel(configFile.absolutePath)
        handle != 0L
    }

    /**
     * Streams the reply to [prompt]. The prompt is passed through verbatim: chat and tool
     * framing is the caller's concern, the model's own chat template is applied by MNN.
     */
    fun generate(prompt: String, maxNewTokens: Int = -1): Flow<String> = callbackFlow {
        val current = handle
        check(current != 0L) { "no model loaded" }

        val callback = MnnLlmBridge.TokenCallback { token -> trySend(token) }

        // The native call blocks its thread for the whole decode, so it gets the worker
        // thread to itself while this coroutine stays free to observe cancellation.
        launch(dispatcher) {
            try {
                bridge.nativeGenerateStream(current, prompt, maxNewTokens, callback)
            } finally {
                close()
            }
        }

        // Cancelling the collector flips the native stop flag, which ends the decode loop
        // at its next token and lets the worker thread return.
        awaitClose { bridge.nativeStopGeneration(current) }
    }.buffer(Channel.UNLIMITED)

    /**
     * Streams a reply from a complete conversation. Agent tool rounds must use this path: the
     * native single-prompt overload has no knowledge of earlier assistant calls or tool results.
     */
    fun generateChat(messages: List<ChatTurn>, maxNewTokens: Int = -1): Flow<String> = callbackFlow {
        val current = handle
        check(current != 0L) { "no model loaded" }
        require(messages.isNotEmpty()) { "conversation is empty" }

        val callback = MnnLlmBridge.TokenCallback { token -> trySend(token) }
        val roles = messages.map { it.role }.toTypedArray()
        val contents = messages.map { it.content }.toTypedArray()

        launch(dispatcher) {
            try {
                bridge.nativeGenerateChatStream(current, roles, contents, maxNewTokens, callback)
            } finally {
                close()
            }
        }
        awaitClose { bridge.nativeStopGeneration(current) }
    }.buffer(Channel.UNLIMITED)

    /** Counters for the most recent turn; zeroes before anything has been generated. */
    fun lastStats(): GenerationStats {
        val current = handle
        if (current == 0L) return GenerationStats(0, 0, 0L, 0L)
        val raw = bridge.nativeLastStats(current)
        if (raw.size < 4) return GenerationStats(0, 0, 0L, 0L)
        return GenerationStats(raw[0].toInt(), raw[1].toInt(), raw[2], raw[3], raw.getOrElse(4) { 0L })
    }

    /** Asks the running decode loop to stop at its next token. */
    fun stop() {
        val current = handle
        if (current != 0L) bridge.nativeStopGeneration(current)
    }

    /** Drops KV cache and conversation history without unloading the weights. */
    suspend fun resetHistory() = withContext(dispatcher) {
        val current = handle
        if (current != 0L) bridge.nativeResetHistory(current)
    }

    suspend fun release() = withContext(dispatcher) { releaseBlocking() }

    private fun releaseBlocking() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            bridge.nativeReleaseModel(current)
        }
    }

    fun shutdown() {
        worker.shutdown()
    }
}
