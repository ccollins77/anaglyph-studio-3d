package com.craigcollins.anaglyph.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AnaglyphColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
)

@Composable
fun AnaglyphTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnaglyphColorScheme,
        typography = AnaglyphTypography,
        content = content
    )
}
