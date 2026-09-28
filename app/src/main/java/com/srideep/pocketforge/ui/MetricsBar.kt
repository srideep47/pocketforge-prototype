package com.srideep.pocketforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.chat.RunMetrics
import com.srideep.pocketforge.ui.theme.CodeColors
import java.util.Locale

/**
 * Compact, always-visible hardware telemetry strip above the composer.
 *
 * Proves at a glance to judges that inference is running locally on the phone (OFFLINE badge,
 * live decode tok/s, TTFT, RAM, and SoC thermal state) using tabular monospace numerals so
 * digits never jitter as counters update.
 */
@Composable
fun MetricsBar(metrics: RunMetrics, modifier: Modifier = Modifier) {
    val isOffline = metrics.offline || metrics.airplaneMode
    val (thermalLabel, thermalColor) = thermalInfo(metrics.thermalStatus)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Offline / On-device badge
            if (isOffline) {
                TelemetryBadge(
                    text = if (metrics.airplaneMode) "OFFLINE · AIRPLANE" else "OFFLINE",
                    accent = MaterialTheme.colorScheme.secondary,
                    filled = true,
                )
            } else {
                TelemetryBadge(
                    text = "ON-DEVICE",
                    accent = MaterialTheme.colorScheme.secondary,
                    filled = false,
                )
            }

            // 2. Decode throughput (primary performance signal)
            if (metrics.decodeTokensPerSecond > 0.0) {
                MetricPill(
                    label = "DECODE",
                    value = String.format(Locale.US, "%.1f tok/s", metrics.decodeTokensPerSecond),
                    accent = MaterialTheme.colorScheme.primary,
                )
            } else if (metrics.running) {
                MetricPill(
                    label = "STATE",
                    value = metrics.phase.label,
                    accent = MaterialTheme.colorScheme.primary,
                )
            }

            // 3. Time to first token
            metrics.timeToFirstTokenMs?.let { ttft ->
                MetricPill(
                    label = "TTFT",
                    value = seconds(ttft),
                )
            }

            // 4. Vision sidecar latency (when sketch or self-check ran)
            if (metrics.visionMs > 0L) {
                MetricPill(
                    label = "VISION",
                    value = seconds(metrics.visionMs),
                    accent = CodeColors.visionAccent,
                )
            }

            // 5. Peak RAM footprint
            if (metrics.peakRamMb > 0) {
                MetricPill(
                    label = "RAM",
                    value = String.format(Locale.US, "%.1f GB", metrics.peakRamMb / 1024.0),
                )
            }

            // 6. Thermal status pill (always visible so judges see thermal headroom)
            TelemetryBadge(
                text = thermalLabel,
                accent = thermalColor,
                filled = metrics.thermalStatus >= 3,
            )

            // 7. Prefill speed, generated tokens & elapsed wall-clock time
            if (metrics.prefillTokensPerSecond > 0.0) {
                MetricPill(
                    label = "PREFILL",
                    value = String.format(Locale.US, "%.0f tok/s", metrics.prefillTokensPerSecond),
                )
            }
            if (metrics.generatedTokens > 0) {
                MetricPill(
                    label = "TOKENS",
                    value = metrics.generatedTokens.toString(),
                )
            }
            MetricPill(
                label = if (metrics.running) "LIVE" else "TIME",
                value = seconds(metrics.elapsedMs),
            )
        }
    }
}

/**
 * Per-run hardware telemetry summary card rendered at the end of an assistant message.
 * Uses tabular monospace digits so every metric aligns cleanly.
 */
@Composable
fun RunSummaryCard(
    metrics: RunMetrics?,
    stepCount: Int,
    hadSelfCheck: Boolean,
    modifier: Modifier = Modifier,
) {
    val isOffline = metrics?.let { it.offline || it.airplaneMode } ?: false
    val (thermalLabel, thermalColor) = thermalInfo(metrics?.thermalStatus ?: 0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isOffline) Icons.Default.WifiOff else Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = if (isOffline) "OFFLINE · LOCAL MNN RUN" else "ON-DEVICE · LOCAL MNN RUN",
                    style = CodeColors.tabularMonoStyle,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (hadSelfCheck) {
                    TelemetryBadge(
                        text = "VISION VERIFIED",
                        accent = CodeColors.visionAccent,
                        filled = false,
                    )
                }
                if (stepCount > 0) {
                    Text(
                        text = if (stepCount == 1) "1 tool step" else "$stepCount tool steps",
                        style = CodeColors.tabularMonoStyle,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (metrics != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SummaryStatBox(
                    label = "DECODE",
                    value = if (metrics.decodeTokensPerSecond > 0) {
                        String.format(Locale.US, "%.1f tok/s", metrics.decodeTokensPerSecond)
                    } else {
                        "—"
                    },
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatBox(
                    label = "TTFT",
                    value = metrics.timeToFirstTokenMs?.let { seconds(it) } ?: "—",
                    subValue = if (metrics.visionMs > 0L) "vis " + seconds(metrics.visionMs) else null,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatBox(
                    label = "RAM / SOC",
                    value = if (metrics.peakRamMb > 0) {
                        String.format(Locale.US, "%.1f GB", metrics.peakRamMb / 1024.0)
                    } else {
                        "—"
                    },
                    subValue = thermalLabel,
                    subColor = thermalColor,
                    modifier = Modifier.weight(1f),
                )
                SummaryStatBox(
                    label = "OUTPUT",
                    value = "${metrics.generatedTokens} tok",
                    subValue = "in " + seconds(metrics.elapsedMs),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subValue: String? = null,
    accent: Color? = null,
    subColor: Color? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = CodeColors.tabularMonoStyle,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = CodeColors.tabularMonoStyle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (subValue != null) {
            Text(
                text = subValue,
                style = CodeColors.tabularMonoStyle,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = subColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TelemetryBadge(
    text: String,
    accent: Color,
    filled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) accent.copy(alpha = 0.18f) else accent.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = if (filled) 0.45f else 0.28f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = CodeColors.tabularMonoStyle,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    accent: Color? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = "$label ",
            style = CodeColors.tabularMonoStyle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = CodeColors.tabularMonoStyle,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun thermalInfo(thermalStatus: Int): Pair<String, Color> = when {
    thermalStatus >= 3 -> "THROTTLING" to Color(0xFFFF6B6B)
    thermalStatus == 2 -> "THERMAL WARM" to Color(0xFFFBBF24)
    else -> "THERMAL OK" to Color(0xFF34D399)
}

private fun seconds(ms: Long): String = String.format(Locale.US, "%.1fs", ms / 1000.0)
