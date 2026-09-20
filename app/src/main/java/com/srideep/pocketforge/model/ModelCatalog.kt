package com.srideep.pocketforge.model

import java.io.File

/**
 * The models PocketForge ships with, and the files each one needs on disk.
 *
 * Deliberately a closed list rather than a browser over HuggingFace: a phone has a couple
 * of useful choices to make here, and every other MNN export needs its own compatibility
 * testing before it can be trusted to drive a tool-calling loop.
 *
 * File lists are per-model on purpose. The exports do not share a layout — Qwen carries a
 * plain `tokenizer.txt` and a vision pair, while Gemma 4 uses `tokenizer.mtok` and adds
 * per-layer embeddings and an audio encoder that MNN refuses to start without.
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
    GEMMA_E2B(
        id = "Gemma4-E2B",
        displayName = "Gemma 4 · E2B",
        shortName = "E2B",
        subtitle = "Google's on-device build. Needs ~4 GB free.",
        repo = "taobao-mnn/gemma-4-E2B-it-MNN",
        tokenizerFile = "tokenizer.mtok",
        approxBytes = 3_730_000_000L,
        // Per-layer embeddings and the audio encoder are named in this export's
        // llm_config.json, so MNN looks for them whether or not we use the modality.
        extraFiles = listOf(
            "ple_embeddings_int4.bin",
            "visual.mnn",
            "visual.mnn.weight",
            "audio.mnn",
            "audio.mnn.weight",
        ),
    ),
    ;

    /**
     * What the runtime actually loads. The repos also carry `llm.mnn.json` (a multi-megabyte
     * debug dump) and export metadata, which are pure download cost on a phone.
     */
    val requiredFiles: List<String>
        get() = listOf("llm.mnn", "llm.mnn.weight", "llm_config.json", tokenizerFile) + extraFiles

    fun downloadUrl(file: String): String = "https://huggingface.co/$repo/resolve/main/$file"

    fun directoryIn(modelsDir: File): File = File(modelsDir, id)

    /** A model counts as installed once every required file is present and non-empty. */
    fun isInstalledIn(modelsDir: File): Boolean {
        val dir = directoryIn(modelsDir)
        return dir.isDirectory && requiredFiles.all { File(dir, it).length() > 0L }
    }

    companion object {
        fun byId(id: String): CatalogModel? = entries.firstOrNull { it.id == id }
    }
}
