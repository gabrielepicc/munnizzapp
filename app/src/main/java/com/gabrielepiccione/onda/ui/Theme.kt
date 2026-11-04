package com.gabrielepiccione.onda.ui

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.gabrielepiccione.onda.data.FontChoice
import com.gabrielepiccione.onda.data.Settings
import com.gabrielepiccione.onda.data.ThemeMode

/** Colori derivati dallo schema Material, comodi per i testi sopra lo sfondo. */
data class OndaColors(
    val dark: Boolean,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val onWallpaper: Color,
    val onWallpaperMuted: Color,
    val scrim: Color,
    val panel: Color,
    val panelHigh: Color,
    val panelBorder: Color,
)

val LocalOndaColors = staticCompositionLocalOf { colorsFrom(darkColorScheme(), true) }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

val AccentChoices = listOf(
    0xFF7FE3D1, // acqua
    0xFF8AB4F8, // cielo
    0xFFC3A6FF, // lavanda
    0xFFFFAB91, // corallo
    0xFFFFD27F, // sabbia
    0xFFA5D6A7, // salvia
)

/** Easing "emphasized" di Material 3. */
object Motion {
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> enter(duration: Int = 400): FiniteAnimationSpec<T> = tween(duration, easing = EmphasizedDecelerate)
    fun <T> exit(duration: Int = 200): FiniteAnimationSpec<T> = tween(duration, easing = EmphasizedAccelerate)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 380f)
    val offsetEnter: FiniteAnimationSpec<IntOffset> = tween(400, easing = EmphasizedDecelerate)
    val offsetExit: FiniteAnimationSpec<IntOffset> = tween(200, easing = EmphasizedAccelerate)
}

private fun colorsFrom(scheme: ColorScheme, dark: Boolean) = OndaColors(
    dark = dark,
    accent = scheme.primary,
    onAccent = scheme.onPrimary,
    accentContainer = scheme.primaryContainer,
    onAccentContainer = scheme.onPrimaryContainer,
    onWallpaper = scheme.onSurface,
    onWallpaperMuted = scheme.onSurfaceVariant,
    scrim = scheme.surface,
    panel = scheme.surfaceContainer,
    panelHigh = scheme.surfaceContainerHigh,
    panelBorder = scheme.outlineVariant.copy(alpha = 0.5f),
)

@Composable
fun OndaTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> staticScheme(Color(settings.accent), dark)
    }
    val family = when (settings.font) {
        FontChoice.SYSTEM -> FontFamily.Default
        FontChoice.SANS -> FontFamily.SansSerif
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONO -> FontFamily.Monospace
        FontChoice.ROUNDED -> FontFamily.Cursive
    }
    val typography = remember(family) { typographyWith(family) }
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalOndaColors provides colorsFrom(scheme, dark),
        LocalHapticsEnabled provides settings.haptics,
        LocalDensity provides Density(base.density, base.fontScale * settings.textScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Schema statico costruito a partire dal colore d'accento (per Android < 12 o colori dinamici spenti). */
private fun staticScheme(accent: Color, dark: Boolean): ColorScheme {
    fun mix(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f
    )
    return if (dark) {
        val surface = mix(Color(0xFF101315), accent, 0.04f)
        darkColorScheme(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.4f) Color(0xFF0B1A17) else Color.White,
            primaryContainer = mix(Color(0xFF101315), accent, 0.35f),
            onPrimaryContainer = mix(Color.White, accent, 0.2f),
            secondary = accent,
            surface = surface,
            background = surface,
            surfaceContainer = mix(surface, accent, 0.06f),
            surfaceContainerHigh = mix(surface, accent, 0.1f),
            onSurface = Color(0xFFE3E6E8),
            onSurfaceVariant = Color(0xFFB9C0C4),
        )
    } else {
        val primary = mix(accent, Color.Black, 0.45f)
        val surface = mix(Color(0xFFF8FAFB), accent, 0.04f)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = mix(Color.White, accent, 0.45f),
            onPrimaryContainer = mix(accent, Color.Black, 0.7f),
            secondary = primary,
            surface = surface,
            background = surface,
            surfaceContainer = mix(surface, accent, 0.08f),
            surfaceContainerHigh = mix(surface, accent, 0.13f),
            onSurface = Color(0xFF171C1F),
            onSurfaceVariant = Color(0xFF42494D),
        )
    }
}

private fun typographyWith(family: FontFamily): Typography {
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

/** Impedisce che i tocchi su un pannello sovrapposto arrivino alla home sottostante. */
fun Modifier.blockTouches(): Modifier = this.then(Modifier.pointerInput(Unit) { detectTapGestures() })
