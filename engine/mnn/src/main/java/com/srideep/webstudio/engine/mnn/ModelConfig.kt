package com.srideep.webstudio.engine.mnn

import java.io.File
import org.json.JSONObject

/**
 * The `config.json` MNN reads when a model is created.
 *
 * Defaults are tuned for phones: mmap'd weights keep resident memory close to the
 * KV cache size, KV reuse keeps multi-turn prefill cheap, and attention mode 10
 * selects the INT8-quantised QKV cache.
 */
data class ModelConfig(
    /** Directory holding the exported MNN model (llm.mnn, tokenizer.txt, ...). */
    val modelDir: File,
    val llmModel: String = "llm.mnn",
    val llmWeight: String = "llm.mnn.weight",
    val tokenizerFile: String = "tokenizer.txt",
    val backendType: String = "cpu",
    val threadNum: Int = 4,
    val precision: String = "low",
    val memory: String = "low",
    val useMmap: Boolean = true,
    val reuseKv: Boolean = true,
    /** 10 = INT8 quantised K and V cache. */
    val attentionMode: Int = 10,
    val maxNewTokens: Int = 2048,
    val temperature: Float = 0.6f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("llm_model", File(modelDir, llmModel).absolutePath)
        put("llm_weight", File(modelDir, llmWeight).absolutePath)
        put("tokenizer_file", File(modelDir, tokenizerFile).absolutePath)
        put("backend_type", backendType)
        put("thread_num", threadNum)
        put("precision", precision)
        put("memory", memory)
        put("use_mmap", useMmap)
        put("tmp_path", File(modelDir, "tmp").absolutePath)
        put("reuse_kv", reuseKv)
        put("attention_mode", attentionMode)
        put("max_new_tokens", maxNewTokens)
        put("temperature", temperature.toDouble())
        put("topP", topP.toDouble())
        put("topK", topK)
    }

    /**
     * Materialises the config next to the model and returns the file MNN should load.
     * The scratch directory mmap uses is created here too, since MNN expects it to exist.
     */
    fun writeTo(target: File = File(modelDir, "webstudio_config.json")): File {
        File(modelDir, "tmp").mkdirs()
        target.writeText(toJson().toString(2))
        return target
    }
}
