package com.srideep.pocketforge.agent

import android.util.Log
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

    fun run(userMessage: String): Flow<AgentUpdate> = flow {
        val parser = ToolCallParser()
        var prompt = HermesPrompt.firstTurn(userMessage)
        var malformedRetries = 0

        for (iteration in 0 until maxIterations) {
            currentCoroutineContext().ensureActive()

            val pending = mutableListOf<ToolCall>()
            var sawMalformed = false
            parser.reset()

            var streamed = 0
            engine.generate(prompt, maxNewTokensPerTurn).collect { chunk ->
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

            if (pending.isEmpty()) {
                // A turn that produced a broken call is worth one nudge: the model
                // usually had the right idea and lost the format, and re-prompting is
                // far cheaper than making the user retype the task.
                if (sawMalformed && malformedRetries < 1) {
                    malformedRetries++
                    prompt = HermesPrompt.retryAfterMalformed()
                    continue
                }
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
            is AgentEvent.Malformed -> {
                Log.w(TAG, "malformed tool call (${event.reason}): ${event.raw}")
                emit(AgentUpdate.Failed("bad tool call (${event.reason})"))
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
