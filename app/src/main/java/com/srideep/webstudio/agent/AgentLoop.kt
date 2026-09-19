package com.srideep.webstudio.agent

import com.srideep.webstudio.engine.mnn.MnnLlmEngine
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

    fun run(userMessage: String): Flow<AgentUpdate> = flow {
        val parser = ToolCallParser()
        var prompt = HermesPrompt.firstTurn(userMessage)

        for (iteration in 0 until maxIterations) {
            currentCoroutineContext().ensureActive()

            val pending = mutableListOf<ToolCall>()
            parser.reset()

            engine.generate(prompt, maxNewTokensPerTurn).collect { chunk ->
                for (event in parser.feed(chunk)) {
                    emitEvent(event, pending)
                }
            }
            for (event in parser.finish()) {
                emitEvent(event, pending)
            }

            if (pending.isEmpty()) {
                emit(AgentUpdate.Done)
                return@flow
            }

            val results = mutableListOf<Pair<ToolCall, ToolResult>>()
            for (call in pending) {
                currentCoroutineContext().ensureActive()
                emit(AgentUpdate.ToolStarted(call.name, describe(call)))
                val result = tools.execute(call)
                results += call to result
                emit(AgentUpdate.ToolFinished(call.name, result.ok, result.content.take(400)))
                if (result.ok && call.name == "start_dev_server") {
                    PREVIEW_URL.find(result.content)?.value?.let { emit(AgentUpdate.PreviewReady(it)) }
                }
            }

            prompt = HermesPrompt.toolResponses(results)
        }

        emit(AgentUpdate.Failed("stopped after $maxIterations tool rounds"))
    }

    private suspend fun FlowCollector<AgentUpdate>.emitEvent(
        event: AgentEvent,
        pending: MutableList<ToolCall>,
    ) {
        when (event) {
            is AgentEvent.Text -> emit(AgentUpdate.Token(event.delta))
            is AgentEvent.Call -> pending += event.call
            // Show the model's mistake instead of silently dropping the block; it is the
            // single most useful thing to see when a small model drifts off-format.
            is AgentEvent.Malformed -> emit(AgentUpdate.Failed("bad tool call (${event.reason})"))
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
        val PREVIEW_URL = Regex("""http://localhost:\d+""")
    }
}
