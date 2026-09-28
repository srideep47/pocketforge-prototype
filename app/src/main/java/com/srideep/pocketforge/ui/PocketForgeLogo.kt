package com.srideep.pocketforge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The PocketForge brand emblem rendered in pure Compose vector geometry.
 *
 * Visual metaphor:
 * 1. Outer ARM64 Silicon Die Frame & NPU Pins -> "Runs 100% on-device in your pocket"
 * 2. Blacksmith Anvil formed by `<` and `>` code brackets -> "Forges live HTML/CSS/JS"
 * 3. 4-Point Vision Spark & Emerald Core -> "Sees hand-drawn sketches & self-checks renders"
 */
@Composable
fun PocketForgeLogo(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    val cornerRadius = size * 0.24f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF151E2C),
                        Color(0xFF0A0E15),
                    ),
                ),
            )
            .border(
                width = (size.value * 0.035f).coerceAtLeast(1f).dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.65f),
                        Color(0xFFA78BFA).copy(alpha = 0.45f),
                    ),
                ),
                shape = RoundedCornerShape(cornerRadius),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            fun sx(v: Float) = v / 108f * w
            fun sy(v: Float) = v / 108f * h

            // 1. Subtle radial forge core aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.22f),
                        Color(0xFFA78BFA).copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(sx(54f), sy(48f)),
                    radius = sx(42f),
                ),
                center = Offset(sx(54f), sy(48f)),
                radius = sx(42f),
            )

            // 2. Silicon chip contact pins (Top, Bottom, Left, Right)
            val pinColor = Color(0xFF334155)
            val pinXs = listOf(38f, 52f, 66f)
            pinXs.forEach { px ->
                drawRoundRect(
                    color = if (px == 52f) Color(0xFF38BDF8) else pinColor,
                    topLeft = Offset(sx(px), sy(13f)),
                    size = Size(sx(4f), sy(7f)),
                    cornerRadius = CornerRadius(sx(1.5f)),
                )
                drawRoundRect(
                    color = if (px == 52f) Color(0xFF34D399) else pinColor,
                    topLeft = Offset(sx(px), sy(88f)),
                    size = Size(sx(4f), sy(7f)),
                    cornerRadius = CornerRadius(sx(1.5f)),
                )
                drawRoundRect(
                    color = if (px == 52f) Color(0xFF38BDF8) else pinColor,
                    topLeft = Offset(sx(13f), sy(px)),
                    size = Size(sx(7f), sy(4f)),
                    cornerRadius = CornerRadius(sx(1.5f)),
                )
                drawRoundRect(
                    color = if (px == 52f) Color(0xFF38BDF8) else pinColor,
                    topLeft = Offset(sx(88f), sy(px)),
                    size = Size(sx(7f), sy(4f)),
                    cornerRadius = CornerRadius(sx(1.5f)),
                )
            }

            // 3. Inner Silicon Die Outline
            drawRoundRect(
                color = Color(0xFF283548),
                topLeft = Offset(sx(20f), sy(20f)),
                size = Size(sx(68f), sy(68f)),
                cornerRadius = CornerRadius(sx(12f)),
                style = Stroke(width = sx(2f)),
            )

            // 4. Left '<' Code Bracket (Anvil Horn)
            val leftBracket = Path().apply {
                moveTo(sx(42f), sy(44f))
                lineTo(sx(27f), sy(55f))
                lineTo(sx(42f), sy(66f))
                lineTo(sx(46f), sy(61f))
                lineTo(sx(36f), sy(55f))
                lineTo(sx(46f), sy(49f))
                close()
            }
            drawPath(path = leftBracket, color = Color(0xFF38BDF8))

            // 5. Right '>' Code Bracket (Anvil Heel)
            val rightBracket = Path().apply {
                moveTo(sx(66f), sy(44f))
                lineTo(sx(81f), sy(55f))
                lineTo(sx(66f), sy(66f))
                lineTo(sx(62f), sy(61f))
                lineTo(sx(72f), sy(55f))
                lineTo(sx(62f), sy(49f))
                close()
            }
            drawPath(path = rightBracket, color = Color(0xFF38BDF8))

            // 6. Central Blacksmith Anvil Table, Waist & Heavy Base
            val anvilBody = Path().apply {
                moveTo(sx(40f), sy(52f))
                lineTo(sx(68f), sy(52f))
                lineTo(sx(65f), sy(59f))
                lineTo(sx(59.5f), sy(63f))
                lineTo(sx(59.5f), sy(70f))
                lineTo(sx(67f), sy(74.5f))
                lineTo(sx(67f), sy(78f))
                lineTo(sx(41f), sy(78f))
                lineTo(sx(41f), sy(74.5f))
                lineTo(sx(48.5f), sy(70f))
                lineTo(sx(48.5f), sy(63f))
                lineTo(sx(43f), sy(59f))
                close()
            }
            drawPath(
                path = anvilBody,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF0F6FC),
                        Color(0xFF7DD3FC),
                        Color(0xFF38BDF8),
                    ),
                    startY = sy(52f),
                    endY = sy(78f),
                ),
            )

            // 7. Vision AI Spark / 4-Point Star above the Anvil Face
            val sparkPath = Path().apply {
                moveTo(sx(54f), sy(26f))
                cubicTo(sx(55.2f), sy(33.5f), sx(57f), sy(35.3f), sx(64.5f), sy(36.5f))
                cubicTo(sx(57f), sy(37.7f), sx(55.2f), sy(39.5f), sx(54f), sy(47f))
                cubicTo(sx(52.8f), sy(39.5f), sx(51f), sy(37.7f), sx(43.5f), sy(36.5f))
                cubicTo(sx(51f), sy(35.3f), sx(52.8f), sy(33.5f), sx(54f), sy(26f))
                close()
            }
            drawPath(path = sparkPath, color = Color(0xFFA78BFA))

            // 8. On-Device Emerald Core inside the Vision Spark
            drawCircle(
                color = Color(0xFF34D399),
                radius = sx(3.2f),
                center = Offset(sx(54f), sy(36.5f)),
            )
        }
    }
}
