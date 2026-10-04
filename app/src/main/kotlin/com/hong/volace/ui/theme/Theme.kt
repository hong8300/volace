package com.hong.volace.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.hong.volace.data.contentColorOn

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

/** Black and white with nothing in between, for bright light or low vision. */
private val HighContrastColors = darkColorScheme(
    primary = Color(0xFFFFE066),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A3000),
    onPrimaryContainer = Color(0xFFFFF4B8),
    secondary = Color(0xFF7FE0FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color.White,
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF242424),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFF8A8A8A),
    error = Color(0xFFFF8A80),
)

private val MidnightColors = darkColorScheme(
    primary = Color(0xFF9CC2FF),
    onPrimary = Color(0xFF00274F),
    primaryContainer = Color(0xFF1F3F75),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF8FD8E8),
    background = Color(0xFF0B1730),
    onBackground = Color(0xFFEAF1FF),
    surface = Color(0xFF0B1730),
    onSurface = Color(0xFFEAF1FF),
    surfaceVariant = Color(0xFF16264A),
    onSurfaceVariant = Color(0xFFB8C7E8),
    surfaceContainer = Color(0xFF12213F),
    surfaceContainerHigh = Color(0xFF1A2B4D),
    outline = Color(0xFF4A5E86),
    outlineVariant = Color(0xFF2C3D63),
    error = Color(0xFFFFB4AB),
)

/** Whether [skin] draws dark, given the system setting (for AUTO / DYNAMIC). */
fun Skin.isDark(systemDark: Boolean): Boolean = when (this) {
    Skin.LIGHT -> false
    Skin.AUTO, Skin.DYNAMIC -> systemDark
    Skin.DEFAULT, Skin.HIGH_CONTRAST, Skin.MIDNIGHT -> true
}

@Composable
fun VolaceTheme(
    skin: Skin = SkinStore.state(LocalContext.current).collectAsState().value,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = skin.isDark(isSystemInDarkTheme())
    val colors = when (skin) {
        Skin.DEFAULT -> DarkColors
        Skin.LIGHT -> LightColors
        Skin.AUTO -> if (dark) DarkColors else LightColors
        Skin.DYNAMIC -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        Skin.HIGH_CONTRAST -> HighContrastColors
        Skin.MIDNIGHT -> MidnightColors
    }
    // A skin can be dark while the system is light (and the reverse): keep the status and
    // navigation bar icons readable against it.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Content on a profile's colour: white, or near black on light colours (see contentColorOn). */
fun readableOn(color: Color): Color = Color(contentColorOn(color.toArgb()))
