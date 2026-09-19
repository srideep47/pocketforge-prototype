package com.srideep.pocketforge.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.chat.ChatMessage
import com.srideep.pocketforge.chat.ModelStatus
import com.srideep.pocketforge.chat.Role
import com.srideep.pocketforge.chat.StudioUiState
import com.srideep.pocketforge.chat.ToolTrace
import com.srideep.pocketforge.ui.theme.CodeColors

/**
 * The chat tab: transcript above, composer below.
 *
 * Voice appears in exactly one place — the mic dictates into the field. Sending stays a
 * separate tap, so a misheard prompt never reaches the model.
 */
@Composable
fun ChatPane(
    state: StudioUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            EmptyChat(
                modelReady = state.modelStatus == ModelStatus.READY,
                onSuggestion = onInputChange,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.messages, key = { it.id }) { message -> MessageRow(message) }
            }
        }

        Composer(state, onInputChange, onSend, onStop, onMic)
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    when (message.role) {
        Role.USER -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Role.SYSTEM -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "  " + message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Role.ASSISTANT -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (message.tools.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                ) {
                    message.tools.forEach { trace -> ToolTraceRow(trace) }
                }
            }

            if (message.text.isNotBlank()) {
                MarkdownText(text = message.text, modifier = Modifier.padding(end = 28.dp))
            } else if (message.streaming && message.tools.isEmpty()) {
                ThinkingDots()
            }
        }
    }
}

@Composable
private fun ToolTraceRow(trace: ToolTrace) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            when (trace.ok) {
                null -> PulsingDot()
                true -> Icon(
                    Icons.Default.Check,
                    contentDescription = "done",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )

                false -> Icon(
                    Icons.Default.Close,
                    contentDescription = "failed",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Text(
            text = trace.name,
            fontFamily = CodeColors.mono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
        if (trace.summary.isNotBlank()) {
            Text(
                text = trace.summary,
                fontFamily = CodeColors.mono,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ThinkingDots() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { PulsingDot() }
    }
}

@Composable
private fun EmptyChat(
    modelReady: Boolean,
    onSuggestion: (String) -> Unit,
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
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (modelReady) "What should we build?" else "Load a model to begin",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (modelReady) {
                "Describe a site. It gets written, served and previewed on this phone."
            } else {
                "Open the chip menu in the top bar and download one."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )

        if (modelReady) {
            suggestions.forEach { suggestion ->
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable { onSuggestion(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun Composer(
    state: StudioUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = onMic,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isListening) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            },
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Dictate",
                        modifier = Modifier.size(20.dp),
                        tint = if (state.isListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                TextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Describe the site you want",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                val enabled = state.isGenerating || state.input.isNotBlank()
                IconButton(
                    onClick = if (state.isGenerating) onStop else onSend,
                    enabled = enabled,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
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
                        modifier = Modifier.size(19.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
