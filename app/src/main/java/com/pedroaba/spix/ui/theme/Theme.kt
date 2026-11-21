package com.pedroaba.spix.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

object SpixColors {
    val Red = Color(0xFFC30027)
    val Blue = Color(0xFF065FD4)
    val Ink = Color(0xFF0F0F0F)
    val Secondary = Color(0xFF606060)
    val Surface = Color(0xFFF2F2F2)
    val Border = Color(0xFFD3D3D3)
    val Selection = Color(0xFFFCE8EC)
    val Error = Color(0xFFA61C32)
}

private val LightColors = lightColorScheme(
    primary = SpixColors.Red, onPrimary = Color.White,
    secondary = SpixColors.Blue, onSecondary = Color.White,
    background = Color.White, onBackground = SpixColors.Ink,
    surface = Color.White, onSurface = SpixColors.Ink,
    surfaceVariant = SpixColors.Surface, onSurfaceVariant = SpixColors.Secondary,
    outline = SpixColors.Border, outlineVariant = Color(0xFFEEEEEE),
    error = SpixColors.Error,
    primaryContainer = SpixColors.Selection, onPrimaryContainer = SpixColors.Red,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = SpixColors.Surface, surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    inverseSurface = SpixColors.Ink, inverseOnSurface = Color.White,
    inversePrimary = Color(0xFFFF8095), surfaceTint = SpixColors.Red,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8095), onPrimary = Color(0xFF480014),
    primaryContainer = Color(0xFF4B1625), onPrimaryContainer = Color(0xFFFF91A6),
    secondary = Color(0xFF8AB4F8), onSecondary = Color(0xFF002F65),
    background = Color(0xFF0F0F0F), onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F), onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727), onSurfaceVariant = Color(0xFFB5B5B5),
    surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF242424), surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF353535),
    surfaceBright = Color(0xFF353535), surfaceDim = Color(0xFF0F0F0F),
    outline = Color(0xFF707070), outlineVariant = Color(0xFF363636),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF1F1F1), inverseOnSurface = Color(0xFF0F0F0F),
    inversePrimary = SpixColors.Red, surfaceTint = Color(0xFFFF8095),
)

@Composable
fun SpixTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val lightness by animateFloatAsState(
        targetValue = if (darkTheme) 0f else 1f,
        animationSpec = tween(durationMillis = 440, easing = CubicBezierEasing(.22f, .05f, .26f, 1f)),
        label = "Spix theme",
    )
    val colors = remember(lightness) {
        when (lightness) {
            0f -> DarkColors
            1f -> LightColors
            else -> blendTheme(DarkColors, LightColors, lightness)
        }
    }
    MaterialTheme(colorScheme = colors, typography = SpixTypography, content = content)
}

private val SpixTypography = Typography()

private fun blendTheme(from: ColorScheme, to: ColorScheme, fraction: Float): ColorScheme {
    fun blend(start: Color, end: Color) = lerp(start, end, fraction)
    return from.copy(
        primary = blend(from.primary, to.primary),
        onPrimary = blend(from.onPrimary, to.onPrimary),
        primaryContainer = blend(from.primaryContainer, to.primaryContainer),
        onPrimaryContainer = blend(from.onPrimaryContainer, to.onPrimaryContainer),
        inversePrimary = blend(from.inversePrimary, to.inversePrimary),
        secondary = blend(from.secondary, to.secondary),
        onSecondary = blend(from.onSecondary, to.onSecondary),
        secondaryContainer = blend(from.secondaryContainer, to.secondaryContainer),
        onSecondaryContainer = blend(from.onSecondaryContainer, to.onSecondaryContainer),
        tertiary = blend(from.tertiary, to.tertiary),
        onTertiary = blend(from.onTertiary, to.onTertiary),
        tertiaryContainer = blend(from.tertiaryContainer, to.tertiaryContainer),
        onTertiaryContainer = blend(from.onTertiaryContainer, to.onTertiaryContainer),
        background = blend(from.background, to.background),
        onBackground = blend(from.onBackground, to.onBackground),
        surface = blend(from.surface, to.surface),
        onSurface = blend(from.onSurface, to.onSurface),
        surfaceVariant = blend(from.surfaceVariant, to.surfaceVariant),
        onSurfaceVariant = blend(from.onSurfaceVariant, to.onSurfaceVariant),
        surfaceTint = blend(from.surfaceTint, to.surfaceTint),
        inverseSurface = blend(from.inverseSurface, to.inverseSurface),
        inverseOnSurface = blend(from.inverseOnSurface, to.inverseOnSurface),
        error = blend(from.error, to.error),
        onError = blend(from.onError, to.onError),
        errorContainer = blend(from.errorContainer, to.errorContainer),
        onErrorContainer = blend(from.onErrorContainer, to.onErrorContainer),
        outline = blend(from.outline, to.outline),
        outlineVariant = blend(from.outlineVariant, to.outlineVariant),
        scrim = blend(from.scrim, to.scrim),
        surfaceBright = blend(from.surfaceBright, to.surfaceBright),
        surfaceDim = blend(from.surfaceDim, to.surfaceDim),
        surfaceContainer = blend(from.surfaceContainer, to.surfaceContainer),
        surfaceContainerHigh = blend(from.surfaceContainerHigh, to.surfaceContainerHigh),
        surfaceContainerHighest = blend(from.surfaceContainerHighest, to.surfaceContainerHighest),
        surfaceContainerLow = blend(from.surfaceContainerLow, to.surfaceContainerLow),
        surfaceContainerLowest = blend(from.surfaceContainerLowest, to.surfaceContainerLowest),
    )
}
