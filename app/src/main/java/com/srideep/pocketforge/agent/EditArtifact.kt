package com.srideep.pocketforge.agent

/**
 * Small search-and-replace patches to the current page, the cheap alternative to a `<site>`.
 *
 * Rewriting the whole page for "make the button orange" costs ~2k generated tokens, four to
 * five minutes on the phone; the same change as a patch is a few dozen tokens. The format is
 * raw text between tags, like `<site>`, because escaping code into JSON is where small models
 * fail:
 *
 *     <edit>
 *     <find>exact text from the current page</find>
 *     <replace>what it becomes</replace>
 *     </edit>
 */
object EditArtifact {

    data class Edit(val find: String, val replace: String)

    /** How the patches went: the page after them, or the finds that matched nothing. */
    sealed interface Outcome {
        data class Applied(val html: String, val count: Int) : Outcome
        data class Missed(val finds: List<String>) : Outcome
    }

    private val EDIT = Regex(
        "<edit>\\s*<find>(.*?)</find>\\s*<replace>(.*?)</replace>\\s*</edit>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun extract(raw: String): List<Edit> =
        EDIT.findAll(raw).map { Edit(stripNewlineEdges(it.groupValues[1]), stripNewlineEdges(it.groupValues[2])) }
            .filter { it.find.isNotBlank() }
            .toList()

    /**
     * Applies [edits] in order. A find is matched exactly first, then with each line's leading
     * and trailing whitespace ignored, since models often re-indent what they quote. Nothing is
     * applied unless every find matches: half a change can leave the page broken.
     */
    fun apply(page: String, edits: List<Edit>): Outcome {
        var html = page
        val missed = mutableListOf<String>()
        for (edit in edits) {
            val exact = html.indexOf(edit.find)
            if (exact >= 0) {
                html = html.replaceRange(exact, exact + edit.find.length, edit.replace)
                continue
            }
            val loose = looseMatch(html, edit.find)
            if (loose != null) {
                html = html.replaceRange(loose, edit.replace)
            } else {
                missed += edit.find
            }
        }
        return if (missed.isEmpty()) Outcome.Applied(html, edits.size) else Outcome.Missed(missed)
    }

    /** The range of [page] whose lines equal [find]'s lines once each is trimmed. */
    private fun looseMatch(page: String, find: String): IntRange? {
        val wanted = find.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return null
        val lines = page.lines()
        val offsets = IntArray(lines.size)
        var offset = 0
        for (i in lines.indices) {
            offsets[i] = offset
            offset += lines[i].length + 1
        }
        for (start in lines.indices) {
            if (lines[start].trim() != wanted[0]) continue
            var line = start
            var matched = 0
            while (line < lines.size && matched < wanted.size) {
                val trimmed = lines[line].trim()
                if (trimmed.isEmpty()) { line++; continue }
                if (trimmed != wanted[matched]) break
                matched++
                line++
            }
            if (matched == wanted.size) {
                val end = offsets[line - 1] + lines[line - 1].length
                return offsets[start] until end
            }
        }
        return null
    }

    private fun stripNewlineEdges(value: String): String = value.removePrefix("\n").removeSuffix("\n")
}
