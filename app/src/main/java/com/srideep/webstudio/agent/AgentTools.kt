package com.srideep.webstudio.agent

import com.srideep.webstudio.runtime.node.DevServerClient
import com.srideep.webstudio.workspace.Workspace
import org.json.JSONObject

/** Outcome of running a tool, rendered back to the model as a `<tool_response>`. */
data class ToolResult(val ok: Boolean, val content: String)

/**
 * The tools the coding agent can call.
 *
 * Every argument comes from the model, so paths go through [Workspace.resolve] and
 * failures come back as ordinary results rather than exceptions — a wrong path should
 * let the model correct itself on the next turn, not end the run.
 */
class AgentTools(
    private val workspace: Workspace,
    private val devServer: DevServerClient,
) {

    suspend fun execute(call: ToolCall): ToolResult = try {
        when (call.name) {
            "create_file" -> createFile(call.arguments)
            "edit_file" -> editFile(call.arguments)
            "read_file" -> readFile(call.arguments)
            "list_files" -> listFiles(call.arguments)
            "start_dev_server" -> startDevServer(call.arguments)
            "stop_dev_server" -> stopDevServer()
            else -> ToolResult(false, "unknown tool: ${call.name}")
        }
    } catch (e: Exception) {
        ToolResult(false, "${call.name} failed: ${e.message}")
    }

    private fun createFile(args: JSONObject): ToolResult {
        val path = args.requireString("path") ?: return missing("path")
        val content = args.optString("content", "")
        workspace.write(path, content)
        return ToolResult(true, "wrote $path (${content.length} chars)")
    }

    private fun editFile(args: JSONObject): ToolResult {
        val path = args.requireString("path") ?: return missing("path")
        val target = args.requireString("target") ?: return missing("target")
        val replacement = args.optString("replacement", "")
        workspace.edit(path, target, replacement)
        return ToolResult(true, "edited $path")
    }

    private fun readFile(args: JSONObject): ToolResult {
        val path = args.requireString("path") ?: return missing("path")
        return ToolResult(true, workspace.read(path))
    }

    private fun listFiles(args: JSONObject): ToolResult {
        val directory = args.optString("directory", "").ifBlank { "" }
        val entries = workspace.listRecursively(directory)
        if (entries.isEmpty()) return ToolResult(true, "(empty)")
        val listing = entries.joinToString("\n") { entry ->
            if (entry.isDirectory) "${entry.relativePath}/" else "${entry.relativePath} (${entry.sizeBytes}b)"
        }
        return ToolResult(true, listing)
    }

    private suspend fun startDevServer(args: JSONObject): ToolResult {
        // project_path is relative to the workspace; the default is the workspace itself.
        val relative = args.optString("project_path", "").ifBlank { "" }
        val directory = workspace.resolve(relative)
        val state = devServer.start(directory)
        return if (state.running) {
            ToolResult(true, "dev server running at ${state.url}")
        } else {
            ToolResult(false, state.error ?: "dev server did not start")
        }
    }

    private fun stopDevServer(): ToolResult {
        devServer.stop()
        return ToolResult(true, "dev server stopped")
    }

    private fun missing(name: String) = ToolResult(false, "missing required argument: $name")

    private fun JSONObject.requireString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() }
}
