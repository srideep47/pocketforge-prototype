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

        For a new website, return exactly one complete document using this envelope (a change
        to an existing page may use <edit> blocks instead, as its request explains):

        <site>
        <!doctype html>
        <html lang="en">...</html>
        </site>

        The content inside <site> is raw HTML, not JSON. Do not escape its quotes or newlines.
        Do not put it in a Markdown fence. Keep the document under 4,000 characters: it is
        written one token at a time on a phone, and every extra line is seconds of waiting.
        Write compact code: no comments, one <style> and one <script>, shared classes instead
        of styling each element separately, and a CSS grid for any grid of buttons. Include a
        responsive viewport, accessible labels, polished styling, and working interactions when
        the request needs them.

        Follow the requested scope exactly. The words "just" and "only" are hard constraints:
        the body may contain only the requested visible content. Do not add welcome text,
        subtitles, descriptions, buttons, paragraphs, navigation, or interactions. For example,
        "just hello world text" means a body with one element whose only text is "Hello World".
        Use only valid CSS values. Every CSS custom property you reference must be declared;
        for a simple page, prefer direct values over custom properties.

        Interactions must really work, not just look right. The values your script compares
        against must be exactly the ones your controls send: a key labelled × or ÷ either sends
        "*" and "/" or the script handles "×" and "÷". Whatever the user is typing or has
        produced must be visible on screen at every step, and a display starts with a sensible
        value (a calculator shows 0), never an empty box. Before writing, trace one real use
        through your code; for a calculator, 7 × 8 = must show 7, then 7×8, then 56.
        Give the document a short <title> naming the app, such as "Calculator": it becomes the
        app's name on the home screen.

        When the user gives no style, make it look like a finished product, not a template:
        the system-ui font stack, a centred column no wider than 960px with 20px side padding,
        one accent colour with a darker hover shade, spacing in multiples of 8px, 12px corner
        radius, soft shadows, tap targets at least 44px tall, clear contrast, and short
        transitions on interactive elements.

        Never explain the code before the <site> block. PocketForge writes it to index.html and
        starts the preview automatically. If the latest message is a <tool_response> saying the
        file was written, reply with one short completion sentence and do not emit another site.
    """.trimIndent()

    fun systemPrompt(): String = SYSTEM_PREAMBLE

    /**
     * The user turn the model actually sees.
     *
     * Each run starts a fresh conversation, so an edit request on its own reaches a model that
     * has never seen the page: it rebuilds from scratch and "make the button orange" loses
     * everything else. The current document therefore travels with every change request.
     *
     * [imageTag] is MNN's inline image reference (`<img>...</img>`), which its multimodal
     * tokenizer swaps for vision embeddings before prefill.
     */
    fun userTurn(
        request: String,
        currentPage: String?,
        imageTag: String?,
        sketchDescription: String? = null,
    ): String = buildString {
        if (sketchDescription != null) {
            append("The user photographed a hand-drawn sketch of the app. A vision model read it:\n<sketch>\n")
            append(sketchDescription)
            append(
                "\n</sketch>\nBuild this as a working page. Keep every heading, label, input and button " +
                    "the sketch lists, in the same order and arrangement, and make every control work.\n\n",
            )
        }
        if (imageTag != null) {
            append(imageTag)
            append('\n')
            append(
                "The image is a sketch, wireframe or screenshot of the interface the user wants. " +
                    "Rebuild its layout, sections, labels and controls as a working page. Read any " +
                    "handwritten text in it, and make every control it shows actually work.\n\n",
            )
        }
        if (currentPage != null) {
            append("This is the current index.html:\n<current>\n")
            append(currentPage)
            append("\n</current>\n\n")
            append(
                "Apply the change below. If it touches only a few places, reply with one block per " +
                    "place and nothing else:\n<edit>\n<find>lines copied exactly from the current " +
                    "page</find>\n<replace>the new lines</replace>\n</edit>\nEach find must be copied " +
                    "character for character and appear only once in the page. If most of the page " +
                    "changes, return the complete updated document inside one <site></site> block " +
                    "instead. Keep everything the change does not mention exactly as it is." +
                    "\n\nChange: ",
            )
        }
        append(request)
    }

    /**
     * What the image-bearing turn is replaced with once the model has answered it. The vision
     * encoder would otherwise run again on every later round of the same task.
     */
    fun withoutImage(turn: String, imageTag: String): String =
        turn.replace(imageTag, "[photo attached above]")

    fun retryAfterMalformed(): String =
        "The previous output could not be saved. Return one complete raw HTML document inside " +
            "<site></site>, with no JSON, Markdown fence, explanation, or tool call."

    fun retryAfterMissedEdits(finds: List<String>): String =
        "These <find> texts are not in the current page, so nothing was changed:\n" +
            finds.joinToString("\n") { "- " + it.lines().first().take(120) } +
            "\nReturn the complete updated document inside one raw <site></site> block now."

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
    private val LINK = Regex("<link\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_SRC = Regex(
        "<script\\b(?=[^>]*\\bsrc\\s*=)[^>]*>.*?</script\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val VIEWPORT = Regex(
        "<meta\\b(?=[^>]*\\bname\\s*=\\s*['\"]viewport['\"])[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HEAD_END = Regex("</head\\s*>", RegexOption.IGNORE_CASE)

    /** A `<site>` was opened and the reply stopped before the document ended. */
    fun isCutOff(raw: CharSequence): Boolean {
        val open = OPEN.find(raw) ?: return false
        val start = open.range.last + 1
        return CLOSE.find(raw, start) == null && !raw.substring(start).contains("</html>", ignoreCase = true)
    }

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
            return candidate.trim().takeIf(::looksLikeHtml)?.let(::makeSelfContained)
        }
        return FENCE.find(raw)?.groupValues?.get(1)?.trim()?.takeIf(::looksLikeHtml)
            ?.let(::makeSelfContained)
    }

    /** The preview is offline and an artifact owns one file, so linked dependencies cannot work. */
    private fun makeSelfContained(value: String): String {
        var html = LINK.replace(value, "")
        html = SCRIPT_SRC.replace(html, "")
        if (!VIEWPORT.containsMatchIn(html)) {
            html = HEAD_END.replaceFirst(
                html,
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n</head>",
            )
        }
        return html.trim()
    }

    private fun looksLikeHtml(value: String): Boolean =
        value.contains("<html", ignoreCase = true) ||
            (value.contains("<!doctype html", ignoreCase = true) && value.contains("</html>", ignoreCase = true))
}
