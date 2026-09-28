package com.srideep.pocketforge.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.chat.ChatMessage
import com.srideep.pocketforge.chat.ModelStatus
import com.srideep.pocketforge.chat.Role
import com.srideep.pocketforge.chat.RunMetrics
import com.srideep.pocketforge.chat.StudioUiState
import com.srideep.pocketforge.chat.ToolTrace
import com.srideep.pocketforge.ui.theme.CodeColors
import java.util.Locale

/** Everything the composer can do besides typing. */
class ComposerActions(
    val onSend: () -> Unit,
    val onStop: () -> Unit,
    val onMic: () -> Unit,
    val onToggleHandsFree: () -> Unit,
    val onCamera: () -> Unit,
    val onPickImage: () -> Unit,
    val onClearImage: () -> Unit,
    val onToggleThinking: () -> Unit = {},
)

private val CHARS_REGEX = Regex("""(\d+)\s*chars""")

/**
 * The chat tab: vertical step timeline transcript above, live telemetry & composer below.
 */
@Composable
fun ChatPane(
    state: StudioUiState,
    onInputChange: (String) -> Unit,
    actions: ComposerActions,
    modifier: Modifier = Modifier,
    onOpenPreview: () -> Unit = {},
    onOpenCode: (String) -> Unit = {},
    onOpenModelSheet: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val lastMessage = state.messages.lastOrNull()

    LaunchedEffect(
        state.messages.size,
        lastMessage?.text?.length,
        lastMessage?.tools?.size,
        lastMessage?.streaming,
    ) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val liveChars = remember(state.status) {
        CHARS_REGEX.find(state.status)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
    val hasBuiltPage = state.previewUrl != null ||
        state.devServerRunning ||
        state.files.any { it.name.equals("index.html", ignoreCase = true) }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            EmptyChat(
                modelReady = state.modelStatus == ModelStatus.READY,
                modelName = state.modelName,
                onSuggestion = onInputChange,
                onCamera = actions.onCamera,
                onOpenModelSheet = onOpenModelSheet,
                modifier = Modifier.weight(1f),
            )
        } else {
            val lastAssistantIndex = state.messages.indexOfLast { it.role == Role.ASSISTANT }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(state.messages, key = { _, msg -> msg.id }) { index, message ->
                    val isLatestAssistant = index == lastAssistantIndex
                    MessageRow(
                        message = message,
                        isLatestAssistant = isLatestAssistant,
                        liveChars = if (isLatestAssistant) liveChars else 0,
                        metrics = if (isLatestAssistant) state.metrics else null,
                        previewUrl = state.previewUrl,
                        hasBuiltPage = hasBuiltPage,
                        onOpenPreview = onOpenPreview,
                        onOpenCode = onOpenCode,
                    )
                }
            }
        }

        state.metrics?.let { MetricsBar(it) }
        Composer(state = state, onInputChange = onInputChange, actions = actions)
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    isLatestAssistant: Boolean,
    liveChars: Int,
    metrics: RunMetrics?,
    previewUrl: String?,
    hasBuiltPage: Boolean,
    onOpenPreview: () -> Unit,
    onOpenCode: (String) -> Unit,
) {
    when (message.role) {
        Role.USER -> UserMessageCard(message)

        Role.SYSTEM -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f))
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        Role.ASSISTANT -> AssistantRunCard(
            message = message,
            isLatestAssistant = isLatestAssistant,
            liveChars = liveChars,
            metrics = metrics,
            previewUrl = previewUrl,
            hasBuiltPage = hasBuiltPage,
            onOpenPreview = onOpenPreview,
            onOpenCode = onOpenCode,
        )
    }
}

