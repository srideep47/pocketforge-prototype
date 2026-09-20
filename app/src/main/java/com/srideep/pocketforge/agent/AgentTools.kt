package com.srideep.pocketforge.agent

import com.srideep.pocketforge.runtime.node.DevServerClient
import com.srideep.pocketforge.workspace.Workspace
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
        when (canonicalName(call.name)) {
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
        val path = normalizeWebPath(args.requireString("path") ?: return missing("path"))
        val content = args.optString("content", "")
        workspace.write(path, content)
        return ToolResult(true, "wrote $path (${content.length} chars)")
    }

    private fun editFile(args: JSONObject): ToolResult {
        val path = normalizeWebPath(args.requireString("path") ?: return missing("path"))
        val target = args.requireString("target") ?: return missing("target")
        val replacement = args.optString("replacement", "")
        workspace.edit(path, target, replacement)
        return ToolResult(true, "edited $path")
    }

    private fun readFile(args: JSONObject): ToolResult {
        val path = normalizeWebPath(args.requireString("path") ?: return missing("path"))
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
        val requested = args.optString("project_path", "").ifBlank { "" }
        // Models commonly pass "index.html" even though the server expects a directory.
        val relative = if (requested.substringAfterLast('/').contains('.')) {
            requested.substringBeforeLast('/', "")
        } else {
            requested
        }
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

    /** Starts the live preview as a host guarantee, not another behavior the model must learn. */
    suspend fun ensurePreview(): ToolResult? {
        if (!workspace.exists("index.html")) return null
        val current = devServer.state.value
        if (current.running && current.url != null) {
            return ToolResult(true, "dev server running at ${current.url}")
        }
        val state = devServer.start(workspace.root)
        return if (state.running) {
            ToolResult(true, "dev server running at ${state.url}")
        } else {
            ToolResult(false, state.error ?: "dev server did not start")
        }
    }

    fun hasEntryPage(): Boolean = workspace.exists("index.html")

    /**
     * Small models reach for the obvious synonym rather than the name in the prompt —
     * write_file for create_file was the most common. Accepting the synonym costs nothing
     * and turns a failed turn into a working one.
     */
    private fun canonicalName(name: String): String = when (name.lowercase()) {
        "write_file", "file_create", "new_file", "save_file", "create" -> "create_file"
        "replace_in_file", "update_file", "modify_file", "edit" -> "edit_file"
        "open_file", "cat", "read" -> "read_file"
        "ls", "list", "list_directory" -> "list_files"
        "run_dev_server", "serve", "start_server" -> "start_dev_server"
        "stop_server" -> "stop_dev_server"
        else -> name.lowercase()
    }

    private fun JSONObject.requireString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() }

    private fun normalizeWebPath(path: String): String {
        val clean = path.trim()
        val leaf = clean.substringAfterLast('/')
        if (!leaf.equals("index.hmtl", ignoreCase = true) &&
            !leaf.equals("index.htm", ignoreCase = true)
        ) return clean
        val parent = clean.substringBeforeLast('/', "")
        return if (parent.isBlank()) "index.html" else "$parent/index.html"
    }
}
