package com.srideep.pocketforge.agent

import android.util.Log
import com.srideep.pocketforge.engine.mnn.ChatTurn
import com.srideep.pocketforge.engine.mnn.GenerationStats
import com.srideep.pocketforge.engine.mnn.MnnLlmEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/** Progress the UI renders while the agent works. */
sealed interface AgentUpdate {
    /** A fragment of the assistant's prose. */
    data class Token(val text: String) : AgentUpdate

    data class ToolStarted(val name: String, val summary: String) : AgentUpdate

    data class ToolFinished(val name: String, val ok: Boolean, val detail: String) : AgentUpdate

    /**
     * Characters generated so far this turn. A tool call's body is buffered until its
     * closing tag, so without this the UI shows nothing at all while the model writes a
     * file — which is most of a run.
     */
    data class Progress(val charsGenerated: Int) : AgentUpdate

    /**
     * MNN's counters for the turn that just finished. Emitted per turn rather than read
     * once at the end: the counters are per-`response()` call, and the last turn of a run
     * is a short summary whose throughput is all warm-up overhead.
     */
    data class TurnStats(val stats: GenerationStats) : AgentUpdate

    /** The dev server came up; the preview tab should point here. */
    data class PreviewReady(val url: String) : AgentUpdate

    data class Failed(val reason: String) : AgentUpdate

    data object Done : AgentUpdate
}

/**
 * Drives the model until it stops asking for tools.
 *
 * One iteration is: generate, parse `<tool_call>` blocks out of the stream, run them,
 * feed the results back. The loop ends when a turn produces no calls, or after
 * [maxIterations] — a small local model will occasionally loop on a failing edit and the
 * phone should not burn through its battery discovering that.
 */
