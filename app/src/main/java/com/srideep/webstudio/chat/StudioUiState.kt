package com.srideep.webstudio.chat

import com.srideep.webstudio.workspace.WorkspaceEntry

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
)

enum class ModelStatus { MISSING, LOADING, READY, FAILED }

/** The file currently open in the editor tab. */
data class OpenFile(
    val path: String,
    val content: String,
    val dirty: Boolean = false,
)

data class StudioUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val isListening: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.MISSING,
    val modelName: String? = null,
    val availableModels: List<String> = emptyList(),
    val files: List<WorkspaceEntry> = emptyList(),
    val openFile: OpenFile? = null,
    val previewUrl: String? = null,
    val devServerRunning: Boolean = false,
    val status: String = "",
)
