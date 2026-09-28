package com.srideep.pocketforge.chat

import com.srideep.pocketforge.workspace.WorkspaceEntry

enum class Role { USER, ASSISTANT, SYSTEM }

/** A line under an assistant message recording one tool the agent ran. */
data class ToolTrace(
    val name: String,
    val summary: String,
    val ok: Boolean? = null,
    val detail: String = "",
)

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val tools: List<ToolTrace> = emptyList(),
    val streaming: Boolean = false,
    /** A photo sent with a user message, shown as a thumbnail above its text. */
    val imagePath: String? = null,
)

enum class ModelStatus { MISSING, LOADING, READY, FAILED }

/** Where one catalog model stands, from the model sheet's point of view. */
enum class ModelInstallState { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, LOADED }

data class ModelEntry(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val approxBytes: Long,
    val state: ModelInstallState,
    val progress: Float = 0f,
    val progressLabel: String = "",
    /** The vision helper is used automatically, so it has no "Use" button. */
    val isVision: Boolean = false,
    /** Copied onto the phone over USB rather than downloaded in the app. */
    val sideloadOnly: Boolean = false,
)

/** The file currently open in the editor tab. */
data class OpenFile(
    val path: String,
    val content: String,
    val dirty: Boolean = false,
)

/**
 * Measurements for the current or most recent agent run, for the metrics bar.
 *
 * Token counts and rates come from MNN's own counters; time to first token is wall clock
 * from tapping send to the first streamed chunk, so it includes vision encoding, prompt
 * templating and prefill — the wait the user actually sees.
 */
data class RunMetrics(
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val timeToFirstTokenMs: Long? = null,
    val visionMs: Long = 0L,
    val prefillTokensPerSecond: Double = 0.0,
    val decodeTokensPerSecond: Double = 0.0,
    val generatedTokens: Int = 0,
    val peakRamMb: Int = 0,
    val airplaneMode: Boolean = false,
    val offline: Boolean = false,
    val thermalStatus: Int = 0,
)

data class StudioUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    /** Photo attached to the next message; the page gets built from it. */
    val attachedImage: String? = null,
    val isGenerating: Boolean = false,
    val isListening: Boolean = false,
    /** Dictation sends as soon as speech ends, so a change can be spoken without touching. */
    val handsFree: Boolean = false,
    /** The coder reasons before it answers: slower, but better pages. Changing it reloads the model. */
    val thinking: Boolean = true,
    val metrics: RunMetrics? = null,
    val modelStatus: ModelStatus = ModelStatus.MISSING,
    val modelName: String? = null,
    val models: List<ModelEntry> = emptyList(),
    val files: List<WorkspaceEntry> = emptyList(),
    val openFile: OpenFile? = null,
    val previewUrl: String? = null,
    val devServerRunning: Boolean = false,
    val status: String = "",
)
