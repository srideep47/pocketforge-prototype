package com.srideep.pocketforge.agent

import org.json.JSONArray
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
class ToolCallParser(private val knownTools: Set<String> = DEFAULT_TOOLS) {

    private val buffer = StringBuilder()
    private var insideCall = false

    /** Everything released as prose this turn, and whether any call was parsed. */
    private val releasedText = StringBuilder()
    private var sawCall = false

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
                        if (text.isNotEmpty()) {
                            releasedText.append(text)
                            events += AgentEvent.Text(text)
                        }
                    }
                    break
                }
                if (start > 0) {
                    val text = buffer.substring(0, start)
                    releasedText.append(text)
                    events += AgentEvent.Text(text)
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
            if (insideCall) {
                // The model stopped before its closing tag, which small models do often.
                // The body is usually complete bar a brace, so try to parse it anyway
                // rather than throwing away a turn that took a minute to generate.
                val event = parseCall(leftover)
                if (event is AgentEvent.Call) sawCall = true
                events += event
            } else {
                releasedText.append(leftover)
                events += AgentEvent.Text(leftover)
            }
        }
        insideCall = false

        // Small models also drop the tags entirely and emit the bare object. Prose is
        // streamed out as it arrives, so this can only be judged once the turn is over
        // and only matters when nothing else this turn parsed as a call.
        if (!sawCall) {
            bareCall(releasedText.toString())?.let { events += it }
        }
        return events
    }

    /**
     * Looks for an untagged `{"name": ..., "arguments": ...}` naming a tool we know.
     * Requiring a known name is what keeps this from firing on prose about JSON.
     */
    private fun bareCall(text: String): AgentEvent? {
        val start = text.indexOf('{')
        if (start < 0 || !text.contains("\"name\"")) return null
        val parsed = parseCall(text.substring(start))
        return (parsed as? AgentEvent.Call)?.takeIf { it.call.name in knownTools }
    }

    fun reset() {
        buffer.setLength(0)
        releasedText.setLength(0)
        insideCall = false
        sawCall = false
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

            val normalized = normalizeCall(parsed)
                ?: return AgentEvent.Malformed(body, "tool call has no name")
            return AgentEvent.Call(ToolCall(normalized.first, normalized.second, body))
        }
        // Every strict parse failed. Before giving up on a turn that took a minute to
        // generate, try to lift the call out by hand: the content is usually correct and
        // the fault is one stray escape in a key, which JSON rejects wholesale.
        salvage(trimmed)?.let { return it }
        return AgentEvent.Malformed(body, lastError)
    }

    /**
     * Pulls a tool call out of not-quite-JSON.
     *
     * Observed on device: {"name": "write_file", "arguments": {"path\": "index.html",
     * "content": "..."}} — a backslash before a key's closing quote. The HTML in `content`
     * was perfect. Strict parsing throws all of it away, so this reads the name and the
     * known argument keys directly and unescapes their values.
     */
    private fun salvage(text: String): AgentEvent? {
        val name = NAME.find(text)?.groupValues?.get(1) ?: return null
        if (name !in knownTools) return null

        val arguments = JSONObject()
        for (key in ARGUMENT_KEYS) {
            stringValueOf(text, key)?.let { arguments.put(key, it) }
        }
        return AgentEvent.Call(ToolCall(name, arguments, text))
    }

    /**
     * Accepts the tool dialects emitted by the bundled Qwen exports. The ordinary Hermes
     * shape is a flat arguments object. Qwen 3.5 also occasionally emits a `call_tool`
     * wrapper or a list of `{argument_name, argument_value}` records even when shown the
     * canonical schema. Normalize all of them before the executor sees the call.
     */
    private fun normalizeCall(parsed: JSONObject): Pair<String, JSONObject>? {
        var name = parsed.optString("name")
            .ifBlank { parsed.optString("tool_name") }
            .ifBlank { parsed.optString("function") }
        var rawArguments: Any? = parsed.opt("arguments") ?: parsed.opt("parameters")

        if (name == "call_tool" || name == "tool_call") {
            val wrapper = rawArguments as? JSONObject ?: parsed
            name = wrapper.optString("tool_name")
                .ifBlank { wrapper.optString("name") }
                .ifBlank { wrapper.optString("function") }
            rawArguments = wrapper.opt("arguments")
                ?: wrapper.opt("parameters")
                ?: wrapper.opt("tool_arguments")
        }
        if (name.isBlank()) return null
        return name to normalizeArguments(rawArguments)
    }

    private fun normalizeArguments(raw: Any?): JSONObject = when (raw) {
        is JSONObject -> {
            val argumentName = raw.optString("argument_name")
                .ifBlank { raw.optString("name").takeIf { raw.has("argument_value") } ?: "" }
            JSONObject().also { normalized ->
                raw.keys().forEach { key ->
                    if (key !in ARGUMENT_META_KEYS) {
                        normalized.put(key, unwrapArgumentValue(raw.opt(key)))
                    }
                }
                if (argumentName.isNotBlank()) normalized.put(
                    argumentName,
                    unwrapArgumentValue(raw.opt("argument_value") ?: raw.opt("value") ?: ""),
                )
            }
        }
        is JSONArray -> JSONObject().also { normalized ->
            for (index in 0 until raw.length()) {
                val item = raw.optJSONObject(index) ?: continue
                val key = item.optString("argument_name")
                    .ifBlank { item.optString("name") }
                    .ifBlank { item.optString("key") }
                if (key.isNotBlank()) {
                    normalized.put(
                        key,
                        unwrapArgumentValue(
                            item.opt("argument_value") ?: item.opt("value") ?: item.opt("content") ?: "",
                        ),
                    )
                }
            }
        }
        is String -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        else -> JSONObject()
    }

    private fun unwrapArgumentValue(value: Any?): Any? = when (value) {
        is JSONObject -> value.opt("argument_value")
            ?: value.opt("value")
            ?: value.opt("content")
            ?: value
        else -> value
    }

    /**
     * Reads one `"key": "value"` where value may itself contain escaped quotes. Scans to
     * the closing quote honouring backslash escapes, rather than trusting a regex not to
     * stop at the first `\"` inside a file's contents.
     */
    private fun stringValueOf(text: String, key: String): String? {
        val marker = Regex(QUOTE + key + OPTIONAL_SLASH + QUOTE + COLON + QUOTE).find(text)
            ?: return null
        var i = marker.range.last + 1
        val out = StringBuilder()
        while (i < text.length) {
            val c = text[i]
            if (c == BACKSLASH && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> Unit
                    '"' -> out.append('"')
                    BACKSLASH -> out.append(BACKSLASH)
                    else -> out.append(next)
                }
                i += 2
                continue
            }
            if (c == '"') break
            out.append(c)
            i++
        }
        return out.toString().takeIf { it.isNotEmpty() }
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

        // Regex fragments, spelled out because the pattern needs both a literal quote and
        // an optional literal backslash — the stray escape small models leave before a
        // key's closing quote, which is what makes the object unparseable.
        const val QUOTE = "\""
        const val OPTIONAL_SLASH = """\\?"""
        const val COLON = """\s*:\s*"""
        const val BACKSLASH = '\\'

        val NAME = Regex(QUOTE + "name" + OPTIONAL_SLASH + QUOTE + COLON + QUOTE + "([A-Za-z_]+)" + QUOTE)
        val ARGUMENT_KEYS = listOf(
            "path",
            "content",
            "target",
            "replacement",
            "directory",
            "project_path",
        )
        val ARGUMENT_META_KEYS = setOf(
            "argument_name", "argument_value", "name", "key", "value", "type",
        )

        // The synonyms are here too: AgentTools maps them onto the real tools, so the
        // parser has to recognise them or salvage would reject a call it could run.
        val DEFAULT_TOOLS = setOf(
            "create_file", "write_file", "file_create", "new_file", "save_file",
            "edit_file", "replace_in_file", "update_file", "modify_file",
            "read_file", "open_file",
            "list_files", "list_directory",
            "start_dev_server", "run_dev_server", "start_server",
            "stop_dev_server", "stop_server",
        )
    }
}
