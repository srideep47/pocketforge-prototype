package com.srideep.pocketforge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.chat.OpenFile
import com.srideep.pocketforge.ui.theme.CodeColors
import com.srideep.pocketforge.workspace.WorkspaceEntry

private val EDITOR_TEXT_SIZE = 13.sp
private val EDITOR_LINE_HEIGHT = 20.sp

/**
 * The code tab: horizontal file tabs bar, header bar, and a line-number gutter beside
 * a syntax-highlighted code editor.
 */
@Composable
fun EditorPane(
    openFile: OpenFile?,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    files: List<WorkspaceEntry> = emptyList(),
    onSelectFile: (String) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val fileEntries = remember(files) { files.filter { !it.isDirectory } }

    Column(modifier = modifier.fillMaxSize()) {
        FileTabsBar(
            fileEntries = fileEntries,
            openFile = openFile,
            onSelectFile = onSelectFile,
            onOpenDrawer = onOpenDrawer,
        )

        if (openFile == null) {
            EmptyEditor(
                hasProjectFiles = fileEntries.isNotEmpty(),
                firstFilePath = fileEntries.firstOrNull()?.relativePath,
                onSelectFile = onSelectFile,
                onOpenDrawer = onOpenDrawer,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        val scroll = rememberScrollState()
        val lineCount = remember(openFile.content) { openFile.content.count { it == '\n' } + 1 }
        val highlighter = remember(openFile.path) {
            VisualTransformation { text ->
                TransformedText(
                    CodeHighlighter.highlight(text.text, openFile.path),
                    OffsetMapping.Identity,
                )
            }
        }

        EditorHeader(openFile = openFile, lineCount = lineCount, onSave = onSave)

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
private fun FileTabsBar(
    fileEntries: List<WorkspaceEntry>,
    openFile: OpenFile?,
    onSelectFile: (String) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val tabPaths = remember(fileEntries, openFile?.path) {
        val paths = fileEntries.map { it.relativePath }.toMutableList()
        if (openFile != null && openFile.path !in paths) {
            paths.add(0, openFile.path)
        }
        paths
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Open file explorer",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            if (tabPaths.isEmpty()) {
                Text(
                    text = "No files in workspace",
                    fontFamily = CodeColors.mono,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabPaths.forEach { path ->
                        val active = path == openFile?.path
                        val isDirty = active && openFile?.dirty == true
                        val fileName = path.substringAfterLast('/')
                        val extColor = extensionAccent(fileName)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
                                    },
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onSelectFile(path) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(extColor),
                            )
                            Text(
                                text = fileName,
                                fontFamily = CodeColors.mono,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                            if (isDirty) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun EditorHeader(openFile: OpenFile, lineCount: Int, onSave: () -> Unit) {
    val directory = openFile.path.substringBeforeLast('/', "")
    val name = openFile.path.substringAfterLast('/')

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (directory.isNotEmpty()) {
                        Text(
                            text = "$directory/",
                            fontFamily = CodeColors.mono,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = name,
                        fontFamily = CodeColors.mono,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedVisibility(visible = openFile.dirty) {
                        Text(
                            text = " · unsaved",
                            fontFamily = CodeColors.mono,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = "$lineCount lines · ${openFile.content.length} chars",
                    style = CodeColors.tabularMonoStyle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onSave,
                enabled = openFile.dirty,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (openFile.dirty) Icons.Default.Save else Icons.Default.Check,
                    contentDescription = if (openFile.dirty) "Save" else "Saved",
                    modifier = Modifier.size(20.dp),
                    tint = if (openFile.dirty) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun Gutter(lineCount: Int) {
    val digits = lineCount.toString().length
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width((24 + digits * 8).dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = AnnotatedString((1..lineCount).joinToString("\n")),
            style = CodeColors.tabularMonoStyle,
            fontSize = EDITOR_TEXT_SIZE,
            lineHeight = EDITOR_LINE_HEIGHT,
            color = CodeColors.gutter,
        )
    }
}

@Composable
private fun EmptyEditor(
    hasProjectFiles: Boolean,
    firstFilePath: String?,
    onSelectFile: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier,
) {
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
            modifier = Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "No file open",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = if (hasProjectFiles) {
                "Tap a file tab above or open the project explorer."
            } else {
                "Open one from the project drawer, or let the agent write index.html."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (firstFilePath != null) {
            Button(onClick = { onSelectFile(firstFilePath) }) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open $firstFilePath")
            }
        } else {
            Button(onClick = onOpenDrawer) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open project explorer")
            }
        }
    }
}

private fun extensionAccent(fileName: String): Color {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> Color(0xFFE8956B)
        "css" -> Color(0xFF38BDF8)
        "js", "mjs", "ts" -> Color(0xFFFCD34D)
        "json" -> Color(0xFFA78BFA)
        else -> Color(0xFF94A3B8)
    }
}
