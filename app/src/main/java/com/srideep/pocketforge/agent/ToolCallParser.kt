package com.srideep.pocketforge.agent

import org.json.JSONObject

/** One tool invocation the model asked for. */
data class ToolCall(val name: String, val arguments: JSONObject, val raw: String)

/** What the parser hands back as tokens arrive. */
sealed interface AgentEvent {
    /** Prose meant for the chat transcript. */
    data class Text(val delta: String) : AgentEvent

    data class Call(val call: ToolCall) : AgentEvent

    /** A `<tool_call>` block that was not valid JSON. */
    data class Malformed(val raw: String, val reason: String) : AgentEvent
}

/**
 * Incremental parser for the Hermes tool-calling format.
 *
 * Tokens arrive a fragment at a time, so tags can be split across chunks. Text is only
 * released once it cannot still turn out to be the start of `<tool_call>`, which keeps a
 * half-typed tag from flashing up in the transcript.
 */
class ToolCallParser {

    private val buffer = StringBuilder()
    private var insideCall = false

    fun feed(chunk: String): List<AgentEvent> {
        buffer.append(chunk)
        val events = mutableListOf<AgentEvent>()

        while (true) {
            if (insideCall) {
                val end = buffer.indexOf(CLOSE_TAG)
                if (end < 0) break
                var body = buffer.substring(0, end)
                buffer.delete(0, end + CLOSE_TAG.length)
                insideCall = false
                // Small models re-open the tag before getting to the JSON, often by
                // parroting the format example. Keep only what follows the last one.
                val reopened = body.lastIndexOf(OPEN_TAG)
                if (reopened >= 0) body = body.substring(reopened + OPEN_TAG.length)
                events += parseCall(body)
            } else {
                val start = buffer.indexOf(OPEN_TAG)
                if (start < 0) {
                    val safe = buffer.length - longestTagPrefixAtEnd()
                    if (safe > 0) {
                        val text = buffer.substring(0, safe)
                        buffer.delete(0, safe)
                        if (text.isNotEmpty()) events += AgentEvent.Text(text)
                    }
                    break
                }
                if (start > 0) {
                    events += AgentEvent.Text(buffer.substring(0, start))
                }
                buffer.delete(0, start + OPEN_TAG.length)
                insideCall = true
            }
        }
        return events
    }

    /** Flushes whatever is left once the model stops generating. */
    fun finish(): List<AgentEvent> {
        val events = mutableListOf<AgentEvent>()
        if (buffer.isNotEmpty()) {
            val leftover = buffer.toString()
            buffer.setLength(0)
            events += if (insideCall) {
                AgentEvent.Malformed(leftover, "generation ended inside a tool call")
            } else {
                AgentEvent.Text(leftover)
            }
        }
        insideCall = false
        return events
    }

    fun reset() {
        buffer.setLength(0)
        insideCall = false
    }

    private fun parseCall(body: String): AgentEvent {
        // Fences, a stray "json" language tag and leading prose all show up here; the
        // object starts at the first brace.
        val trimmed = body.trim().trim('`').removePrefix("json").trim()
        val start = trimmed.indexOf('{')
        if (start < 0) {
            return AgentEvent.Malformed(body, "no JSON object in the tool call")
        }

        val lastBrace = trimmed.lastIndexOf('}')
        val candidates = buildList {
            if (lastBrace > start) add(trimmed.substring(start, lastBrace + 1))
            // A 2B model drops a closing brace often enough to be worth repairing
            // rather than throwing the whole turn away.
            add(closeOpenBraces(trimmed.substring(start)))
        }

        var lastError = "invalid JSON"
        for (candidate in candidates) {
            val parsed = runCatching { JSONObject(candidate) }.getOrElse { error ->
                lastError = error.message ?: "invalid JSON"
                null
            } ?: continue

            val name = parsed.optString("name")
            if (name.isBlank()) return AgentEvent.Malformed(body, "tool call has no name")
            val args = parsed.optJSONObject("arguments")
                ?: parsed.optJSONObject("parameters")
                ?: JSONObject()
            return AgentEvent.Call(ToolCall(name, args, body))
        }
        return AgentEvent.Malformed(body, lastError)
    }

    /** Appends whatever quote and braces the model left open, so the object parses. */
    private fun closeOpenBraces(candidate: String): String {
        var depth = 0
        var inString = false
        var escaped = false
        for (character in candidate) {
            when {
                escaped -> escaped = false
                inString && character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> depth++
                character == '}' -> depth--
            }
        }
        return buildString {
            append(candidate)
            if (inString) append('"')
            repeat(maxOf(depth, 0)) { append('}') }
        }
    }

    /** Length of the trailing run that could still grow into [OPEN_TAG]. */
    private fun longestTagPrefixAtEnd(): Int {
        val max = minOf(OPEN_TAG.length - 1, buffer.length)
        for (length in max downTo 1) {
            val tail = buffer.substring(buffer.length - length)
            if (OPEN_TAG.startsWith(tail)) return length
        }
        return 0
    }

    private companion object {
        const val OPEN_TAG = "<tool_call>"
        const val CLOSE_TAG = "</tool_call>"
    }
}
