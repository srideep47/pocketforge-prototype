package com.srideep.pocketforge.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.ui.theme.CodeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A thumbnail of a file on disk, decoded off the main thread and subsampled to [maxSide].
 * Includes a wireframe sketch fallback while decoding or in @Preview inspection mode.
 */
@Composable
fun LocalImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxSide: Int = 512,
) {
    val isPreview = LocalInspectionMode.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, isPreview) {
        value = if (isPreview || path.startsWith("preview://")) {
            null
        } else {
            withContext(Dispatchers.IO) { decode(path, maxSide) }
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        SketchThumbnailPlaceholder(
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
private fun SketchThumbnailPlaceholder(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val lineColor = CodeColors.visionAccent.copy(alpha = 0.45f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 18.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            val pad = 12.dp.toPx()
            if (size.width > pad * 3 && size.height > pad * 3) {
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(pad, pad),
                    size = Size(size.width - pad * 2, size.height - pad * 2),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    ),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Draw,
                contentDescription = contentDescription,
                tint = CodeColors.visionAccent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "SKETCH",
                fontFamily = CodeColors.mono,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = CodeColors.visionAccent,
            )
        }
    }
}

private fun decode(path: String, maxSide: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
