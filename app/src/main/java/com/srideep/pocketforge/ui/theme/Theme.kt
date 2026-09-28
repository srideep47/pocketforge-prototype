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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srideep.pocketforge.R

/**
 * Bundled JetBrains Mono font family for code, paths, tool traces, and tabular metrics.
 */
val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Fixed precision pro-studio palette rather than dynamic Material You colors.
 * High-contrast dark-first surfaces with a clean light theme counterpart.
 */
private val ObsidianBg = Color(0xFF0A0D12)
private val SurfaceDark = Color(0xFF11161F)
private val SurfaceElevatedDark = Color(0xFF171E29)
private val SurfaceHighDark = Color(0xFF1E2634)
private val SurfaceHighestDark = Color(0xFF263042)
private val OutlineDark = Color(0xFF283244)
private val OutlineSubtleDark = Color(0xFF1D2533)
private val TextPrimaryDark = Color(0xFFF0F4FA)
private val TextSecondaryDark = Color(0xFF9BA6B8)

private val AccentCyan = Color(0xFF38BDF8)
private val AccentCyanInk = Color(0xFF041E30)
private val AccentCyanContainer = Color(0xFF0E2D47)
private val AccentCyanOnContainer = Color(0xFFD6F0FE)

private val EmeraldReady = Color(0xFF34D399)
private val EmeraldInk = Color(0xFF042619)
private val EmeraldContainer = Color(0xFF0F2E24)

private val VisionViolet = Color(0xFFA78BFA)
private val VisionContainerDark = Color(0xFF231B3F)

private val AmberFix = Color(0xFFFBBF24)
private val AmberContainerDark = Color(0xFF33260B)

private val CoralError = Color(0xFFFF6B6B)
private val CoralErrorInk = Color(0xFF3B0A0A)

private val DarkColors = darkColorScheme(
    primary = AccentCyan,
    onPrimary = AccentCyanInk,
    primaryContainer = AccentCyanContainer,
    onPrimaryContainer = AccentCyanOnContainer,
    secondary = EmeraldReady,
    onSecondary = EmeraldInk,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = VisionViolet,
    onTertiary = Color(0xFF1A0F36),
    tertiaryContainer = VisionContainerDark,
    onTertiaryContainer = Color(0xFFEDE5FF),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceElevatedDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineSubtleDark,
    error = CoralError,
    onError = CoralErrorInk,
    errorContainer = Color(0xFF3A1418),
    onErrorContainer = Color(0xFFFFDADA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF026AA7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EEFF),
    onPrimaryContainer = Color(0xFF03263D),
    secondary = Color(0xFF0D7A52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F5E8),
    onSecondaryContainer = Color(0xFF052E1E),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F141C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F141C),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFEEF2F7),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFD5DDE8),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFC81E1E),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

/** Clean developer studio typography scale. */
private val PocketTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
)

private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Colours and font styles for code, tool traces, vision badges, and hardware telemetry.
 */
object CodeColors {
    val tag = Color(0xFF7DD3FC)
    val attribute = Color(0xFFC4B5FD)
    val string = Color(0xFF86EFAC)
    val keyword = Color(0xFFF9A8D4)
    val number = Color(0xFFFCD34D)
    val comment = Color(0xFF64748B)
    val gutter = Color(0xFF4E5B70)

    val visionAccent = VisionViolet
    val visionContainer = VisionContainerDark
    val fixAmber = AmberFix
    val fixContainer = AmberContainerDark
    val emeraldLive = EmeraldReady

    val mono: FontFamily = JetBrainsMonoFamily

    /** Tabular numerals style so live tok/s, TTFT, and token counters never jitter horizontally. */
    val tabularMonoStyle = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontFeatureSettings = "tnum",
    )
}

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
