package com.srideep.pocketforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.workspace.WorkspaceEntry

/**
 * The project tree.
 *
 * The workspace hands back a depth-first list, which is already tree order, so the tree
 * is drawn by indenting on path depth and hiding anything under a collapsed folder —
 * there is no second data structure to keep in step with the filesystem.
 */
@Composable
fun FileExplorer(
    files: List<WorkspaceEntry>,
    selectedPath: String?,
    onOpen: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }

    val visible = remember(files, collapsed) { files.filterNot { it.isHiddenBy(collapsed) } }
    val fileCount = remember(files) { files.count { !it.isDirectory } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Project", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (fileCount == 1) "1 file" else "$fileCount files",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { dialog = ExplorerDialog.NewFolder }) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = "New folder",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { dialog = ExplorerDialog.NewFile }) {
                Icon(Icons.Default.NoteAdd, contentDescription = "New file", modifier = Modifier.size(20.dp))
            }
        }

        if (files.isEmpty()) {
            EmptyState(onCreate = { dialog = ExplorerDialog.NewFile })
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.relativePath }) { entry ->
                    EntryRow(
                        entry = entry,
                        selected = entry.relativePath == selectedPath,
                        collapsed = entry.relativePath in collapsed,
                        onClick = {
                            if (entry.isDirectory) {
                                collapsed = if (entry.relativePath in collapsed) {
                                    collapsed - entry.relativePath
                                } else {
                                    collapsed + entry.relativePath
                                }
                            } else {
                                onOpen(entry.relativePath)
                            }
                        },
                        onRename = { dialog = ExplorerDialog.Rename(entry.relativePath) },
                        onDelete = { dialog = ExplorerDialog.ConfirmDelete(entry.relativePath) },
                    )
                }
            }
        }
    }

    when (val open = dialog) {
        null -> Unit

        ExplorerDialog.NewFile -> NameDialog(
            title = "New file",
            placeholder = "about.html",
            confirmLabel = "Create",
            onDismiss = { dialog = null },
            onConfirm = { path ->
                dialog = null
                onCreateFile(path)
            },
        )

        ExplorerDialog.NewFolder -> NameDialog(
            title = "New folder",
            placeholder = "assets",
            confirmLabel = "Create",
            onDismiss = { dialog = null },
            onConfirm = { path ->
                dialog = null
                onCreateFolder(path)
            },
        )

        is ExplorerDialog.Rename -> NameDialog(
            title = "Rename",
            placeholder = open.path,
            initial = open.path,
            confirmLabel = "Rename",
            onDismiss = { dialog = null },
            onConfirm = { path ->
                dialog = null
                if (path != open.path) onRename(open.path, path)
            },
        )

        is ExplorerDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete " + open.path.substringAfterLast('/') + "?") },
            text = { Text(open.path + "\n\nThis cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    onDelete(open.path)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EntryRow(
    entry: WorkspaceEntry,
    selected: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val depth = entry.relativePath.count { it == '/' }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(start = (8 + depth * 16).dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (entry.isDirectory) {
            Icon(
                imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (collapsed) "Expand" else "Collapse",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Box(modifier = Modifier.width(16.dp))
            FileTypeBadge(entry.name)
        }

        Text(
            text = entry.name,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )

        if (!entry.isDirectory) {
            Text(
                text = humanSize(entry.sizeBytes),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** A short extension chip, which reads faster than a column of identical file icons. */
@Composable
private fun FileTypeBadge(name: String) {
    val extension = name.substringAfterLast('.', "").lowercase()
    val tint = when (extension) {
        "html", "htm" -> Color(0xFFE8956B)
        "css" -> Color(0xFF6BA8E8)
        "js", "mjs", "ts" -> Color(0xFFE8CF6B)
        "json" -> Color(0xFF9B8CE8)
        "md", "txt" -> Color(0xFF8C9BA8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = extension.take(4).ifEmpty { "?" },
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier
            .width(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 3.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No files yet", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Ask the agent to build something, or start one yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onCreate, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("  New file")
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                supportingText = { Text("Paths are relative to the project root") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private sealed interface ExplorerDialog {
    data object NewFile : ExplorerDialog
    data object NewFolder : ExplorerDialog
    data class Rename(val path: String) : ExplorerDialog
    data class ConfirmDelete(val path: String) : ExplorerDialog
}

/** True when any ancestor directory of this entry is collapsed. */
private fun WorkspaceEntry.isHiddenBy(collapsed: Set<String>): Boolean {
    if (collapsed.isEmpty()) return false
    var cut = relativePath.lastIndexOf('/')
    while (cut > 0) {
        if (relativePath.substring(0, cut) in collapsed) return true
        cut = relativePath.lastIndexOf('/', cut - 1)
    }
    return false
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> (bytes / 1024).toString() + " KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
