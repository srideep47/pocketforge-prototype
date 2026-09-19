package com.srideep.pocketforge.model

import java.io.File

/**
 * The two models PocketForge ships with, and the files each one needs on disk.
 *
 * Deliberately a closed list rather than a browser over HuggingFace: a phone has one
 * useful choice to make here — the small fast one or the bigger accurate one — and every
 * other MNN export needs its own compatibility testing before it can be trusted to drive
 * a tool-calling loop.
 */
enum class CatalogModel(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val repo: String,
    val approxBytes: Long,
) {
    QWEN_2B(
        id = "Qwen3.5-2B",
        displayName = "Qwen 3.5 · 2B",
        subtitle = "Fast. Fits comfortably alongside the dev server.",
        repo = "taobao-mnn/Qwen3.5-2B-MNN",
        approxBytes = 1_385_000_000L,
    ),
    QWEN_4B(
        id = "Qwen3.5-4B",
        displayName = "Qwen 3.5 · 4B",
        subtitle = "Better code, fewer malformed tool calls. Needs ~3 GB free.",
        repo = "taobao-mnn/Qwen3.5-4B-MNN",
        approxBytes = 2_833_000_000L,
    ),
    ;

    /**
     * What the runtime actually loads. The repos also carry `llm.mnn.json` (a 9 MB debug
     * dump) and export metadata, which are pure download cost on a phone.
     */
    val requiredFiles: List<String>
        get() = listOf(
            "llm.mnn",
            "llm.mnn.weight",
            "llm_config.json",
            "tokenizer.txt",
            // Both exports are multimodal; MNN refuses to load without the visual pair
            // even when only text is used.
            "visual.mnn",
            "visual.mnn.weight",
        )

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
