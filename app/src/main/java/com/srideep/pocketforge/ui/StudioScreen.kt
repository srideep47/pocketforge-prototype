package com.srideep.pocketforge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srideep.pocketforge.chat.ModelStatus
import com.srideep.pocketforge.chat.StudioUiState
import kotlinx.coroutines.launch

private enum class StudioTab(val label: String) {
    CHAT("Chat"),
    CODE("Code"),
    PREVIEW("Preview"),
}

/** Callbacks the screen needs, grouped so the composable signature stays readable. */
class StudioActions(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onStop: () -> Unit,
    val onMic: () -> Unit,
    val onOpenFile: (String) -> Unit,
    val onEditorChange: (String) -> Unit,
    val onSaveFile: () -> Unit,
    val onCreateFile: (String) -> Unit,
    val onDeleteFile: (String) -> Unit,
    val onRefreshFiles: () -> Unit,
    val onStartServer: () -> Unit,
    val onStopServer: () -> Unit,
    val onLoadModel: (String) -> Unit,
    val onRefreshModels: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(state: StudioUiState, actions: StudioActions) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var modelMenuOpen by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FileExplorer(
                    files = state.files,
                    selectedPath = state.openFile?.path,
                    onOpen = { path ->
                        actions.onOpenFile(path)
                        tab = StudioTab.CODE.ordinal
                        scope.launch { drawerState.close() }
                    },
                    onCreate = actions.onCreateFile,
                    onDelete = actions.onDeleteFile,
                    onRefresh = actions.onRefreshFiles,
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("PocketForge", style = MaterialTheme.typography.titleMedium)
                            if (state.status.isNotBlank()) {
                                Text(
                                    text = state.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Folder, contentDescription = "Files")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            actions.onRefreshModels()
                            modelMenuOpen = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Model",
                                tint = when (state.modelStatus) {
                                    ModelStatus.READY -> MaterialTheme.colorScheme.primary
                                    ModelStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false },
                        ) {
                            if (state.availableModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No models found") },
                                    onClick = { modelMenuOpen = false },
                                )
                            }
                            state.availableModels.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        modelMenuOpen = false
                                        actions.onLoadModel(name)
                                    },
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
                    StudioTab.entries.forEachIndexed { index, entry ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(entry.label) },
                            icon = {
                                Icon(
                                    imageVector = when (entry) {
                                        StudioTab.CHAT -> Icons.AutoMirrored.Filled.Chat
                                        StudioTab.CODE -> Icons.Default.Code
                                        StudioTab.PREVIEW -> Icons.Default.Public
                                    },
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }

                when (StudioTab.entries[tab]) {
                    StudioTab.CHAT -> ChatPane(
                        state = state,
                        onInputChange = actions.onInputChange,
                        onSend = actions.onSend,
                        onStop = actions.onStop,
                        onMic = actions.onMic,
                        modifier = Modifier.weight(1f),
                    )

                    StudioTab.CODE -> EditorPane(
                        openFile = state.openFile,
                        onContentChange = actions.onEditorChange,
                        onSave = actions.onSaveFile,
                        modifier = Modifier.weight(1f),
                    )

                    StudioTab.PREVIEW -> PreviewPane(
                        url = state.previewUrl,
                        serverRunning = state.devServerRunning,
                        onStartServer = actions.onStartServer,
                        onStopServer = actions.onStopServer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