@Composable
private fun UserMessageCard(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        message.imagePath?.let { path ->
            Box(
                modifier = Modifier
                    .size(width = 186.dp, height = 216.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, CodeColors.visionAccent.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
            ) {
                LocalImage(
                    path = path,
                    contentDescription = "Attached sketch photo",
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                        .border(1.dp, CodeColors.visionAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = CodeColors.visionAccent,
                    )
                    Text(
                        text = "SKETCH INPUT",
                        fontFamily = CodeColors.mono,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = CodeColors.visionAccent,
                    )
                }
            }
        }
        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantRunCard(
    message: ChatMessage,
    isLatestAssistant: Boolean,
    liveChars: Int,
    metrics: RunMetrics?,
    previewUrl: String?,
    hasBuiltPage: Boolean,
    onOpenPreview: () -> Unit,
    onOpenCode: (String) -> Unit,
) {
    val activeWriteTool = message.tools.lastOrNull {
        (it.name == "create_file" || it.name == "edit_file") && it.ok == null
    }
    val targetFileName = activeWriteTool?.summary?.takeIf { it.isNotBlank() } ?: "index.html"
    val showLiveWriter = message.streaming && (activeWriteTool != null || liveChars > 0 || (metrics?.generatedTokens ?: 0) > 0)
    val charCount = when {
        liveChars > 0 -> liveChars
        message.text.isNotEmpty() -> message.text.length
        else -> (metrics?.generatedTokens ?: 0) * 4
    }
    val tokenCount = metrics?.generatedTokens ?: 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Vertical Step Timeline + Live File Writer
        if (message.tools.isNotEmpty() || showLiveWriter || message.streaming) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (message.streaming) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                ),
                        )
                        Text(
                            text = if (message.streaming) "AGENT PIPELINE · ACTIVE" else "AGENT PIPELINE · COMPLETE",
                            fontFamily = CodeColors.mono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (message.streaming) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = if (message.tools.size == 1) "1 step" else "${message.tools.size} steps",
                        style = CodeColors.tabularMonoStyle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                message.tools.forEachIndexed { idx, trace ->
                    val isLastItem = idx == message.tools.lastIndex && !showLiveWriter
                    TimelineStepRow(trace = trace, isLast = isLastItem)
                }

                if (showLiveWriter) {
                    LiveFileWriterRow(
                        fileName = targetFileName,
                        charsCount = charCount,
                        tokensCount = tokenCount,
                        tokPerSec = metrics?.decodeTokensPerSecond ?: 0.0,
                    )
                } else if (message.streaming && message.tools.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "writing index.html… preparing tool call",
                            fontFamily = CodeColors.mono,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 2. Assistant Markdown Prose / Summary
        if (message.text.isNotBlank()) {
            MarkdownText(
                text = message.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
            )
        }

        // 3. Clear "Open preview" affordance once a page is built
        val touchedSite = message.tools.any {
            it.name in setOf("create_file", "edit_file", "start_dev_server", "check_render")
        }
        if (!message.streaming && (touchedSite || (isLatestAssistant && hasBuiltPage))) {
            OpenPreviewCard(
                previewUrl = previewUrl,
                onOpenPreview = onOpenPreview,
                onOpenCode = { onOpenCode("index.html") },
            )
        }

        // 4. Per-run telemetry summary card at the end of each assistant message
        if (!message.streaming && (message.tools.isNotEmpty() || message.text.isNotBlank())) {
            RunSummaryCard(
                metrics = metrics,
                stepCount = message.tools.size,
                hadSelfCheck = message.tools.any { it.name == "check_render" && it.ok == true },
            )
        }
    }
}

@Composable
private fun TimelineStepRow(
    trace: ToolTrace,
    isLast: Boolean,
) {
    val isVisionStep = trace.name == "read_sketch" || trace.name == "check_render"
    val isFixing = trace.name == "check_render" && trace.summary.startsWith("fixing:", ignoreCase = true)

    val stepAccent = when {
        trace.ok == false -> MaterialTheme.colorScheme.error
        isFixing -> CodeColors.fixAmber
        isVisionStep -> CodeColors.visionAccent
        trace.ok == true -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    val (stepIcon, headline, badgeText) = stepPresentation(trace)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Left vertical timeline rail + status icon node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(stepAccent.copy(alpha = 0.16f))
                    .border(1.dp, stepAccent.copy(alpha = 0.55f), CircleShape),
            ) {
                when (trace.ok) {
                    null -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.8.dp,
                        color = stepAccent,
                    )

                    true -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "done",
                        modifier = Modifier.size(13.dp),
                        tint = stepAccent,
                    )

                    false -> Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "failed",
                        modifier = Modifier.size(13.dp),
                        tint = stepAccent,
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right step card content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 2.dp else 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        isFixing -> CodeColors.fixContainer.copy(alpha = 0.55f)
                        isVisionStep -> CodeColors.visionContainer.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isFixing -> CodeColors.fixAmber.copy(alpha = 0.4f)
                        isVisionStep -> CodeColors.visionAccent.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    },
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = stepIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = stepAccent,
                    )
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        fontFamily = CodeColors.mono,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = stepAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(stepAccent.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }

            // Tool trace identifier & summary line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = trace.name,
                    fontFamily = CodeColors.mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (trace.summary.isNotBlank()) {
                    Text(
                        text = "· " + trace.summary,
                        fontFamily = CodeColors.mono,
                        fontSize = 11.sp,
                        color = if (isFixing) CodeColors.fixAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Optional detail snippet for vision reading or render check
            if (isVisionStep && trace.detail.isNotBlank()) {
                Text(
                    text = if (trace.name == "check_render" && isFixing) {
                        "Checking its own work → " + trace.detail
                    } else {
                        trace.detail
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun stepPresentation(trace: ToolTrace): Triple<ImageVector, String, String?> {
    return when (trace.name) {
        "read_sketch" -> Triple(
            Icons.Default.Visibility,
            "Reading your sketch",
            "VISION",
        )

        "think" -> Triple(
            Icons.Default.Psychology,
            if (trace.ok == null) "Thinking it through" else "Planned the page",
            "REASONING",
        )

        "check_render" -> {
            val isFixing = trace.summary.startsWith("fixing:", ignoreCase = true)
            if (isFixing) {
                val issue = trace.summary.substringAfter(":").trim().ifBlank { trace.detail }
                Triple(
                    Icons.Default.AutoFixHigh,
                    "Fixing: $issue",
                    "SELF-FIX",
                )
            } else {
                Triple(
                    Icons.Default.FactCheck,
                    "Checking its own work",
                    if (trace.ok == true) "VERIFIED" else "VISION CRITIC",
                )
            }
        }

        "create_file" -> Triple(
            Icons.Default.NoteAdd,
            "Writing " + trace.summary.ifBlank { "index.html" },
            "WRITE",
        )

        "edit_file" -> Triple(
            Icons.Default.EditNote,
            "Patching " + trace.summary.ifBlank { "index.html" },
            "PATCH",
        )

        "read_file" -> Triple(
            Icons.Default.Description,
            "Reading " + trace.summary.ifBlank { "file" },
            "READ",
        )

        "list_files" -> Triple(
            Icons.Default.FolderOpen,
            "Inspecting workspace files",
            "FILES",
        )

        "start_dev_server" -> Triple(
            Icons.Default.Dns,
            "Starting local dev server",
            "LOCALHOST",
        )

        else -> Triple(
            Icons.Default.Build,
            trace.name,
            null,
        )
    }
}

/**
 * Live file-writer telemetry row shown while the LLM streams out index.html so the UI
 * never looks frozen during multi-thousand-character code generation.
 */
@Composable
private fun LiveFileWriterRow(
    fileName: String,
    charsCount: Int,
    tokensCount: Int,
    tokPerSec: Double,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "writing $fileName…",
                fontFamily = CodeColors.mono,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val statsText = buildString {
            if (charsCount > 0) append(String.format(Locale.US, "%,d chars", charsCount))
            if (tokensCount > 0) {
                if (isNotEmpty()) append(" · ")
                append("$tokensCount tok")
            }
            if (tokPerSec > 0.0) {
                if (isNotEmpty()) append(" · ")
                append(String.format(Locale.US, "%.1f tok/s", tokPerSec))
            }
        }.ifEmpty { "streaming…" }

        Text(
            text = statsText,
            style = CodeColors.tabularMonoStyle,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Prominent "Open preview" affordance shown inside the chat once a page is built.
 */
@Composable
private fun OpenPreviewCard(
    previewUrl: String?,
    onOpenPreview: () -> Unit,
    onOpenCode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                )
                Text(
                    text = "Live preview ready",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = previewUrl ?: "http://127.0.0.1 · index.html",
                fontFamily = CodeColors.mono,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = onOpenCode,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Code", style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = onOpenPreview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("Open preview", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EmptyChat(
    modelReady: Boolean,
    modelName: String?,
    onSuggestion: (String) -> Unit,
    onCamera: () -> Unit,
    onOpenModelSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = listOf(
        "A dark landing page for a coffee shop",
        "A calculator with chunky retro buttons",
        "A stopwatch with start, stop and lap",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // On-device hardware pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f), RoundedCornerShape(100))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "100% ON-DEVICE · MNN ENGINE · ZERO CLOUD",
                fontFamily = CodeColors.mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Build apps on your phone. Offline.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "Prompt a full web app or snap a hand-drawn sketch. Local Qwen writes index.html, serves it on localhost, and checks its own render with vision.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )

        if (!modelReady) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenModelSheet)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "Load a model to begin",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Tap to open local models (Qwen 3.5 · 4B + Vision helper)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Big "Build from a photo" camera hero button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .clickable(onClick = onCamera)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = "Build from a photo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Snap a hand-drawn paper wireframe — local vision turns it into live HTML/JS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "STARTER PROMPTS",
            fontFamily = CodeColors.mono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        suggestions.forEach { suggestion ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
                    .clickable { onSuggestion(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = ">",
                    fontFamily = CodeColors.mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Whether the coder reasons before writing. Switching reloads the model (a few seconds), so it
 * is locked while a run or a load is in progress.
 */
@Composable
private fun ThinkingPill(
    on: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (on) CodeColors.visionAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.6f else 0.25f), RoundedCornerShape(50))
            .background(if (on) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = "Toggle thinking", onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = accent.copy(alpha = if (enabled) 1f else 0.5f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (on) "Thinking on" else "Thinking off",
            fontFamily = CodeColors.mono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accent.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Composer(
    state: StudioUiState,
    onInputChange: (String) -> Unit,
    actions: ComposerActions,
) {
    var photoMenuOpen by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            state.attachedImage?.let { path ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 10.dp),
                ) {
                    LocalImage(
                        path = path,
                        contentDescription = "Photo to build from",
                        maxSide = 256,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, CodeColors.visionAccent, RoundedCornerShape(10.dp)),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = "Sketch photo attached",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Vision will inspect this photo and build index.html",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = actions.onClearImage,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove photo",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            ThinkingPill(
                on = state.thinking,
                enabled = !state.isGenerating && state.modelStatus != ModelStatus.LOADING,
                onToggle = actions.onToggleThinking,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )
            if (state.handsFree) {
                Text(
                    text = if (state.isListening) {
                        "● Listening… speak your change"
                    } else {
                        "Hands-free active: tap Dictate and speak"
                    },
                    fontFamily = CodeColors.mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // 48dp touch target for Dictate (tap dictates; long-press toggles hands-free)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                state.isListening -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                state.handsFree -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                                else -> Color.Transparent
                            },
                        )
                        .combinedClickable(
                            onClick = actions.onMic,
                            onLongClick = actions.onToggleHandsFree,
                            onClickLabel = "Dictate",
                            onLongClickLabel = "Toggle hands-free",
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Dictate",
                        modifier = Modifier.size(21.dp),
                        tint = if (state.isListening || state.handsFree) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                // 48dp touch target for Build from a photo
                Box {
                    IconButton(
                        onClick = { photoMenuOpen = true },
                        enabled = !state.isGenerating,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Build from a photo",
                            modifier = Modifier.size(21.dp),
                            tint = if (state.attachedImage != null) {
                                CodeColors.visionAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    DropdownMenu(
                        expanded = photoMenuOpen,
                        onDismissRequest = { photoMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Take a photo") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                            onClick = {
                                photoMenuOpen = false
                                actions.onCamera()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Choose an image") },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                photoMenuOpen = false
                                actions.onPickImage()
                            },
                        )
                    }
                }

                TextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (state.attachedImage != null) {
                                "Anything to add? (optional)"
                            } else {
                                "Describe the app or page to build…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                val enabled = state.isGenerating || state.input.isNotBlank() || state.attachedImage != null
                IconButton(
                    onClick = if (state.isGenerating) actions.onStop else actions.onSend,
                    enabled = enabled,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                state.isGenerating -> MaterialTheme.colorScheme.error
                                enabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                ) {
                    Icon(
                        imageVector = if (state.isGenerating) {
                            Icons.Default.Stop
                        } else {
                            Icons.AutoMirrored.Filled.Send
                        },
                        contentDescription = if (state.isGenerating) "Stop" else "Send",
                        modifier = Modifier.size(20.dp),
                        tint = when {
                            state.isGenerating -> MaterialTheme.colorScheme.onError
                            enabled -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
