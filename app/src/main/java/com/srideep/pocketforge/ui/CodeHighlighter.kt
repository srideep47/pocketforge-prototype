package com.srideep.pocketforge.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.srideep.pocketforge.ui.theme.CodeColors

/**
 * A single-pass highlighter for the three languages this studio writes.
 *
 * Not a parser — a scanner over comments, strings, tags and keywords, which is all a
 * phone-sized editor needs and is cheap enough to run on every keystroke. A real grammar
 * would be a dependency and a frame budget for output nobody reads that closely.
 */
object CodeHighlighter {

    private val JS_KEYWORDS = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "of",
        "in", "new", "class", "extends", "import", "export", "from", "default", "try",
        "catch", "finally", "throw", "await", "async", "typeof", "instanceof", "null",
        "undefined", "true", "false", "this", "switch", "case", "break", "continue",
    )

    fun highlight(source: String, fileName: String): AnnotatedString {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "html", "htm", "xml", "svg" -> markup(source)
            "css" -> css(source)
            "js", "mjs", "ts", "json" -> script(source)
            else -> AnnotatedString(source)
        }
    }

    /** HTML: comments, tag names, attribute names, quoted values. */
    private fun markup(source: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("<!--", i) -> {
                    val end = source.indexOf("-->", i).let { if (it < 0) source.length else it + 3 }
                    span(source, i, end, CodeColors.comment)
                    i = end
                }

                source[i] == '<' -> {
                    val end = source.indexOf('>', i).let { if (it < 0) source.length else it + 1 }
                    tag(source.substring(i, end))
                    i = end
                }

                else -> {
                    val next = source.indexOf('<', i).let { if (it < 0) source.length else it }
                    append(source.substring(i, next))
                    i = next
                }
            }
        }
    }

    /** One `<...>` run: the name, then attribute / value pairs. */
    private fun AnnotatedString.Builder.tag(fragment: String) {
        var i = 0
        // "<" plus the element name, including a closing slash or a doctype bang.
        while (i < fragment.length && (fragment[i] == '<' || fragment[i] == '/' || fragment[i] == '!')) i++
        while (i < fragment.length && (fragment[i].isLetterOrDigit() || fragment[i] == '-')) i++
        withStyle(SpanStyle(color = CodeColors.tag)) { append(fragment.substring(0, i)) }

        while (i < fragment.length) {
            val c = fragment[i]
            when {
                c == '"' || c == '\'' -> {
                    val close = fragment.indexOf(c, i + 1).let { if (it < 0) fragment.length else it + 1 }
                    withStyle(SpanStyle(color = CodeColors.string)) { append(fragment.substring(i, close)) }
                    i = close
                }

                c.isLetter() -> {
                    var end = i
                    while (end < fragment.length && (fragment[end].isLetterOrDigit() || fragment[end] == '-')) end++
                    withStyle(SpanStyle(color = CodeColors.attribute)) { append(fragment.substring(i, end)) }
                    i = end
                }

                c == '>' || c == '/' -> {
                    withStyle(SpanStyle(color = CodeColors.tag)) { append(c) }
                    i++
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

    /** CSS: comments, selectors before `{`, quoted values, numbers. */
    private fun css(source: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i).let { if (it < 0) source.length else it + 2 }
                    span(source, i, end, CodeColors.comment)
                    i = end
                }

                source[i] == '"' || source[i] == '\'' -> i = quoted(source, i)

                source[i] == '#' || source[i] == '.' -> {
                    var end = i + 1
                    while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '-' || source[end] == '_')) end++
                    span(source, i, end, CodeColors.keyword)
                    i = end
                }

                source[i].isDigit() -> {
                    var end = i
                    while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '.' || source[end] == '%')) end++
                    span(source, i, end, CodeColors.number)
                    i = end
                }

                else -> {
                    append(source[i])
                    i++
                }
            }
        }
    }

    /** JavaScript and JSON: comments, strings, keywords, numbers. */
    private fun script(source: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    span(source, i, end, CodeColors.comment)
                    i = end
                }

                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i).let { if (it < 0) source.length else it + 2 }
                    span(source, i, end, CodeColors.comment)
                    i = end
                }

                c == '"' || c == '\'' || c == '`' -> i = quoted(source, i)

                c.isLetter() || c == '_' || c == '$' -> {
                    var end = i
                    while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_' || source[end] == '$')) end++
                    val word = source.substring(i, end)
                    if (word in JS_KEYWORDS) {
                        withStyle(SpanStyle(color = CodeColors.keyword)) { append(word) }
                    } else {
                        append(word)
                    }
                    i = end
                }

                c.isDigit() -> {
                    var end = i
                    while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '.')) end++
                    span(source, i, end, CodeColors.number)
                    i = end
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

    /** Consumes a quoted run, honouring backslash escapes; returns the index after it. */
    private fun AnnotatedString.Builder.quoted(source: String, start: Int): Int {
        val quote = source[start]
        var end = start + 1
        while (end < source.length) {
            if (source[end] == '\\') {
                end += 2
                continue
            }
            if (source[end] == quote) {
                end++
                break
            }
            end++
        }
        val safeEnd = minOf(end, source.length)
        span(source, start, safeEnd, CodeColors.string)
        return safeEnd
    }

    private fun AnnotatedString.Builder.span(source: String, start: Int, end: Int, color: Color) {
        withStyle(SpanStyle(color = color)) { append(source.substring(start, minOf(end, source.length))) }
    }
}
