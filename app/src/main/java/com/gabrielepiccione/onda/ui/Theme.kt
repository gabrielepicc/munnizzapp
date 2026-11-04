package com.gabrielepiccione.onda.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.gabrielepiccione.onda.data.Settings
import com.gabrielepiccione.onda.data.ThemeMode

/** Colori usati sopra lo sfondo (wallpaper). */
data class OndaColors(
    val dark: Boolean,
    val accent: Color,
    val onWallpaper: Color,
    val onWallpaperMuted: Color,
    val scrim: Color,
    val panel: Color,
    val panelBorder: Color,
)

val LocalOndaColors = staticCompositionLocalOf {
    OndaColors(true, Color(0xFF7FE3D1), Color.White, Color.White.copy(alpha = 0.6f), Color.Black, Color(0xFF141A1F), Color.White.copy(alpha = 0.08f))
}

val AccentChoices = listOf(
    0xFF7FE3D1, // acqua
    0xFF7FB8E3, // cielo
    0xFFB49CFF, // lavanda
    0xFFFF9C8A, // corallo
    0xFFFFD27F, // sabbia
    0xFF9FE38A, // salvia
)

@Composable
fun OndaTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = Color(settings.accent)
    val colors = if (dark) {
        OndaColors(
            dark = true,
            accent = accent,
            onWallpaper = Color(0xFFF4F7F8),
            onWallpaperMuted = Color(0xFFF4F7F8).copy(alpha = 0.62f),
            scrim = Color(0xFF05080A),
            panel = Color(0xFF12181D),
            panelBorder = Color.White.copy(alpha = 0.08f),
        )
    } else {
        OndaColors(
            dark = false,
            accent = accent,
            onWallpaper = Color(0xFF0E1418),
            onWallpaperMuted = Color(0xFF0E1418).copy(alpha = 0.62f),
            scrim = Color(0xFFF2F5F6),
            panel = Color(0xFFF7F9FA),
            panelBorder = Color.Black.copy(alpha = 0.08f),
        )
    }
    val scheme = if (dark) {
        darkColorScheme(primary = accent, secondary = accent, surface = colors.panel, background = colors.panel)
    } else {
        lightColorScheme(primary = darken(accent), secondary = darken(accent), surface = colors.panel, background = colors.panel)
    }
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalOndaColors provides colors,
        LocalDensity provides Density(base.density, base.fontScale * settings.textScale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Sui temi chiari l'accento pastello va scurito per restare leggibile. */
private fun darken(c: Color): Color = Color(c.red * 0.55f, c.green * 0.55f, c.blue * 0.55f, 1f)

/** Impedisce che i tocchi su un pannello sovrapposto arrivino alla home sottostante. */
fun androidx.compose.ui.Modifier.blockTouches(): androidx.compose.ui.Modifier =
    this.then(
        androidx.compose.ui.Modifier.pointerInput(Unit) {
            detectTapGestures()
        }
    )
