package com.srideep.pocketforge.chat

import com.srideep.pocketforge.workspace.WorkspaceEntry

enum class Role { USER, ASSISTANT, SYSTEM }

/** A line under an assistant message recording one tool the agent ran. */
data class ToolTrace(
    val name: String,
    val summary: String,
    val ok: Boolean? = null,
    val detail: String = "",
    /** How long the step took, once it has finished. */
    val elapsedMs: Long? = null,
    /** A picture the step produced: the page screenshot a check_render step judged. */
    val imagePath: String? = null,
    /** `SystemClock.elapsedRealtime()` when the step started; used to fill [elapsedMs]. */
    val startedAt: Long = 0L,
)

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String = "",
    val tools: List<ToolTrace> = emptyList(),
    val streaming: Boolean = false,
    /** A photo sent with a user message, shown as a thumbnail above its text. */
    val imagePath: String? = null,
    /** The finished run's numbers, kept per assistant message so earlier turns keep theirs. */
    val metrics: RunMetrics? = null,
)

/** What the agent is doing right now, for a live label while the counters catch up. */
enum class RunPhase(val label: String) {
    IDLE("idle"),
    PREFILL("reading prompt…"),
    THINKING("thinking…"),
    WRITING("writing…"),
    TOOLS("running tools…"),
    CHECKING("checking render…"),
    DONE("done"),
}

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
    /**
     * Decode tok/s only arrives when a turn ends (MNN counts per `response()`), so during a
     * turn this is the honest thing to show instead of a throughput that is still zero.
     */
    val phase: RunPhase = RunPhase.IDLE,
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
    /**
     * The coder plans before it writes (capped at ~1k tokens). On by default: without it the 4B
     * wrote bloated pages that ran past the budget; with it a photo-to-app run took 5 minutes.
     * Changing it reloads the model.
     */
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
