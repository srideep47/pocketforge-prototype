package com.srideep.pocketforge.agent

/**
 * Prompt scaffolding for the local website builder.
 *
 * Sending a whole HTML document as a JSON string is a poor contract for a 2B/4B local model:
 * every quote and newline must be escaped, so one imperfect token invalidates several kilobytes
 * of otherwise-good code. The primary contract is therefore a raw `<site>` artifact. Hermes
 * tool calls remain supported by [ToolCallParser] for model variants that choose them anyway.
 */
object HermesPrompt {

    private val SYSTEM_PREAMBLE = """
        You are PocketForge, a website builder running locally on an Android phone.
        Build polished, functional sites with plain HTML, inline CSS and inline JavaScript.
        There is no package manager and no network: do not use npm, imports, CDNs, remote
        images, web fonts, or external assets.

        For every website creation or change request, return exactly one complete document
        using this envelope:

        <site>
        <!doctype html>
        <html lang="en">...</html>
        </site>

        The content inside <site> is raw HTML, not JSON. Do not escape its quotes or newlines.
        Do not put it in a Markdown fence. Keep the document under 6,000 characters so it can
        finish reliably on-device. Include a responsive viewport, accessible labels, polished
        styling, and working interactions when the request needs them.

        Follow the requested scope exactly. The words "just" and "only" are hard constraints:
        the body may contain only the requested visible content. Do not add welcome text,
        subtitles, descriptions, buttons, paragraphs, navigation, or interactions. For example,
        "just hello world text" means a body with one element whose only text is "Hello World".
        Use only valid CSS values. Every CSS custom property you reference must be declared;
        for a simple page, prefer direct values over custom properties.

        Never explain the code before the <site> block. PocketForge writes it to index.html and
        starts the preview automatically. If the latest message is a <tool_response> saying the
        file was written, reply with one short completion sentence and do not emit another site.
    """.trimIndent()

    fun systemPrompt(): String = SYSTEM_PREAMBLE

    fun retryAfterMalformed(): String =
        "The previous output could not be saved. Return one complete raw HTML document inside " +
            "<site></site>, with no JSON, Markdown fence, explanation, or tool call."

    fun retryIncomplete(hasIndex: Boolean): String = if (hasIndex) {
        "The user's requested change was not applied. Return the complete updated index.html " +
            "inside one raw <site></site> block now."
    } else {
        "index.html does not exist. Return the complete site inside one raw <site></site> block now."
    }

    fun toolResponses(results: List<Pair<ToolCall, ToolResult>>): String =
        results.joinToString("\n") { (call, result) ->
            val status = if (result.ok) "ok" else "error"
            "<tool_response>\n${call.name}: $status\n${result.content}\n</tool_response>"
        }
}

/** Extracts the raw HTML protocol and a fenced-HTML fallback used by some Qwen checkpoints. */
object SiteArtifact {
    private val OPEN = Regex("<site(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val CLOSE = Regex("</site>", RegexOption.IGNORE_CASE)
    private val FENCE = Regex("```(?:html)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    fun extract(raw: String): String? {
        val open = OPEN.find(raw)
        if (open != null) {
            val start = open.range.last + 1
            val close = CLOSE.find(raw, start)
            val candidate = if (close != null) {
                raw.substring(start, close.range.first)
            } else {
                val htmlEnd = raw.indexOf("</html>", start, ignoreCase = true)
                if (htmlEnd < 0) return null else raw.substring(start, htmlEnd + "</html>".length)
            }
            return candidate.trim().takeIf(::looksLikeHtml)
        }
        return FENCE.find(raw)?.groupValues?.get(1)?.trim()?.takeIf(::looksLikeHtml)
    }

    private fun looksLikeHtml(value: String): Boolean =
        value.contains("<html", ignoreCase = true) ||
            (value.contains("<!doctype html", ignoreCase = true) && value.contains("</html>", ignoreCase = true))
}
