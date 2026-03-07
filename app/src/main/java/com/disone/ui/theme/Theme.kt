package com.disone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val Gold = Color(0xFFD4AF37)
private val GoldLight = Color(0xFFF5D98C)
private val BackgroundBlack = Color(0xFF0A0A0A)
private val SurfaceDark = Color(0xFF141414)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    secondary = GoldLight,
    background = BackgroundBlack,
    surface = SurfaceDark,
    onPrimary = Color.Black,
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
        content = {
            val systemUiController = rememberSystemUiController()
            DisposableEffect(systemUiController) {
                systemUiController.setStatusBarColor(
                    color = Color.Transparent,
                    darkIcons = false
                )
                onDispose { }
            }
            content()
        }
    )
}
