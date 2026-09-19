package com.srideep.pocketforge.engine.mnn

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The `config.json` MNN reads when a model is created.
 *
 * Defaults are tuned for one job: a coding agent on a phone. Sampling is cold and
 * penalised so the model emits the same well-formed tool call twice rather than
 * improvising; weights are mmap'd and the KV cache is INT8 and disk-backed, because a
 * 32k window on a handset does not fit in RAM any other way.
 */
data class ModelConfig(
    /** Directory holding the exported MNN model (llm.mnn, tokenizer.txt, ...). */
    val modelDir: File,
    /**
     * Scratch directory for mmap'd weights and KV cache. Kept off the model directory on
     * purpose: models sit on the FUSE-backed external volume, which is a poor host for a
     * file the runtime maps and writes to.
     */
    val tmpDir: File = File(modelDir, "tmp"),

    val llmModel: String = "llm.mnn",
    val llmWeight: String = "llm.mnn.weight",
    val tokenizerFile: String = "tokenizer.txt",

    val backendType: String = "cpu",
    val threadNum: Int = 6,
    val precision: String = "low",
    val memory: String = "low",
    val power: String = "high",

    /** Total context window, prompt plus generation. */
    val maxAllTokens: Int = 32_768,
    /** Ceiling on a single reply, so one runaway turn cannot eat the whole window. */
    val maxNewTokens: Int = 4_096,

    val useMmap: Boolean = true,
    val useCachedMmap: Boolean = true,
    val reuseKv: Boolean = true,
    /** 10 = INT8 quantised K and V cache. MNN downgrades it to 9 when reuse_kv is on. */
    val attentionMode: Int = 10,
    /** Spills the KV cache to [tmpDir]; at 32k on a phone this is not optional. */
    val kvcacheMmap: Boolean = true,

    // Sampling. Low temperature with an active repetition penalty: the agent's output is
    // JSON tool calls and source code, where creativity is a defect.
    val temperature: Float = 0.25f,
    val topP: Float = 0.9f,
    val topK: Int = 20,
    val minP: Float = 0.05f,
    val penalty: Float = 1.05f,
    val penaltyWindow: Int = 256,

    /**
     * Qwen3.5 emits a `<think>` block before answering when this is on. For a tool-calling
     * loop on ~10 tok/s hardware that is a large latency tax for little accuracy, so it is
     * off by default.
     */
    val enableThinking: Boolean = false,
) {

    fun toJson(): JSONObject = JSONObject().apply {
        // MNN resolves these by concatenating its base_dir (the config file's own
        // directory) with the value, so they must stay bare file names.
        put("llm_model", llmModel)
        put("llm_weight", llmWeight)
        put("tokenizer_file", tokenizerFile)

        put("backend_type", backendType)
        put("thread_num", threadNum)
        put("precision", precision)
        put("memory", memory)
        put("power", power)

        put("max_all_tokens", maxAllTokens)
        put("max_new_tokens", maxNewTokens)

        put("use_mmap", useMmap)
        put("use_cached_mmap", useCachedMmap)
        put("reuse_kv", reuseKv)
        put("attention_mode", attentionMode)
        put("kvcache_mmap", kvcacheMmap)
        // tmp_path is the one path MNN takes verbatim rather than joining to base_dir.
        put("tmp_path", tmpDir.absolutePath)

        put("sampler_type", "mixed")
        put("mixed_samplers", JSONArray(listOf("penalty", "topK", "topP", "min_p", "temperature")))
        put("temperature", temperature.toDouble())
        put("topP", topP.toDouble())
        put("topK", topK)
        put("min_p", minP.toDouble())
        put("penalty", penalty.toDouble())
        put("penalty_window", penaltyWindow)

        put(
            "jinja",
            JSONObject().put("context", JSONObject().put("enable_thinking", enableThinking)),
        )
    }

    /**
     * Materialises the config next to the model and returns the file MNN should load.
     * The scratch directory is created here too, since MNN expects it to exist.
     */
    fun writeTo(target: File = File(modelDir, "pocketforge_config.json")): File {
        tmpDir.mkdirs()
        target.writeText(toJson().toString(2))
        return target
    }
}
