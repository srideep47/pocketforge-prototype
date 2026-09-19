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
    /**
     * Three, matching the A710 cluster on the device this was measured on.
     *
     * MNN splits a layer evenly across threads and waits for the slowest, so pulling in
     * little cores costs more than it adds. Worth knowing: with power=high MNN still
     * never scheduled anything onto the prime core here — during generation cpu7 sat at
     * its 787 MHz floor while cpu4-6 ran flat out at 2745 MHz — so the useful width is
     * the mid cluster, and asking for more threads than that only adds contention.
     */
    val threadNum: Int = 3,
    /** fp16 compute. */
    val precision: String = "low",
    /**
     * "low", and this one is worth stating plainly because the obvious guess is wrong:
     * setting it to "high" to "keep more resident" cost 3.6x. Measured on Qwen3.5-2B,
     * Snapdragon 8+ Gen 1 — 2.6 tok/s decode at "high" against 9.3 at "low".
     */
    val memory: String = "low",
    val power: String = "high",

    /** Total context window, prompt plus generation. */
    val maxAllTokens: Int = 32_768,
    /** Ceiling on a single reply, so one runaway turn cannot eat the whole window. */
    /**
     * Deliberately tight. A small model that loses the format loops instead of stopping,
     * and the only thing a high ceiling buys is a longer wait before we find out.
     */
    val maxNewTokens: Int = 2_048,

    /**
     * On. The reasoning against it — that paging weights from UFS makes decode
     * storage-bound — did not survive measurement: mmap'd was slightly faster per token
     * (10.3 against 9.9 tok/s) and cut model load from roughly 40s to 7s, because the
     * page cache holds a 1.2 GB model comfortably on a 12 GB device.
     */
    val useMmap: Boolean = true,
    val useCachedMmap: Boolean = true,
    val reuseKv: Boolean = true,
    /** 10 = INT8 quantised K and V cache. MNN downgrades it to 9 when reuse_kv is on. */
    val attentionMode: Int = 10,
    /**
     * Off: a disk-backed KV cache puts a UFS read in the path of every attention step.
     * MNN grows the cache with the sequence rather than preallocating [maxAllTokens], so
     * an ordinary agent run stays well inside RAM even with a 32k ceiling.
     */
    val kvcacheMmap: Boolean = false,

    /**
     * Lookahead speculative decoding drafts tokens from n-grams already seen and verifies
     * them in one pass, which is a real win on repetitive code.
     *
     * Off after measuring it on a Snapdragon 8+ Gen 1 with Qwen3.5-2B: it did not improve
     * time-to-finish, and turns ran noticeably longer, which is what drafting from
     * already-seen n-grams does to a model inclined to restate itself. Prefill, not
     * decode, was the real cost here — see HermesPrompt.
     */
    val speculativeType: String = "",
    val draftPredictLength: Int = 6,
    val ngramMatchMaxLen: Int = 4,
    /** Feed generated tokens back into the n-gram table, not just the prompt. */
    val ngramUpdate: Boolean = true,

    // Sampling. These are the values the agent loop was first proven against on device.
    // Lowering the temperature and adding a penalty stage to chase "more deterministic
    // code" made a 2B fall into degenerate repetition — near-greedy decoding is the
    // classic trigger — so any change here needs a run on hardware behind it.
    val temperature: Float = 0.6f,
    val topP: Float = 0.95f,
    val topK: Int = 40,

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

        if (speculativeType.isNotBlank()) {
            put("speculative_type", speculativeType)
            put("draft_predict_length", draftPredictLength)
            put("ngram_match_maxlen", ngramMatchMaxLen)
            put("ngram_update", ngramUpdate)
            put("draft_match_strictness", "low")
            put("draft_selection_rule", "freqxlen")
        }
        // tmp_path is the one path MNN takes verbatim rather than joining to base_dir.
        put("tmp_path", tmpDir.absolutePath)

        // No sampler_type override: MNN's default chain is what this loop was proven
        // against, and forcing "mixed" with a penalty stage is what broke it.
        put("temperature", temperature.toDouble())
        put("topP", topP.toDouble())
        put("topK", topK)
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
