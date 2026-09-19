package com.srideep.pocketforge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.chat.OpenFile
import com.srideep.pocketforge.ui.theme.CodeColors

private val EDITOR_TEXT_SIZE = 13.sp
private val EDITOR_LINE_HEIGHT = 20.sp

/**
 * The code tab: a gutter of line numbers beside a syntax-highlighted editor.
 *
 * Highlighting runs through a [VisualTransformation] so the underlying text the field
 * edits stays plain — colour never changes offsets, which is what keeps the cursor and
 * selection honest.
 */
@Composable
fun EditorPane(
    openFile: OpenFile?,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (openFile == null) {
        EmptyEditor(modifier)
        return
    }

    val scroll = rememberScrollState()
    val lineCount = remember(openFile.content) { openFile.content.count { it == '\n' } + 1 }
    val highlighter = remember(openFile.path) {
        VisualTransformation { text ->
            TransformedText(
                CodeHighlighter.highlight(text.text, openFile.path),
                androidx.compose.ui.text.input.OffsetMapping.Identity,
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        EditorHeader(openFile = openFile, lineCount = lineCount, onSave = onSave)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll),
        ) {
            Gutter(lineCount)

            BasicTextField(
                value = openFile.content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    fontFamily = CodeColors.mono,
                    fontSize = EDITOR_TEXT_SIZE,
                    lineHeight = EDITOR_LINE_HEIGHT,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                visualTransformation = highlighter,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun EditorHeader(openFile: OpenFile, lineCount: Int, onSave: () -> Unit) {
    val directory = openFile.path.substringBeforeLast('/', "")
    val name = openFile.path.substringAfterLast('/')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (directory.isNotEmpty()) {
                    Text(
                        text = "$directory/",
                        fontFamily = CodeColors.mono,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = name,
                    fontFamily = CodeColors.mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AnimatedVisibility(visible = openFile.dirty) {
                    Box(
                        modifier = Modifier
                            .padding(start = 7.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = if (lineCount == 1) "1 line" else "$lineCount lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onSave, enabled = openFile.dirty) {
            Icon(
                imageVector = if (openFile.dirty) Icons.Default.Save else Icons.Default.Check,
                contentDescription = if (openFile.dirty) "Save" else "Saved",
                modifier = Modifier.size(20.dp),
                tint = if (openFile.dirty) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Gutter(lineCount: Int) {
    val digits = lineCount.toString().length
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width((22 + digits * 8).dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Drawn as one string so the gutter shares the editor's line box exactly.
        Text(
            text = AnnotatedString((1..lineCount).joinToString("\n")),
            fontFamily = CodeColors.mono,
            fontSize = EDITOR_TEXT_SIZE,
            lineHeight = EDITOR_LINE_HEIGHT,
            color = CodeColors.gutter,
        )
    }
}

@Composable
private fun EmptyEditor(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Code,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No file open",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Open one from the project drawer, or let the agent write it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
