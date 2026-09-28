package com.srideep.pocketforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.chat.ModelEntry
import com.srideep.pocketforge.chat.ModelInstallState
import com.srideep.pocketforge.ui.theme.CodeColors
import java.util.Locale

/**
 * On-device model manager sheet.
 *
 * Clearly distinguishes primary Coder LLMs from the automatic Vision Helper sidecar,
 * and shows exact Use / In use / Ready / Remove states and download progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    models: List<ModelEntry>,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ModelSheetContent(
            models = models,
            onDownload = onDownload,
            onCancel = onCancel,
            onLoad = onLoad,
            onDelete = onDelete,
        )
    }
}

@Composable
fun ModelSheetContent(
    models: List<ModelEntry>,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<ModelEntry?>(null) }
    val coderModels = remember(models) { models.filter { !it.isVision } }
    val visionModels = remember(models) { models.filter { it.isVision } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "On-Device Models",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "MNN ARM64 · OFFLINE",
                    fontFamily = CodeColors.mono,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        Text(
            text = "Runs 100% locally on this phone. Download once; builds and previews work in Airplane Mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (coderModels.isNotEmpty()) {
            Text(
                text = "PRIMARY CODER MODELS",
                fontFamily = CodeColors.mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            coderModels.forEach { model ->
                ModelRowCard(
                    model = model,
                    onDownload = { onDownload(model.id) },
                    onCancel = { onCancel(model.id) },
                    onLoad = { onLoad(model.id) },
                    onDelete = { pendingDelete = model },
                )
            }
        }

        if (visionModels.isNotEmpty()) {
            Text(
                text = "VISION HELPER SIDECAR (NOT A STANDALONE CODER)",
                fontFamily = CodeColors.mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CodeColors.visionAccent,
                modifier = Modifier.padding(top = 6.dp),
            )
            visionModels.forEach { model ->
                ModelRowCard(
                    model = model,
                    onDownload = { onDownload(model.id) },
                    onCancel = { onCancel(model.id) },
                    onLoad = { onLoad(model.id) },
                    onDelete = { pendingDelete = model },
                )
            }
        }

        pendingDelete?.let { model ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Remove " + model.displayName + "?") },
                text = {
                    Text(
                        "Deletes " + gigabytes(model.approxBytes) +
                            " from this device. Downloading it again requires a network connection.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = model.id
                        pendingDelete = null
                        onDelete(id)
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun ModelRowCard(
    model: ModelEntry,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val isLoaded = model.state == ModelInstallState.LOADED
    val isVisionReady = model.isVision &&
        model.state != ModelInstallState.NOT_DOWNLOADED &&
        model.state != ModelInstallState.DOWNLOADING

    val borderColor = when {
        isLoaded -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
        model.isVision -> CodeColors.visionAccent.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isLoaded -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    model.isVision -> CodeColors.visionContainer.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
            )
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (model.isVision) Icons.Default.Visibility else Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (model.isVision) CodeColors.visionAccent else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLoaded) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "In use",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                // Role badge + Size pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (model.isVision) "HELPER · AUTO SIDECAR" else "CODER · AGENT",
                        fontFamily = CodeColors.mono,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (model.isVision) CodeColors.visionAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                (if (model.isVision) CodeColors.visionAccent else MaterialTheme.colorScheme.primary)
                                    .copy(alpha = 0.14f),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                    Text(
                        text = gigabytes(model.approxBytes),
                        style = CodeColors.tabularMonoStyle,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // One-line subtitle
                Text(
                    text = model.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (model.isVision) {
                    Text(
                        text = "Used automatically for sketch photos & render self-checks (not a coder).",
                        style = MaterialTheme.typography.labelSmall,
                        color = CodeColors.visionAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Right action area (Use / In use / Ready / Download / Pause / Remove)
            when {
                model.state == ModelInstallState.NOT_DOWNLOADED && model.sideloadOnly -> Text(
                    text = "Copy via USB",
                    fontFamily = CodeColors.mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                isVisionReady -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Ready",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                model.state == ModelInstallState.NOT_DOWNLOADED -> Button(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = gigabytes(model.approxBytes),
                        style = CodeColors.tabularMonoStyle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                model.state == ModelInstallState.DOWNLOADING -> TextButton(onClick = onCancel) {
                    Text("Pause")
                }

                model.state == ModelInstallState.DOWNLOADED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onLoad,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Use")
                    }
                }

                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "In use",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }

        if (model.state == ModelInstallState.DOWNLOADING) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { model.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = model.progressLabel.ifBlank { "Downloading…" },
                        style = CodeColors.tabularMonoStyle,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", model.progress * 100f),
                        style = CodeColors.tabularMonoStyle,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun gigabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
