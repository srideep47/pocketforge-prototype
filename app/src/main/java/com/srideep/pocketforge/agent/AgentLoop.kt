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
    /** A full page is 1.5-2.5k tokens; at 2048 a calculator page was cut off on every turn. */
    private val maxNewTokensPerTurn: Int = 4096,
    /** Reasoning comes out of the same budget as the page, and a page alone is ~1.5k tokens. */
    private val maxNewTokensThinking: Int = 8192,
    /** Reasoning past this (~1k tokens, two minutes on the phone) is cut off; see run(). */
    private val thinkingBudgetChars: Int = 4000,
) {

    /**
     * @param currentPage the page being changed, or null to build a new one.
     * @param imageTag an MNN `<img>` reference for a photo the page should be built from, for a
     *   coder that can see images.
     * @param sketchDescription the vision sidecar's reading of that photo, for one that cannot.
     * @param thinking whether the model was loaded with thinking on. Qwen3.5's template then
     *   opens `<think>` itself, so each reply starts mid-reasoning and only `</think>` marks
     *   where the answer begins.
     */
    fun run(
        userMessage: String,
        currentPage: String? = null,
        imageTag: String? = null,
        sketchDescription: String? = null,
        thinking: Boolean = false,
    ): Flow<AgentUpdate> = flow {
        val maxNewTokens = if (thinking) maxNewTokensThinking else maxNewTokensPerTurn
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
            // Reasoning is held back from the parser: it often quotes a <site> or a tool call
            // it is planning, and parsing that would act on the plan instead of the answer.
            var reasoning = if (thinking) StringBuilder() else null
            var overBudget = false
            // What follows the template's assistant header before the answer: the closed
            // thought in thinking mode, nothing otherwise (the template closes an empty one).
            var thoughtPrefix = ""
            if (reasoning != null) emit(AgentUpdate.ToolStarted(THINK_STEP, "Thinking"))

            suspend fun take(chunk: String) {
                streamed += chunk.length
                emit(AgentUpdate.Progress(streamed))
                var answer = chunk
                val thought = reasoning
                if (thought != null) {
                    thought.append(chunk)
                    val end = thought.indexOf(THINK_END)
                    if (end < 0) {
                        if (thought.length > thinkingBudgetChars && !overBudget) {
                            overBudget = true
                            engine.stop()
                        }
                        return
                    }
                    answer = thought.substring(end + THINK_END.length).trimStart()
                    thoughtPrefix = thought.substring(0, end) + THINK_END + "\n\n"
                    emit(AgentUpdate.ToolFinished(THINK_STEP, true, thought.substring(0, end).trim().takeLast(400)))
                    reasoning = null
                }
                rawAssistant.append(answer)
                for (event in parser.feed(answer)) {
                    if (event is AgentEvent.Malformed) sawMalformed = true
                    emitEvent(event, pending)
                }
            }

            engine.generateChat(conversation, maxNewTokens).collect { take(it) }
            val cut = reasoning
            if (overBudget && cut != null) {
                // Budget forcing: Qwen3.5 left alone reasons for thousands of tokens, about
                // fifteen minutes on this phone, often without ever reaching the page. Its plan
                // is complete long before that, so the thought is closed for it and the reply
                // continues from there as the answer.
                emit(AgentUpdate.TurnStats(engine.lastStats()))
                val plan = cut.toString().trimEnd()
                emit(AgentUpdate.ToolFinished(THINK_STEP, true, plan.takeLast(400)))
                reasoning = null
                val prefix = plan + "\n\n" + THINK_WRAP_UP + "\n" + THINK_END + "\n\n"
                thoughtPrefix = prefix
                engine.continueChat(conversation, prefix, maxNewTokensPerTurn).collect { take(it) }
            }
            // A page longer than the budget stops mid-document. Asking again regenerates it from
            // the top and stops in the same place (three turns of that, 16 minutes, no page, on
            // device), so the reply is continued from where it was cut instead.
            var continuations = 0
            while (
                reasoning == null && continuations < MAX_CONTINUATIONS &&
                rawAssistant.length < MAX_CONTINUED_CHARS && SiteArtifact.isCutOff(rawAssistant)
            ) {
                continuations++
                emit(AgentUpdate.TurnStats(engine.lastStats()))
                Log.i(TAG, "turn $iteration: page cut off at ${rawAssistant.length} chars, continuing")
                engine.continueChat(conversation, thoughtPrefix + rawAssistant, maxNewTokensPerTurn).collect { take(it) }
            }
            reasoning?.let {
                // The budget ran out mid-thought; the retry below asks again.
                emit(AgentUpdate.ToolFinished(THINK_STEP, false, "ran out of tokens while thinking"))
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
                        raw = SITE_ARTIFACT,
                    )
                    sawMalformed = false
                }
            }
            var missedFinds = emptyList<String>()
            if (pending.isEmpty() && currentPage != null) {
                val edits = EditArtifact.extract(rawAssistant.toString())
                if (edits.isNotEmpty()) {
                    when (val outcome = EditArtifact.apply(currentPage, edits)) {
                        is EditArtifact.Outcome.Applied -> {
                            Log.i(TAG, "turn $iteration: applied ${outcome.count} edit(s)")
                            SiteArtifact.extract("<site>" + outcome.html + "</site>")?.let { html ->
                                pending += ToolCall(
                                    name = "create_file",
                                    arguments = org.json.JSONObject()
                                        .put("path", "index.html")
                                        .put("content", html),
                                    raw = SITE_ARTIFACT,
                                )
                                sawMalformed = false
                            }
                        }
                        is EditArtifact.Outcome.Missed -> missedFinds = outcome.finds
                    }
                }
            }
            Log.i(
                TAG,
                "turn $iteration: ${streamed} chars streamed, answer ${rawAssistant.length} chars, " +
                    "calls=${pending.size} malformed=$sawMalformed overBudget=$overBudget missedFinds=${missedFinds.size}",
            )
            conversation += ChatTurn("assistant", rawAssistant.toString())

            if (pending.isEmpty()) {
                // A turn that produced a broken call is worth one nudge: the model
                // usually had the right idea and lost the format, and re-prompting is
                // far cheaper than making the user retype the task.
                if (missedFinds.isNotEmpty() && incompleteRetries < 2) {
                    incompleteRetries++
                    conversation += ChatTurn("user", HermesPrompt.retryAfterMissedEdits(missedFinds))
                    continue
                }
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

            // A complete page arrived as a <site> artifact and was saved: that is the whole task.
            // Asking the model to acknowledge it re-feeds the entire page as a new prompt, which
            // on Ling cost a minute of prefill and ~3.5 GB of memory, enough for the low-memory
            // killer to take the app, all to produce "Done."
            if (results.all { (call, result) -> call.raw == SITE_ARTIFACT && result.ok } && siteChanged) {
                emit(AgentUpdate.Token("Built index.html."))
                tools.ensurePreview()?.let { result ->
                    PREVIEW_URL.find(result.content)?.value?.let { emit(AgentUpdate.PreviewReady(it)) }
                }
                emit(AgentUpdate.Done)
                return@flow
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

    companion object {
        /** Name of the step that stands for the model's reasoning in a message's tool list. */
        const val THINK_STEP = "think"
        private const val TAG = "AgentLoop"
        private val PREVIEW_URL = Regex("""http://localhost:\d+""")
        private const val SITE_ARTIFACT = "<site> artifact"
        private const val THINK_END = "</think>"
        private const val MAX_CONTINUATIONS = 1

        /**
         * A page this long when cut off is not unfinished but runaway: on device, a calculator
         * reached 11k characters across two continuations without closing. Asking again is the
         * better bet.
         */
        private const val MAX_CONTINUED_CHARS = 8000

        /** Closes a cut-off thought in the model's own voice, so the answer follows naturally. */
        private const val THINK_WRAP_UP = "That is enough planning. I will write the complete page now."
    }
}
