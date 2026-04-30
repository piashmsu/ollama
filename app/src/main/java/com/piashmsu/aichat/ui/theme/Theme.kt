package com.piashmsu.aichat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.piashmsu.aichat.data.prefs.DarkMode

private val NeonCyan = Color(0xFF00E5FF)
private val NeonMagenta = Color(0xFFFF2D95)
private val Purple = Color(0xFF7C4DFF)
private val DarkBg = Color(0xFF0B0E14)
private val DarkSurface = Color(0xFF11141C)
private val DarkSurfaceElev = Color(0xFF1A1F2C)
private val TextPrimary = Color(0xFFE6E9F2)
private val TextSecondary = Color(0xFF9AA3B8)

private val DolphinDark = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    secondary = Purple,
    onSecondary = Color.White,
    tertiary = NeonMagenta,
    onTertiary = Color.White,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElev,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = DarkSurfaceElev,
    surfaceContainerHigh = Color(0xFF222838),
)

private val DolphinLight = lightColorScheme(
    primary = Color(0xFF0066CC),
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFFC2185B),
    background = Color(0xFFFAFAFC),
    surface = Color.White,
)

@Composable
fun DolphinAITheme(
    darkMode: DarkMode = DarkMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.System -> systemDark
        DarkMode.Dark -> true
        DarkMode.Light -> false
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        useDark -> DolphinDark
        else -> DolphinLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
