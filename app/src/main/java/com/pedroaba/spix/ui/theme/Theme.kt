package com.pedroaba.spix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography(), content = content)
}
