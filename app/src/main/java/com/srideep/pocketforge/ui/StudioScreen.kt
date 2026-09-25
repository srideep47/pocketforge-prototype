package com.srideep.pocketforge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.chat.ModelStatus
import com.srideep.pocketforge.chat.StudioUiState
import com.srideep.pocketforge.ui.theme.CodeColors
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
    val onToggleHandsFree: () -> Unit,
    val onCamera: () -> Unit,
    val onPickImage: () -> Unit,
    val onClearImage: () -> Unit,
    val onOpenFile: (String) -> Unit,
    val onEditorChange: (String) -> Unit,
    val onSaveFile: () -> Unit,
    val onCreateFile: (String) -> Unit,
    val onCreateFolder: (String) -> Unit,
    val onRenameFile: (String, String) -> Unit,
    val onDeleteFile: (String) -> Unit,
    val onRefreshFiles: () -> Unit,
    val onNewProject: () -> Unit,
    val onStartServer: () -> Unit,
    val onStopServer: () -> Unit,
    val onLoadModel: (String) -> Unit,
    val onDownloadModel: (String) -> Unit,
    val onCancelDownload: (String) -> Unit,
    val onDeleteModel: (String) -> Unit,
    val onRefreshModels: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(state: StudioUiState, actions: StudioActions) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var modelSheetOpen by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                FileExplorer(
                    files = state.files,
                    selectedPath = state.openFile?.path,
                    onOpen = { path ->
                        actions.onOpenFile(path)
                        tab = StudioTab.CODE.ordinal
                        scope.launch { drawerState.close() }
                    },
                    onCreateFile = actions.onCreateFile,
                    onCreateFolder = actions.onCreateFolder,
                    onRename = actions.onRenameFile,
                    onDelete = actions.onDeleteFile,
                    onRefresh = actions.onRefreshFiles,
                    onNewProject = actions.onNewProject,
                )
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        title = {
                            Column {
                                Text(
                                    text = "PocketForge",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                AnimatedVisibility(visible = state.status.isNotBlank()) {
                                    Text(
                                        text = state.status,
                                        fontFamily = CodeColors.mono,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = "Project files",
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        },
                        actions = {
                            ModelChip(
                                name = state.modelName,
                                status = state.modelStatus,
                                onClick = {
                                    actions.onRefreshModels()
                                    modelSheetOpen = true
                                },
                            )
                        },
                    )
                    SegmentedTabs(selected = tab, onSelect = { tab = it })
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (StudioTab.entries[tab]) {
                    StudioTab.CHAT -> ChatPane(
                        state = state,
                        onInputChange = actions.onInputChange,
                        actions = ComposerActions(
                            onSend = actions.onSend,
                            onStop = actions.onStop,
                            onMic = actions.onMic,
                            onToggleHandsFree = actions.onToggleHandsFree,
                            onCamera = actions.onCamera,
                            onPickImage = actions.onPickImage,
                            onClearImage = actions.onClearImage,
                        ),
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

    if (modelSheetOpen) {
        ModelSheet(
            models = state.models,
            onDownload = actions.onDownloadModel,
            onCancel = actions.onCancelDownload,
            onLoad = { id ->
                modelSheetOpen = false
                actions.onLoadModel(id)
            },
            onDelete = actions.onDeleteModel,
            onDismiss = { modelSheetOpen = false },
        )
    }
}

/**
 * The model state lives in the top bar as a chip rather than an icon: which model is
 * loaded is the single most load-bearing fact on screen, and an icon cannot say it.
 */
@Composable
private fun ModelChip(name: String?, status: ModelStatus, onClick: () -> Unit) {
    val dotColor = when (status) {
        ModelStatus.READY -> MaterialTheme.colorScheme.secondary
        ModelStatus.LOADING -> MaterialTheme.colorScheme.primary
        ModelStatus.FAILED -> MaterialTheme.colorScheme.error
        ModelStatus.MISSING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        status == ModelStatus.LOADING -> "Loading"
        status == ModelStatus.READY && name != null -> name
        status == ModelStatus.FAILED -> "Failed"
        else -> "No model"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(100))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = "  $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A pill segmented control; Material's TabRow indicator reads as a browser, not a tool. */
@Composable
private fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        StudioTab.entries.forEachIndexed { index, entry ->
            val active = index == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (entry) {
                        StudioTab.CHAT -> Icons.AutoMirrored.Filled.Chat
                        StudioTab.CODE -> Icons.Default.Code
                        StudioTab.PREVIEW -> Icons.Default.Public
                    },
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = "  " + entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
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
