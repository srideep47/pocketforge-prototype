package com.srideep.pocketforge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srideep.pocketforge.chat.ModelEntry
import com.srideep.pocketforge.chat.ModelInstallState

/**
 * The model picker: two rows, each one of download / use / in-use.
 *
 * A sheet rather than a dropdown because these rows carry a progress bar and a
 * multi-gigabyte download that the user needs to watch, not a one-tap choice.
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Model", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Runs entirely on this device. Download once; it works offline after that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            models.forEach { model ->
                ModelRow(
                    model = model,
                    onDownload = { onDownload(model.id) },
                    onCancel = { onCancel(model.id) },
                    onLoad = { onLoad(model.id) },
                    onDelete = { onDelete(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelEntry,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    if (model.state == ModelInstallState.LOADED) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "In use",
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = model.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (model.state) {
                ModelInstallState.NOT_DOWNLOADED -> Button(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  " + gigabytes(model.approxBytes))
                }

                ModelInstallState.DOWNLOADING -> TextButton(onClick = onCancel) { Text("Pause") }

                ModelInstallState.DOWNLOADED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onLoad) { Text("Use") }
                }

                ModelInstallState.LOADED -> OutlinedButton(onClick = onDelete) { Text("Remove") }
            }
        }

        if (model.state == ModelInstallState.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { model.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = model.progressLabel.ifBlank { "Starting…" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun gigabytes(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
