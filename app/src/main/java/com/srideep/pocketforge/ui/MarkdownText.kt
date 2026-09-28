package com.srideep.pocketforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.ui.theme.CodeColors

/**
 * Lightweight Markdown renderer for chat: fenced code blocks with syntax highlighting,
 * headings, bullets, and inline `code`, **bold**, and *italic*.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { splitBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val ext = block.language.lowercase().ifBlank { "html" }
    val highlighted = remember(block.code, ext) {
        CodeHighlighter.highlight(block.code, "snippet.$ext")
    }
    val lineCount = remember(block.code) { block.code.count { it == '\n' } + 1 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ext.uppercase(),
                fontFamily = CodeColors.mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "$lineCount lines",
                style = CodeColors.tabularMonoStyle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = highlighted,
            fontFamily = CodeColors.mono,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
}

private fun splitBlocks(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var language = ""

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MarkdownBlock.Paragraph(paragraph.toString().trimEnd())
        paragraph.setLength(0)
    }

    source.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(language, code.toString().trimEnd())
                code.setLength(0)
                inCode = false
            } else {
                flushParagraph()
                language = line.trim().removePrefix("```").trim()
                inCode = true
            }
            return@forEach
        }
        if (inCode) {
            code.appendLine(line)
        } else {
            paragraph.appendLine(line)
        }
    }

    if (inCode && code.isNotBlank()) blocks += MarkdownBlock.Code(language, code.toString().trimEnd())
    flushParagraph()
    return blocks
}

private fun inlineMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    source.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')

        var line = rawLine
        var headingLevel = 0
        while (line.startsWith("#")) {
            headingLevel++
            line = line.removePrefix("#")
        }
        line = if (headingLevel > 0) line.trimStart() else line
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            line = line.replaceFirst(Regex("""^(\s*)[-*]\s"""), "$1• ")
        }

        val base = when {
            headingLevel == 1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)
            headingLevel > 1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            else -> SpanStyle()
        }

        withStyle(base) { appendInline(line) }
    }
}

private val INLINE = Regex("""(\*\*(.+?)\*\*)|(\*(.+?)\*)|(`(.+?)`)""")

private fun AnnotatedString.Builder.appendInline(line: String) {
    var cursor = 0
    INLINE.findAll(line).forEach { match ->
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        when {
            match.groupValues[2].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }

            match.groupValues[4].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[4]) }

            match.groupValues[6].isNotEmpty() ->
                withStyle(
                    SpanStyle(
                        fontFamily = CodeColors.mono,
                        fontWeight = FontWeight.Medium,
                        color = CodeColors.tag,
                    ),
                ) { append(match.groupValues[6]) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}
