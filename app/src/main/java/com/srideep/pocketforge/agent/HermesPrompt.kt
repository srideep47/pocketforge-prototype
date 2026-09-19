package com.srideep.pocketforge.agent

/**
 * Prompt scaffolding for Hermes-style tool calling, which is what the Qwen 2.5/3.x
 * instruct checkpoints exported for MNN are tuned on.
 *
 * The tool schema is prepended to the first user turn rather than sent as a separate
 * system message: MNN applies the model's own chat template per `response()` call and
 * keeps history internally, so one framed turn is all the model needs to see.
 */
object HermesPrompt {

    private val TOOL_SCHEMAS = listOf(
        """{"type":"function","function":{"name":"create_file","description":"Create or overwrite a file in the project. Always write the complete file content.","parameters":{"type":"object","properties":{"path":{"type":"string","description":"Path relative to the project root, e.g. index.html"},"content":{"type":"string","description":"Full contents of the file"}},"required":["path","content"]}}}""",
        """{"type":"function","function":{"name":"edit_file","description":"Replace the first occurrence of a snippet in an existing file.","parameters":{"type":"object","properties":{"path":{"type":"string"},"target":{"type":"string","description":"Exact text to find"},"replacement":{"type":"string","description":"Text to put in its place"}},"required":["path","target","replacement"]}}}""",
        """{"type":"function","function":{"name":"read_file","description":"Read a file from the project.","parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}}""",
        """{"type":"function","function":{"name":"list_files","description":"List the files in the project.","parameters":{"type":"object","properties":{"directory":{"type":"string","description":"Optional sub-directory, relative to the project root"}},"required":[]}}}""",
        """{"type":"function","function":{"name":"start_dev_server","description":"Serve the project over HTTP on the device and open it in the preview tab.","parameters":{"type":"object","properties":{"project_path":{"type":"string","description":"Optional sub-directory to serve, relative to the project root"}},"required":[]}}}""",
        """{"type":"function","function":{"name":"stop_dev_server","description":"Stop the running dev server.","parameters":{"type":"object","properties":{},"required":[]}}}""",
    )

    private val SYSTEM_PREAMBLE = """
        You are a web developer working directly on an Android phone. You build small
        websites with plain HTML, CSS and JavaScript. There is no package manager and no
        network, so never reach for npm, a CDN or an external font — write the code yourself.

        You are a function calling AI model. You are provided with function signatures
        within <tools></tools> XML tags. Call one or more of them to do the work; do not
        guess what a file contains, read it. Here are the available tools:
        <tools>
        ${TOOL_SCHEMAS.joinToString("\n")}
        </tools>

        For each function call, return a JSON object with the function name and arguments
        within <tool_call></tool_call> XML tags:
        <tool_call>
        {"name": <function-name>, "arguments": <args-dict>}
        </tool_call>

        Work in small steps: create or edit one file per tool call, start the dev server
        once the project is runnable, then reply with a short plain-text summary and no
        further tool calls.
    """.trimIndent()

    /** The opening turn: tool schemas plus what the user asked for. */
    fun firstTurn(userMessage: String): String = "$SYSTEM_PREAMBLE\n\n$userMessage"

    /** Results of the tools the model just called, fed back for the next step. */
    fun toolResponses(results: List<Pair<ToolCall, ToolResult>>): String =
        results.joinToString("\n") { (call, result) ->
            val status = if (result.ok) "ok" else "error"
            "<tool_response>\n{\"name\": \"${call.name}\", \"status\": \"$status\"}\n${result.content}\n</tool_response>"
        }
}
