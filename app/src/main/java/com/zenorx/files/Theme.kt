package com.zenorx.files

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Orange = Color(0xFFE95420)
val Aubergine = Color(0xFF2C001E)
val AubergineSoft = Color(0xFF4A1A3C)

private val DarkScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Color(0xFF77216F),
    onSecondary = Color.White,
    background = Color(0xFF262626),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF303030),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    secondaryContainer = Color(0xFF4A2A3F),
    onSecondaryContainer = Color.White,
    outline = Color(0xFF555555),
    error = Color(0xFFFF6B6B),
    surfaceContainer = Color(0xFF383838),
    surfaceContainerHigh = Color(0xFF3A3A3A),
    surfaceContainerHighest = Color(0xFF424242)
)

private val LightScheme = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Color(0xFF77216F),
    onSecondary = Color.White,
    background = Color(0xFFF6F5F4),
    onBackground = Color(0xFF2B2B2B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2B2B),
    surfaceVariant = Color(0xFFEAE8E6),
    onSurfaceVariant = Color(0xFF666666),
    secondaryContainer = Color(0xFFF3D9CF),
    onSecondaryContainer = Color(0xFF2B2B2B),
    outline = Color(0xFFCDC7C2),
    error = Color(0xFFC7162B),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF0EEEC)
)

@Composable
fun ZenorXTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Aubergine.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = scheme.background.toArgb()
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = false
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
