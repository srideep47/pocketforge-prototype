package com.srideep.pocketforge.vision

import com.srideep.pocketforge.engine.mnn.ChatTurn
import com.srideep.pocketforge.engine.mnn.MnnLlmEngine
import com.srideep.pocketforge.engine.mnn.ModelConfig
import java.io.File

/** What the vision model saw, and what reading it cost. */
data class SketchReading(val description: String, val visionMicros: Long, val elapsedMs: Long)

/**
 * A small vision model that turns a photo of a sketch into text for a coder that cannot see.
 *
 * It runs on its own engine next to the coder. Qwen 3.5 0.8B was chosen over Apple's
 * FastVLM 0.5B after testing both on the same hand-drawn sketches: FastVLM read the title
 * but reported a 4x4 calculator keypad as "10 rows and 10 columns", while Qwen transcribed
 * every row exactly.
 *
 * The questions are short and asked one at a time in a single conversation. At 0.8B, one long
 * instruction ("describe everything, in this format") produced rambling and invented layouts;
 * two plain questions each got a precise answer. Sharing the conversation means the image is
 * encoded once and the second question reuses its KV cache.
 */
class VisionSidecar {

    private val engine = MnnLlmEngine()
    private var loadedDir: File? = null

    val isLoaded: Boolean get() = engine.isLoaded && loadedDir != null

    suspend fun ensureLoaded(config: ModelConfig): Boolean {
        if (isLoaded && loadedDir == config.modelDir) return true
        val ok = engine.load(config)
        loadedDir = if (ok) config.modelDir else null
        return ok
    }

    /** @param imageTag MNN's inline image reference for the photo. */
    suspend fun read(imageTag: String): SketchReading {
        val started = System.currentTimeMillis()
        engine.resetHistory()
        val visionBefore = engine.lastStats().visionMicros
        val conversation = mutableListOf<ChatTurn>()
        val answers = QUESTIONS.mapIndexed { index, question ->
            conversation += ChatTurn("user", if (index == 0) imageTag + question else question)
            val answer = StringBuilder()
            engine.generateChat(conversation, MAX_TOKENS_PER_ANSWER).collect { answer.append(it) }
            val clean = answer.toString().replace(THINK, "").trim()
            conversation += ChatTurn("assistant", clean)
            clean
        }
        val description = buildString {
            append("Screen: ").append(answers[0]).append('\n')
            append("Drawn, top to bottom:\n").append(answers[1])
        }
        return SketchReading(
            description = description,
            visionMicros = (engine.lastStats().visionMicros - visionBefore).coerceAtLeast(0L),
            elapsedMs = System.currentTimeMillis() - started,
        )
    }

    suspend fun release() {
        engine.release()
        loadedDir = null
    }

    fun shutdown() = engine.shutdown()

    companion object {
        private val QUESTIONS = listOf(
            "What kind of app screen is this sketch? Answer in one short sentence.",
            // Worded as tested: it also lists buttons and writes keypad grids row by row. Asking
            // for buttons explicitly (a third question) only added noise.
            "What text, headings and input boxes are drawn, from top to bottom? Give each with its " +
                "exact words, one per line.",
        )
        private const val MAX_TOKENS_PER_ANSWER = 160
        private val THINK = Regex("(?s)<think>.*?</think>")

        /**
         * Sampling for transcription rather than code: low temperature to stick to what is
         * drawn, and a mild repetition penalty because greedy 0.8B decoding on images loops.
         */
        fun configFor(modelDir: File, tmpDir: File, tokenizerFile: String) = ModelConfig(
            modelDir = modelDir,
            tmpDir = tmpDir,
            tokenizerFile = tokenizerFile,
            threadNum = 4,
            temperature = 0.3f,
            topK = 20,
            topP = 0.9f,
            penalty = 1.1f,
            maxNewTokens = MAX_TOKENS_PER_ANSWER,
            maxAllTokens = 2_048,
        )
    }
}
