package com.disone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeepIndigo = Color(0xFF6366F1)
private val NeonTeal = Color(0xFF14B8A6)
private val BackgroundDark = Color(0xFF0D0D14)
private val SurfaceDark = Color(0xFF16161F)

private val DarkColorScheme = darkColorScheme(
    primary = DeepIndigo,
    secondary = NeonTeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun DisoneTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
