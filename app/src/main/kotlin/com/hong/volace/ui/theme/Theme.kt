package com.hong.volace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB8FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF23477F),
    onPrimaryContainer = Color(0xFFD8E5FF),
    secondary = Color(0xFF7FD4E8),
    background = Color(0xFF101018),
    onBackground = Color(0xFFE6E6EF),
    surface = Color(0xFF101018),
    onSurface = Color(0xFFE6E6EF),
    surfaceVariant = Color(0xFF232330),
    onSurfaceVariant = Color(0xFFC3C3D1),
    surfaceContainer = Color(0xFF1A1A24),
    surfaceContainerHigh = Color(0xFF22222E),
    outline = Color(0xFF4C4C5C),
    outlineVariant = Color(0xFF33333F),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6DE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE6FF),
    onPrimaryContainer = Color(0xFF002E68),
    secondary = Color(0xFF00798F),
    background = Color(0xFFF6F7FC),
    onBackground = Color(0xFF15161C),
    surface = Color.White,
    onSurface = Color(0xFF15161C),
    surfaceVariant = Color(0xFFECEEF6),
    onSurfaceVariant = Color(0xFF474A55),
    surfaceContainer = Color(0xFFF0F2F9),
    surfaceContainerHigh = Color(0xFFE9ECF5),
    outline = Color(0xFFBFC3CE),
    outlineVariant = Color(0xFFD9DDE7),
    error = Color(0xFFBA1A1A),
)

@Composable
fun VolaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
