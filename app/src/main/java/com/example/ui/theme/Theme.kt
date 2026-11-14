package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NovaMonochromeLightScheme = lightColorScheme(
    primary = NovaBlack,
    onPrimary = NovaWhite,
    primaryContainer = NovaCharcoal,
    onPrimaryContainer = NovaWhite,
    secondary = NovaCharcoal,
    onSecondary = NovaWhite,
    secondaryContainer = NovaSurfaceVariant,
    onSecondaryContainer = NovaBlack,
    tertiary = NovaSlate,
    onTertiary = NovaWhite,
    background = NovaWhite,
    onBackground = NovaBlack,
    surface = NovaWhite,
    onSurface = NovaBlack,
    surfaceVariant = NovaPaper,
    onSurfaceVariant = NovaSlate,
    surfaceContainerLow = NovaPaper,
    surfaceContainer = NovaSurfaceVariant,
    outline = NovaHairline,
    outlineVariant = NovaHairline,
    error = NovaError,
    onError = NovaWhite,
    errorContainer = NovaErrorContainer,
    onErrorContainer = NovaError
)

@Composable
fun NovaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = NovaMonochromeLightScheme,
        typography = NovaTypography,
        content = content
    )
}
