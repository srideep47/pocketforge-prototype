package com.srideep.pocketforge.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A fixed palette rather than Material You.
 *
 * Dynamic colour pulls whatever the wallpaper happens to be into a tool whose entire
 * surface is code and terminal output, and the result reads as washed out. A developer
 * tool should look the same on every device.
 */
private val Ink = Color(0xFF0D0F13)
private val SurfaceDark = Color(0xFF14171D)
private val SurfaceHigh = Color(0xFF1B1F27)
private val Line = Color(0xFF262C36)
private val TextPrimary = Color(0xFFE6E9EF)
private val TextMuted = Color(0xFF8A93A3)
private val Accent = Color(0xFF4DA3FF)
private val AccentInk = Color(0xFF04203F)
private val Positive = Color(0xFF4ADE9B)
private val Negative = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = AccentInk,
    primaryContainer = Color(0xFF12314F),
    onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = Positive,
    onSecondary = AccentInk,
    secondaryContainer = Color(0xFF1D2A35),
    onSecondaryContainer = TextPrimary,
    background = Ink,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMuted,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF212733),
    outline = Line,
    outlineVariant = Line,
    error = Negative,
    onError = Color(0xFF3B0A0A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    onPrimaryContainer = Color(0xFF062A55),
    secondary = Color(0xFF12855C),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF12151A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5A6373),
    outline = Color(0xFFD6DBE3),
    error = Color(0xFFC2352F),
)

/** One scale, reused. Monospace is reserved for paths, code and tool names. */
private val PocketTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Colours for code, shared by the editor and chat's fenced blocks. */
object CodeColors {
    val tag = Color(0xFF7FB8FF)
    val attribute = Color(0xFFB79CFF)
    val string = Color(0xFF8FD98F)
    val keyword = Color(0xFFFF9EC4)
    val number = Color(0xFFFFC27A)
    val comment = Color(0xFF5E6878)
    val gutter = Color(0xFF4C5666)
    val mono = FontFamily.Monospace
}

/**
 * Dark by default rather than following the system. The palette was designed for a dark
 * surface — code, a terminal-ish gutter, a preview of pages that are usually dark
 * themselves — and a developer tool that flips to a bright grey sheet at 9am is not doing
 * anyone a favour. The light scheme is kept for anyone who wants it.
 */
@Composable
fun PocketForgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PocketTypography,
        shapes = PocketShapes,
        content = content,
    )
}
