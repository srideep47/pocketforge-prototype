package com.srideep.pocketforge.model

import com.srideep.pocketforge.engine.mnn.ModelConfig
import java.io.File

/** What a model is used for. */
enum class ModelRole {
    /** Writes the code; the user picks one. */
    CODER,

    /** Reads photos of sketches for a coder that cannot see; used automatically when present. */
    VISION,
}

/**
 * The models PocketForge ships with, and the files each one needs on disk.
 *
 * Deliberately a closed list rather than a browser over HuggingFace: a phone has a couple
 * of useful choices to make here, and every other MNN export needs its own compatibility
 * testing before it can be trusted to drive a tool-calling loop.
 *
 * File lists and tokenizer names are per-model because the exports do not share a layout.
 *
 * Gemma 4 E2B is left out: it downloads and loads, but generates the <unused31> placeholder
 * instead of text under our runtime settings.
 */
enum class CatalogModel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val subtitle: String,
    /** HuggingFace repo, or null for a model that is copied onto the phone over USB. */
    val repo: String?,
    val tokenizerFile: String,
    val approxBytes: Long,
    val extraFiles: List<String>,
    val role: ModelRole = ModelRole.CODER,
    /** Whether the model takes images itself; a coder that does not uses the vision model. */
    val seesImages: Boolean = false,
    /**
     * CPU threads. MoE models run hundreds of small expert matmuls per token, where thread
     * synchronisation dominates: on Snapdragon 8 Elite, Ling decodes 50% faster on 4 threads
     * than on 6, and 8 threads (which pulls in the slower cores) is slower still.
     */
    val threadNum: Int = 6,
    /** Prefill block size, 0 for one pass; see ModelConfig.prefillChunk. */
    val prefillChunk: Int = 0,
    /** Prompt plus reply budget; smaller means a smaller attention cache. */
    val contextTokens: Int = ModelConfig.DEFAULT_CONTEXT_TOKENS,
    /**
     * Keep the KV cache in a file instead of RAM. The weights are already memory-mapped from
     * storage; this moves the other large allocation there too, so under memory pressure the
     * kernel can page it out instead of the low-memory killer taking the whole app.
     */
    val kvCacheOnStorage: Boolean = false,
) {
    QWEN_4B(
        id = "Qwen3.5-4B",
        displayName = "Qwen 3.5 · 4B",
        shortName = "4B",
        subtitle = "Recommended. Better code, fewer malformed tool calls. Needs ~3 GB free.",
        repo = "taobao-mnn/Qwen3.5-4B-MNN",
        tokenizerFile = "tokenizer.txt",
        approxBytes = 2_833_000_000L,
        extraFiles = listOf("visual.mnn", "visual.mnn.weight"),
        seesImages = true,
    ),
    QWEN_2B(
        id = "Qwen3.5-2B",
        displayName = "Qwen 3.5 · 2B",
        shortName = "2B",
        subtitle = "Fastest. Fits comfortably alongside the dev server.",
        repo = "taobao-mnn/Qwen3.5-2B-MNN",
        tokenizerFile = "tokenizer.txt",
        approxBytes = 1_385_000_000L,
        extraFiles = listOf("visual.mnn", "visual.mnn.weight"),
        seesImages = true,
    ),
    QWEN_08B_VISION(
        id = "Qwen3.5-0.8B",
        displayName = "Qwen 3.5 · 0.8B vision",
        shortName = "0.8B",
        subtitle = "Reads photos of sketches for coders that cannot see images.",
        repo = "taobao-mnn/Qwen3.5-0.8B-MNN",
        tokenizerFile = "tokenizer.txt",
        approxBytes = 543_000_000L,
        extraFiles = listOf("visual.mnn", "visual.mnn.weight"),
        role = ModelRole.VISION,
        seesImages = true,
    ),
    ;

    val sideloadOnly: Boolean get() = repo == null

    /**
     * What the runtime actually loads. The repos also carry `llm.mnn.json` (a multi-megabyte
     * debug dump) and export metadata, which are pure download cost on a phone.
     */
    val requiredFiles: List<String>
        get() = listOf(
            "llm.mnn",
            "llm.mnn.weight",
            // The export's own runtime config, which our settings merge onto rather than
            // replace — it carries per-export choices we would otherwise discard.
            "config.json",
            "llm_config.json",
            tokenizerFile,
        ) + extraFiles

    fun downloadUrl(file: String): String = "https://huggingface.co/$repo/resolve/main/$file"

    fun directoryIn(modelsDir: File): File = File(modelsDir, id)

    /**
     * Written by the downloader once every byte has landed.
     *
     * Presence-and-non-empty is not a sufficient test: files arrive one at a time, so a
     * download interrupted partway looks complete and the model then fails to load with
     * a truncated weight file.
     */
    fun completionMarkerIn(modelsDir: File): File = File(directoryIn(modelsDir), ".complete")

    /**
     * A USB-copied model has no downloader to write the marker, so for those every required
     * file being present and non-empty is taken as installed.
     */
    fun isInstalledIn(modelsDir: File): Boolean =
        (sideloadOnly || completionMarkerIn(modelsDir).isFile) &&
            requiredFiles.all { File(directoryIn(modelsDir), it).length() > 0L }

    companion object {
        fun byId(id: String): CatalogModel? = entries.firstOrNull { it.id == id }

        val vision: CatalogModel get() = QWEN_08B_VISION
    }
}
