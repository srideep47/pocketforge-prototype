package com.srideep.pocketforge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Accent = Color(0xFF6EA8FE)
private val AccentDark = Color(0xFF2F6BD8)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF07152C),
    secondary = Color(0xFF9BC1FF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF151922),
    surfaceVariant = Color(0xFF1E2430),
    onBackground = Color(0xFFE7E9EE),
    onSurface = Color(0xFFE7E9EE),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    secondary = Color(0xFF4E6A96),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF4),
)

@Composable
fun PocketForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
