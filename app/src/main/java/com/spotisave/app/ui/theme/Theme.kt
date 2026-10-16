package com.spotisave.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = CanvasBlack,
    primaryContainer = SurfaceCardElevated,
    onPrimaryContainer = TextPrimary,
    secondary = AppleSystemGreen,
    onSecondary = CanvasBlack,
    background = CanvasBlack,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.White,
    primaryContainer = SurfaceCardLightElevated,
    onPrimaryContainer = TextPrimaryLight,
    secondary = AppleSystemGreenLight,
    onSecondary = Color.White,
    background = CanvasWhite,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceCardLightElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = HairlineBorderLight
)

val LocalIsDarkTheme = staticCompositionLocalOf { true }

@Composable
fun SpotisaveTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bgColor = if (darkTheme) CanvasBlack else CanvasWhite
            window.statusBarColor = bgColor.toArgb()
            window.navigationBarColor = bgColor.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

