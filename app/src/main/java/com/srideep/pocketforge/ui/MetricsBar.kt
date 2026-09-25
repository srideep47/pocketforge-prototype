package com.srideep.pocketforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * One line of live numbers above the composer.
 *
 * It exists so that "this runs on the phone, offline" is something the screen shows rather
 * than something the user has to take on trust: the offline badge comes from the
 * connectivity service, and every rate comes from MNN's own counters.
 */
@Composable
fun MetricsBar(metrics: RunMetrics, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (metrics.offline) {
            Pill(if (metrics.airplaneMode) "AIRPLANE MODE" else "OFFLINE", MaterialTheme.colorScheme.secondary)
        } else {
            Pill("ON-DEVICE", MaterialTheme.colorScheme.secondary)
        }
        metrics.timeToFirstTokenMs?.let { Pill("first token " + seconds(it)) }
        if (metrics.visionMs > 0L) Pill("vision " + seconds(metrics.visionMs))
        if (metrics.decodeTokensPerSecond > 0.0) {
            Pill(String.format(Locale.US, "%.1f tok/s", metrics.decodeTokensPerSecond))
        }
        if (metrics.prefillTokensPerSecond > 0.0) {
            Pill(String.format(Locale.US, "prefill %.0f tok/s", metrics.prefillTokensPerSecond))
        }
        if (metrics.generatedTokens > 0) Pill(metrics.generatedTokens.toString() + " tokens")
        Pill((if (metrics.running) "" else "total ") + seconds(metrics.elapsedMs))
        if (metrics.peakRamMb > 0) {
            Pill(String.format(Locale.US, "RAM %.1f GB", metrics.peakRamMb / 1024.0))
        }
        // THERMAL_STATUS_SEVERE and up: the SoC is throttling, which explains a slow run.
        if (metrics.thermalStatus >= 3) Pill("THROTTLING", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Pill(text: String, accent: Color? = null) {
    Text(
        text = text,
        fontFamily = CodeColors.mono,
        fontSize = 10.5.sp,
        fontWeight = if (accent != null) FontWeight.SemiBold else FontWeight.Normal,
        color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background((accent ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

private fun seconds(ms: Long): String = String.format(Locale.US, "%.1fs", ms / 1000.0)
