package com.srideep.pocketforge.workspace

import java.io.File
import java.io.IOException

/** A file inside the workspace, as the explorer and the agent's `list_files` see it. */
data class WorkspaceEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

/**
 * Everything the agent and the IDE are allowed to touch: one directory per project under
 * the app's private storage.
 *
 * All paths crossing the API are relative to [root] and resolved through [resolve], which
 * refuses anything that escapes it — the model writes these paths, so they are untrusted.
 */
class Workspace(val root: File) {

    init {
        root.mkdirs()
    }

    private val canonicalRoot: String = root.canonicalPath

    fun resolve(relativePath: String): File {
        val cleaned = relativePath.trim().removePrefix("./").trimStart('/')
        val candidate = File(root, cleaned)
        val canonical = candidate.canonicalPath
        if (canonical != canonicalRoot && !canonical.startsWith(canonicalRoot + File.separator)) {
            throw IOException("path escapes the project directory: $relativePath")
        }
        return candidate
    }

    fun relativePathOf(file: File): String =
        file.canonicalPath.removePrefix(canonicalRoot).trimStart(File.separatorChar)

    fun list(relativeDir: String = ""): List<WorkspaceEntry> {
        val dir = resolve(relativeDir)
        if (!dir.isDirectory) throw IOException("not a directory: $relativeDir")
        return dir.listFiles().orEmpty()
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                WorkspaceEntry(
                    name = file.name,
                    relativePath = relativePathOf(file),
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                )
            }
    }

    /** Depth-first listing used for the agent's view of the project. */
    fun listRecursively(relativeDir: String = "", maxEntries: Int = 200): List<WorkspaceEntry> {
        val collected = mutableListOf<WorkspaceEntry>()
        fun walk(dir: String) {
            if (collected.size >= maxEntries) return
            for (entry in list(dir)) {
                if (entry.name == "node_modules" || entry.name.startsWith(".")) continue
                collected += entry
                if (entry.isDirectory) walk(entry.relativePath)
                if (collected.size >= maxEntries) return
            }
        }
        walk(relativeDir)
        return collected
    }

    fun read(relativePath: String): String {
        val file = resolve(relativePath)
        if (!file.isFile) throw IOException("no such file: $relativePath")
        return file.readText()
    }

    fun write(relativePath: String, content: String): File {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    /** Replaces the first occurrence of [target]; fails loudly when it is not there. */
    fun edit(relativePath: String, target: String, replacement: String): File {
        val file = resolve(relativePath)
        if (!file.isFile) throw IOException("no such file: $relativePath")
        val original = file.readText()
        if (!original.contains(target)) {
            throw IOException("target text not found in $relativePath")
        }
        file.writeText(original.replaceFirst(target, replacement))
        return file
    }

    fun delete(relativePath: String): Boolean {
        val file = resolve(relativePath)
        if (file.canonicalPath == canonicalRoot) throw IOException("refusing to delete the project root")
        return file.deleteRecursively()
    }

    fun createDirectory(relativePath: String): File = resolve(relativePath).apply { mkdirs() }

    /**
     * Renames or moves an entry. Both ends go through [resolve], so a destination that
     * climbs out of the project is refused the same way a read would be.
     */
    fun rename(fromPath: String, toPath: String): File {
        val source = resolve(fromPath)
        if (!source.exists()) throw IOException("no such file: $fromPath")
        val destination = resolve(toPath)
        if (destination.exists()) throw IOException("already exists: $toPath")
        destination.parentFile?.mkdirs()
        if (!source.renameTo(destination)) throw IOException("could not rename $fromPath")
        return destination
    }

    fun exists(relativePath: String): Boolean = resolve(relativePath).exists()
}