class AgentLoop(
    private val engine: MnnLlmEngine,
    private val tools: AgentTools,
    private val maxIterations: Int = 8,
    private val maxNewTokensPerTurn: Int = 2048,
) {

    /**
     * @param currentPage the page being changed, or null to build a new one.
     * @param imageTag an MNN `<img>` reference for a photo the page should be built from, for a
     *   coder that can see images.
     * @param sketchDescription the vision sidecar's reading of that photo, for one that cannot.
     */
    fun run(
        userMessage: String,
        currentPage: String? = null,
        imageTag: String? = null,
        sketchDescription: String? = null,
    ): Flow<AgentUpdate> = flow {
        val parser = ToolCallParser()
        val firstTurn = HermesPrompt.userTurn(userMessage, currentPage, imageTag, sketchDescription)
        val conversation = mutableListOf(
            ChatTurn("system", HermesPrompt.systemPrompt()),
            ChatTurn("user", firstTurn),
        )
        var malformedRetries = 0
        var incompleteRetries = 0
        var siteChanged = false

        for (iteration in 0 until maxIterations) {
            currentCoroutineContext().ensureActive()

            val pending = mutableListOf<ToolCall>()
            var sawMalformed = false
            parser.reset()
            val rawAssistant = StringBuilder()

            var streamed = 0
            engine.generateChat(conversation, maxNewTokensPerTurn).collect { chunk ->
                rawAssistant.append(chunk)
                streamed += chunk.length
                emit(AgentUpdate.Progress(streamed))
                for (event in parser.feed(chunk)) {
                    if (event is AgentEvent.Malformed) sawMalformed = true
                    emitEvent(event, pending)
                }
            }
            for (event in parser.finish()) {
                if (event is AgentEvent.Malformed) sawMalformed = true
                emitEvent(event, pending)
            }
            emit(AgentUpdate.TurnStats(engine.lastStats()))
            if (iteration == 0 && imageTag != null) {
                // The photo has done its job; later rounds only need the text.
                conversation[1] = ChatTurn("user", HermesPrompt.withoutImage(firstTurn, imageTag))
            }
            if (pending.isEmpty()) {
                SiteArtifact.extract(rawAssistant.toString())?.let { html ->
                    pending += ToolCall(
                        name = "create_file",
                        arguments = org.json.JSONObject()
                            .put("path", "index.html")
                            .put("content", html),
                        raw = "<site> artifact",
                    )
                    sawMalformed = false
                }
            }
            conversation += ChatTurn("assistant", rawAssistant.toString())

            if (pending.isEmpty()) {
                // A turn that produced a broken call is worth one nudge: the model
                // usually had the right idea and lost the format, and re-prompting is
                // far cheaper than making the user retype the task.
                if (sawMalformed && malformedRetries < 1) {
                    malformedRetries++
                    conversation += ChatTurn("user", HermesPrompt.retryAfterMalformed())
                    continue
                }
                if ((!tools.hasEntryPage() || !siteChanged) && incompleteRetries < 2) {
                    incompleteRetries++
                    conversation += ChatTurn(
                        "user",
                        HermesPrompt.retryIncomplete(hasIndex = tools.hasEntryPage()),
                    )
                    continue
                }
                if (!tools.hasEntryPage() || !siteChanged) {
                    emit(AgentUpdate.Failed("The model stopped before it wrote a valid index.html"))
                    return@flow
                }
                val finalText = rawAssistant.toString()
                    .replace(Regex("(?s)<think>.*?</think>"), "")
                    .trim()
                if (finalText.isNotBlank()) emit(AgentUpdate.Token(finalText))
                tools.ensurePreview()?.let { result ->
                    PREVIEW_URL.find(result.content)?.value?.let { emit(AgentUpdate.PreviewReady(it)) }
                }
                emit(AgentUpdate.Done)
                return@flow
            }

            val results = mutableListOf<Pair<ToolCall, ToolResult>>()
            for (call in pending) {
                currentCoroutineContext().ensureActive()
                Log.i(TAG, "tool call name=${call.name} keys=${call.arguments.keys().asSequence().toList()}")
                emit(AgentUpdate.ToolStarted(call.name, describe(call)))
                val result = tools.execute(call)
                results += call to result
                if (result.ok &&
                    (result.content.startsWith("wrote ") || result.content.startsWith("edited "))
                ) {
                    siteChanged = true
                }
                emit(AgentUpdate.ToolFinished(call.name, result.ok, result.content.take(400)))
                if (result.ok) {
                    PREVIEW_URL.find(result.content)?.value?.let { emit(AgentUpdate.PreviewReady(it)) }
                }
            }

            conversation += ChatTurn("user", HermesPrompt.toolResponses(results))
            tools.ensurePreview()?.let { result ->
                PREVIEW_URL.find(result.content)?.value?.let { emit(AgentUpdate.PreviewReady(it)) }
            }
        }

        emit(AgentUpdate.Failed("stopped after $maxIterations tool rounds"))
    }

    private suspend fun FlowCollector<AgentUpdate>.emitEvent(
        event: AgentEvent,
        pending: MutableList<ToolCall>,
    ) {
        when (event) {
            // Hold prose until the turn ends. A <site> artifact arrives through this branch too;
            // streaming it would dump source code into chat before we can classify the turn.
            is AgentEvent.Text -> Unit
            is AgentEvent.Call -> pending += event.call
            is AgentEvent.Malformed -> {
                Log.w(TAG, "malformed tool call (${event.reason}): ${event.raw}")
            }
        }
    }

    private fun describe(call: ToolCall): String = when (call.name) {
        "create_file", "read_file" -> call.arguments.optString("path")
        "edit_file" -> call.arguments.optString("path")
        "list_files" -> call.arguments.optString("directory").ifBlank { "." }
        "start_dev_server" -> call.arguments.optString("project_path").ifBlank { "." }
        else -> ""
    }

    private companion object {
        const val TAG = "AgentLoop"
        val PREVIEW_URL = Regex("""http://localhost:\d+""")
    }
}
