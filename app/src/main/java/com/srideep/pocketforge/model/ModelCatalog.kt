package com.srideep.pocketforge.model

import java.io.File

/**
 * The models PocketForge ships with, and the files each one needs on disk.
 *
 * Deliberately a closed list rather than a browser over HuggingFace: a phone has a couple
 * of useful choices to make here, and every other MNN export needs its own compatibility
 * testing before it can be trusted to drive a tool-calling loop.
 *
 * File lists and tokenizer names are per-model because the exports do not share a layout.
 *
 * Only the Qwen 3.5 pair is listed. Gemma 4 E2B downloads and loads, but generates the
 * <unused31> placeholder instead of text under our runtime settings — its export declares
 * mixed attention with a sliding window and does not tolerate the INT8 KV cache mode the
 * Qwen exports are happy with. It is left out rather than shipped broken; the per-model
 * fields here are what it would need to come back.
 */
enum class CatalogModel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val subtitle: String,
    val repo: String,
    val tokenizerFile: String,
    val approxBytes: Long,
    val extraFiles: List<String>,
) {
    QWEN_2B(
        id = "Qwen3.5-2B",
        displayName = "Qwen 3.5 · 2B",
        shortName = "2B",
        subtitle = "Fastest. Fits comfortably alongside the dev server.",
        repo = "taobao-mnn/Qwen3.5-2B-MNN",
        tokenizerFile = "tokenizer.txt",
        approxBytes = 1_385_000_000L,
        extraFiles = listOf("visual.mnn", "visual.mnn.weight"),
    ),
    QWEN_4B(
        id = "Qwen3.5-4B",
        displayName = "Qwen 3.5 · 4B",
        shortName = "4B",
        subtitle = "Better code, fewer malformed tool calls. Needs ~3 GB free.",
        repo = "taobao-mnn/Qwen3.5-4B-MNN",
        tokenizerFile = "tokenizer.txt",
        approxBytes = 2_833_000_000L,
        extraFiles = listOf("visual.mnn", "visual.mnn.weight"),
    ),
    ;

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

    fun isInstalledIn(modelsDir: File): Boolean = completionMarkerIn(modelsDir).isFile &&
        requiredFiles.all { File(directoryIn(modelsDir), it).length() > 0L }

    companion object {
        fun byId(id: String): CatalogModel? = entries.firstOrNull { it.id == id }
    }
}
