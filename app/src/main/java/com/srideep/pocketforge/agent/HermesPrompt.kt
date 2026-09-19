package com.srideep.pocketforge.agent

/**
 * Prompt scaffolding for the tool-calling loop.
 *
 * The tool list is compact signatures rather than a JSON Schema block, and the reason is
 * latency. The schema form is the Hermes convention, but it costs roughly 1,200 tokens,
 * and prefill on this hardware measures around 18 tok/s — over a minute of work before
 * the model emits a single character, on every turn of the loop. The signature form is
 * about a fifth of that. It also gives a small model far less nested JSON to imitate
 * instead of obey.
 *
 * The block is prepended to the first user turn rather than sent as a system message
 * because MNN applies the model's own chat template per `response()` call and keeps
 * history internally, so one framed turn is all the model sees.
 */
object HermesPrompt {

    private val TOOLS = """
        create_file(path, content)            write a file, complete contents every time
        edit_file(path, target, replacement)  replace the first occurrence of target
        read_file(path)                       return a file's contents
        list_files(directory)                 list the project, directory optional
        start_dev_server(project_path)        serve the project and open the preview
        stop_dev_server()                     stop it
    """.trimIndent()

    private val SYSTEM_PREAMBLE = """
        You are a web developer working on an Android phone. You build small sites with
        plain HTML, CSS and JavaScript. There is no package manager and no network, so
        never use npm, a CDN or a web font — write everything yourself, inline.

        Work by calling tools:

        <tools>
        $TOOLS
        </tools>

        To call one, emit exactly this and nothing else:

        <tool_call>
        {"name": "create_file", "arguments": {"path": "index.html", "content": "<!DOCTYPE html>..."}}
        </tool_call>

        Rules:
        - One tool call per turn. Wait for its result before the next.
        - Never repeat these instructions or the tool list back.
        - Put the whole file in content, escaped as a JSON string.
        - Keep it short: one self-contained file unless asked for more.
        - When the project runs, reply with one sentence and no tool call.
    """.trimIndent()

    /** The opening turn: instructions plus what the user asked for. */
    fun firstTurn(userMessage: String): String = "$SYSTEM_PREAMBLE\n\nTask: $userMessage"

    /** Sent when a turn produced a tool call we could not parse. */
    fun retryAfterMalformed(): String =
        "That tool call was cut off or malformed. Send it again as one complete " +
            "<tool_call> block with valid JSON, and nothing else."

    /** Results of the tools the model just called, fed back for the next step. */
    fun toolResponses(results: List<Pair<ToolCall, ToolResult>>): String =
        results.joinToString("\n") { (call, result) ->
            val status = if (result.ok) "ok" else "error"
            "<tool_response>\n${call.name}: $status\n${result.content}\n</tool_response>"
        }
}
