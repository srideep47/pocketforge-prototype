package com.srideep.pocketforge.engine.mnn

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The runtime config MNN reads when a model is created.
 *
 * These values are not guesses. They match a configuration A/B-tested on device against
 * Qwen3.5-2B on MNN 3.6.1, and several are counter-intuitive enough that changing one
 * "because it sounds faster" reliably breaks generation — the comments below record what
 * actually happened when each was set the other way.
 *
 * The settings merge onto the `config.json` the model ships with rather than replacing it:
 * that file carries per-export choices (vision metadata, per-modality backends) which are
 * silently lost if it is overwritten.
 */
data class ModelConfig(
    /** Directory holding the exported MNN model (llm.mnn, tokenizer, config.json, ...). */
    val modelDir: File,
    /** Scratch directory for mmap'd weights. Kept off the FUSE volume the model sits on. */
    val tmpDir: File = File(modelDir, "tmp"),

    val llmModel: String = "llm.mnn",
    val llmWeight: String = "llm.mnn.weight",
    val tokenizerFile: String = "tokenizer.txt",

    val backendType: String = "cpu",
    val threadNum: Int = 6,
    val precision: String = "low",
    /**
     * "low". The obvious guess is wrong: setting this to "high" to keep more resident cost
     * 3.6x — 2.6 tok/s decode against 9.3 — on Qwen3.5-2B, Snapdragon 8+ Gen 1.
     */
    val memory: String = "low",
    val power: String = "high",

    val maxAllTokens: Int = 10_000,
    val maxNewTokens: Int = 4_096,

    val useMmap: Boolean = true,
    /**
     * Off. MNN 3.6.1 can segfault inside createExecutionWithExternal when a dense model is
     * loaded from its generated static cache after an earlier session was released.
     * Rebuilding a 1-2 GB dense model costs seconds; the crash costs the session.
     */
    val useCachedMmap: Boolean = false,
    val kvcacheMmap: Boolean = false,
    val reuseKv: Boolean = true,

    /**
     * 8 = FlashAttention with an fp16 KV cache, and the only mode that survives testing.
     *
     * The quantised alternatives both fail: 10 (int8 K+V, which MNN silently downgrades to
     * 9 / K-only whenever reuse_kv is on) and 14 (4-bit). Observed on device with 10 set,
     * across two SoCs — Qwen3.5-2B emitted 2,048 tokens of the letter F, and Gemma 4 E2B
     * emitted 2,048 copies of its <unused31> placeholder. Mode 8 was also not slower.
     */
    val attentionMode: Int = 8,
    val dynamicOption: Int = 0,

    // Sampling follows what the model publishers validated against rather than generic
    // engine advice. Qwen3.5's model card: temperature 1.0, topP 0.95, topK 20, min_p 0,
    // repetition_penalty 1.0 (off), presence_penalty 1.5.
    //
    // Cold sampling is actively harmful here. Dropping temperature to 0.25 to make code
    // generation "more deterministic" put a 2B into a degenerate repetition loop that ran
    // until it hit the token ceiling; near-greedy decoding is the classic trigger.
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 20,
    val minP: Float = 0.0f,
    val tfsZ: Float = 1.0f,
    val typical: Float = 0.95f,
    /** Multiplicative repetition penalty; the publishers ship this off and lean on presence. */
    val penalty: Float = 1.0f,
    /** Additive per-token-seen penalty — Qwen3.5's own recommended anti-loop control. */
    val presencePenalty: Float = 1.5f,
    val nGram: Int = 8,
    val nGramFactor: Float = 1.02f,

    /**
     * Qwen3.5 emits a `<think>` block before answering when this is on, and MNN offers no
     * way to cap one — no stop string, no thinking-token limit. On a 2B driving a tool
     * loop the latency is not worth it.
     */
    val enableThinking: Boolean = false,
) {

    /** Our settings layered onto the export's own config.json. */
    fun toJson(): JSONObject {
        val shipped = File(modelDir, "config.json")
        val merged = if (shipped.isFile) {
            runCatching { JSONObject(shipped.readText()) }.getOrElse { JSONObject() }
        } else {
            JSONObject()
        }

        return merged.apply {
            // MNN builds each path as base_dir + value, base_dir being this config file's
            // own directory, so these stay bare file names.
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
            put("kvcache_mmap", kvcacheMmap)
            put("reuse_kv", reuseKv)
            // attention_mode supersedes the legacy quant_qkv field from MNN 3.5 on; writing
            // both would just be a second, contradictory source of truth.
            put("attention_mode", attentionMode)
            put("dynamic_option", dynamicOption)
            // tmp_path is the one path MNN takes verbatim rather than joining to base_dir.
            put("tmp_path", tmpDir.absolutePath)

            put("temperature", temperature.toDouble())
            put("topP", topP.toDouble())
            put("topK", topK)
            put("minP", minP.toDouble())
            put("tfsZ", tfsZ.toDouble())
            put("typical", typical.toDouble())
            put("penalty", penalty.toDouble())
            put("presence_penalty", presencePenalty.toDouble())
            put("n_gram", nGram)
            put("ngram_factor", nGramFactor.toDouble())
            put("sampler_type", "mixed")
            // MNN only runs the samplers named here, and the list shipped inside model
            // packages omits tfs and typical — so configuring those without this override
            // sets values that never execute. This is MNN's own documented default order.
            put(
                "mixed_samplers",
                JSONArray(listOf("penalty", "topK", "tfs", "typical", "topP", "min_p", "temperature")),
            )

            // Deep-merged so flipping the thinking flag does not wipe other context keys
            // the package defined. Qwen3.5's chat template gates <think> on exactly this.
            val jinja = optJSONObject("jinja") ?: JSONObject().also { put("jinja", it) }
            val context = jinja.optJSONObject("context") ?: JSONObject().also { jinja.put("context", it) }
            context.put("enable_thinking", enableThinking)
        }
    }

    /**
     * Materialises the merged config next to the model and returns the file MNN loads.
     * The scratch directory is created here too, since MNN expects it to exist.
     */
    fun writeTo(target: File = File(modelDir, "pocketforge_config.json")): File {
        tmpDir.mkdirs()
        target.writeText(toJson().toString(2))
        return target
    }
}
